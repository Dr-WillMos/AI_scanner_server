package org.example.aiscanner_server.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class DlqRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DlqRetryScheduler.class);
    private static final String DLQ_RETRY_KEY_PREFIX = "detect:dlq:retry:";
    private static final int MAX_DLQ_RETRIES = 4;
    private static final int SCAN_LIMIT = 100;
    // backoff seconds indexed by dlqRetryCount
    private static final long[] BACKOFF_SECONDS = {60, 300, 900, 1800};

    private final StreamOperations<String, String, String> streamOps;
    private final StringRedisTemplate stringRedisTemplate;
    private final DetectStreamProducer producer;

    public DlqRetryScheduler(StreamOperations<String, String, String> streamOps,
                             StringRedisTemplate stringRedisTemplate,
                             DetectStreamProducer producer) {
        this.streamOps = streamOps;
        this.stringRedisTemplate = stringRedisTemplate;
        this.producer = producer;
    }

    @Scheduled(fixedDelay = 30_000)
    public void processDlq() {
        var records = streamOps.range(
                DetectStreamProducer.DLQ_STREAM_KEY,
                Range.unbounded());

        if (records == null || records.isEmpty()) {
            return;
        }

        int retried = 0;
        int skipped = 0;
        int finalDead = 0;
        int processed = 0;

        for (MapRecord<String, String, String> record : records) {
            if (processed++ >= SCAN_LIMIT) break;
            Map<String, String> fields = record.getValue();
            String taskId = fields.get("taskId");
            if (taskId == null) {
                streamOps.delete(DetectStreamProducer.DLQ_STREAM_KEY, record.getId().getValue());
                continue;
            }

            int dlqRetryCount = getAndEnsureDlqRetryCount(taskId);
            long backoffSeconds = dlqRetryCount < BACKOFF_SECONDS.length
                    ? BACKOFF_SECONDS[dlqRetryCount]
                    : Long.MAX_VALUE;

            long messageMs = extractTimestamp(record.getId().getValue());
            long ageSeconds = (System.currentTimeMillis() - messageMs) / 1000;

            if (dlqRetryCount >= MAX_DLQ_RETRIES) {
                finalDead++;
                continue; // final dead letter
            }

            if (ageSeconds < backoffSeconds) {
                skipped++;
                continue; // not due yet
            }

            // Retry: increment counter, delete from DLQ, requeue to main stream
            stringRedisTemplate.opsForValue().increment(DLQ_RETRY_KEY_PREFIX + taskId);
            streamOps.delete(DetectStreamProducer.DLQ_STREAM_KEY, record.getId().getValue());
            producer.requeue(taskId,
                    fields.getOrDefault("deviceId", ""),
                    fields.getOrDefault("authorId", ""),
                    fields.getOrDefault("filePath", ""),
                    0); // fresh retryCount
            log.info("DLQ auto-retry: taskId={}, dlqRetryCount={}, backoff={}s, age={}s",
                    taskId, dlqRetryCount, backoffSeconds, ageSeconds);
            retried++;
        }

        if (retried > 0 || finalDead > 0) {
            log.info("DLQ scan complete: retried={}, skipped={}, finalDead={}",
                    retried, skipped, finalDead);
        }
    }

    private int getAndEnsureDlqRetryCount(String taskId) {
        String key = DLQ_RETRY_KEY_PREFIX + taskId;
        String val = stringRedisTemplate.opsForValue().get(key);
        int count = 0;
        if (val != null) {
            try {
                count = Integer.parseInt(val);
            } catch (NumberFormatException ignored) {
            }
        }
        // Ensure the key exists with TTL for newly entered DLQ messages
        if (val == null) {
            stringRedisTemplate.opsForValue().set(key, "0", 24, TimeUnit.HOURS);
        }
        return count;
    }

    private static long extractTimestamp(String streamMessageId) {
        try {
            return Long.parseLong(streamMessageId.split("-")[0]);
        } catch (NumberFormatException e) {
            return System.currentTimeMillis();
        }
    }
}
