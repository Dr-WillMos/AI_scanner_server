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
/**死信重试的调度器*/
@Component
public class DlqRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DlqRetryScheduler.class);
    private static final String DLQ_RETRY_KEY_PREFIX = "detect:dlq:retry:";  //键前缀
    private static final int MAX_DLQ_RETRIES = 4;
    private static final int SCAN_LIMIT = 100;  //每次扫描时最大的拉取数量

    private static final long[] BACKOFF_SECONDS = {60, 300, 900, 1800};  //重试失败后重试时间

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

    @Scheduled(fixedDelay = 30_000)  //每隔30秒执行一次
    public void processDlq() {
        var records = streamOps.range(
                DetectStreamProducer.DLQ_STREAM_KEY,
                Range.unbounded());  //从死信队列中获取所有消息并存入record

        if (records == null || records.isEmpty()) {
            return;
        }

        int retried = 0;
        int skipped = 0;
        int finalDead = 0;
        int processed = 0; //单次扫描的参数，供后续完成逻辑和运维了解死信调度情况

        for (MapRecord<String, String, String> record : records) {  //遍历获得的records
            if (processed++ >= SCAN_LIMIT) break;//如果总消息数大于最大限制，停止。
            Map<String, String> fields = record.getValue();
            String taskId = fields.get("taskId");
            if (taskId == null) {      //ID为0代表无效消息，删除后重试
                streamOps.delete(DetectStreamProducer.DLQ_STREAM_KEY, record.getId().getValue());
                continue;
            }

            int dlqRetryCount = getAndEnsureDlqRetryCount(taskId);
            long backoffSeconds = dlqRetryCount < BACKOFF_SECONDS.length  /**根据当前死信已重试的次数（dlqRetryCount），从退避时间数组中取出对应的等待秒数。*/
                    ? BACKOFF_SECONDS[dlqRetryCount]                 /**如果重试次数已经超过了数组长度，则返回一个非常大的值（相当于不再重试）。*/
                    : Long.MAX_VALUE;

            long messageMs = extractTimestamp(record.getId().getValue()); //得到消息被添加到Stream的时间戳
            long ageSeconds = (System.currentTimeMillis() - messageMs) / 1000;  //当前时间戳-添加时间戳就是已经等待时间戳

            if (dlqRetryCount >= MAX_DLQ_RETRIES) {
                finalDead++;
                continue; // 最终死信
            }

            if (ageSeconds < backoffSeconds) {
                skipped++;
                continue; // 等待时间还不够，暂时跳过
            }

            //重试逻辑：计数器加一，重新进入主队列，删除死信队列。
            stringRedisTemplate.opsForValue().increment(DLQ_RETRY_KEY_PREFIX + taskId);
            streamOps.delete(DetectStreamProducer.DLQ_STREAM_KEY, record.getId().getValue());
            producer.requeue(taskId,
                    fields.getOrDefault("deviceId", ""),
                    fields.getOrDefault("authorId", ""),
                    fields.getOrDefault("filePath", ""),
                    0); // 刷新重试次数
            log.info("DLQ auto-retry: taskId={}, dlqRetryCount={}, backoff={}s, age={}s",
                    taskId, dlqRetryCount, backoffSeconds, ageSeconds);
            retried++;
        }

        if (retried > 0 || finalDead > 0) { //如果存在重试和最终死信，就记录在日志
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
        // 若任务从来没有被记录，默认重试次数为0，确保新输入的Key具有对应的TTL（默认24小时）。
        if (val == null) {
            stringRedisTemplate.opsForValue().set(key, "0", 24, TimeUnit.HOURS);
        }
        return count;
    }

    private static long extractTimestamp(String streamMessageId) {//获取进入队列的时间戳
        try {
            return Long.parseLong(streamMessageId.split("-")[0]);
        } catch (NumberFormatException e) {
            return System.currentTimeMillis();
        }
    }
}
