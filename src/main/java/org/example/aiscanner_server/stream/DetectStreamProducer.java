package org.example.aiscanner_server.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;

import java.util.Map;
/**查询流生产者*/
/**redis stream没有rocketMQ的web管理界面，可以使用第三方工具如Redis Insight对消息队列进行查看和管理*/

@Component
public class DetectStreamProducer {

    private static final Logger log = LoggerFactory.getLogger(DetectStreamProducer.class);

    static final String STREAM_KEY = "detect:stream";
    public static final String DLQ_STREAM_KEY = "detect:stream:dlq";   //Dead Letter Queue死信队列，多次处理失败被放弃处理的Task

    private final StreamOperations<String, String, String> streamOps;  //Spring Data Redis 提供的操作类，内封装了操作消息队列的方法

    public DetectStreamProducer(StreamOperations<String, String, String> streamOps) {
        this.streamOps = streamOps;
    }

    /** 讲一个新任务提交到流上  */
    public void send(String taskId, String deviceId, String authorId, String filePath) {
        Map<String, String> fields = Map.of(
                "taskId", taskId,
                "deviceId", deviceId,
                "authorId", authorId,
                "filePath", filePath,
                "retryCount", "0"
        );  //将必要信息内容和字段名封装成了Map键值对
        MapRecord<String, String, String> record = StreamRecords
                .mapBacked(fields)
                .withStreamKey(STREAM_KEY);   // 将 Map<String,String> 包装成 MapRecord并指定目标消息队列
        var msgId = streamOps.add(record);   //发送消息到 Stream并将消息ID返回到msgId
        log.info("Task enqueued: taskId={}, streamMsgId={}", taskId, msgId);
    }

    /** 将处理失败的消息进行重排列，随后重试 */
    public void requeue(String taskId, String deviceId, String authorId, String filePath, int retryCount) {
        Map<String, String> fields = Map.of(
                "taskId", taskId,
                "deviceId", deviceId,
                "authorId", authorId,
                "filePath", filePath,
                "retryCount", String.valueOf(retryCount)
        );
        MapRecord<String, String, String> record = StreamRecords
                .mapBacked(fields)
                .withStreamKey(STREAM_KEY);
        streamOps.add(record);
        log.warn("Task re-enqueued: taskId={}, retryCount={}", taskId, retryCount);
    }

    /** 达到最大重试次数后，将其放入死信队列 */
    public void sendToDlq(String taskId, String deviceId, String authorId,
                          String filePath, String error, int retryCount) {
        Map<String, String> fields = Map.of(
                "taskId", taskId,
                "deviceId", deviceId,
                "authorId", authorId,
                "filePath", filePath,
                "error", error != null ? error : "unknown",
                "retryCount", String.valueOf(retryCount)
        );
        MapRecord<String, String, String> record = StreamRecords
                .mapBacked(fields)
                .withStreamKey(DLQ_STREAM_KEY);
        streamOps.add(record);
        log.error("Task moved to DLQ: taskId={}, retryCount={}, error={}", taskId, retryCount, error);
    }
}
