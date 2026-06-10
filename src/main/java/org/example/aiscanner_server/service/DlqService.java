package org.example.aiscanner_server.service;

import org.example.aiscanner_server.model.dto.DlqMessage;
import org.example.aiscanner_server.stream.DetectStreamProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DlqService {

    private static final Logger log = LoggerFactory.getLogger(DlqService.class);
    static final String DLQ_RETRY_KEY_PREFIX = "detect:dlq:retry:";

    private final StreamOperations<String, String, String> streamOps;
    private final StringRedisTemplate stringRedisTemplate;
    private final DetectStreamProducer producer;

    public DlqService(StreamOperations<String, String, String> streamOps,
                      StringRedisTemplate stringRedisTemplate,
                      DetectStreamProducer producer) {
        this.streamOps = streamOps;
        this.stringRedisTemplate = stringRedisTemplate;
        this.producer = producer;
    }

    public List<DlqMessage> listMessages(int count) {  //列出所有死信
        int limit = Math.min(count, 100);  //每次最大返回100条
        var records = streamOps.reverseRange(    //var是局部变量自动推断，能够自适应变为对应的变量类型，这里的record实际上是List<MapRecord<String, String, String>>
                DetectStreamProducer.DLQ_STREAM_KEY,
                Range.unbounded());

        if (records == null || records.isEmpty()) {  //返回空
            return Collections.emptyList();
        }

        List<DlqMessage> messages = new ArrayList<>(); //新建可变数组型列表
        for (MapRecord<String, String, String> record : records) { //遍历MapRecord后将其包装为业务友好的DlqMessage
            if (messages.size() >= limit) break;
            DlqMessage msg = DlqMessage.fromStream(
                    record.getId().getValue(),
                    record.getValue(),
                    getDlqRetryCount(record.getValue().get("taskId")));
            messages.add(msg);
        }
        return messages;
    }

    public DlqMessage getMessage(String messageId) {
        var records = streamOps.range(
                DetectStreamProducer.DLQ_STREAM_KEY,
                Range.closed(messageId, messageId));

        if (records == null || records.isEmpty()) {
            return null; //未找到返回空
        }
        MapRecord<String, String, String> record = records.get(0);  //取得第一条
        return DlqMessage.fromStream(   //返回的是业务有好的DlqMessage
                record.getId().getValue(),
                record.getValue(),
                getDlqRetryCount(record.getValue().get("taskId")));
    }

    public DlqMessage retryTask(String messageId) {   //重试死信
        DlqMessage msg = getMessage(messageId);
        if (msg == null) {
            return null;
        }

        producer.send(msg.getTaskId(), msg.getDeviceId(), msg.getAuthorId(), msg.getFilePath()); //用主队列生产者刷新重试次数后重新入队
        streamOps.delete(DetectStreamProducer.DLQ_STREAM_KEY, messageId);  //删除死信
        stringRedisTemplate.delete(DLQ_RETRY_KEY_PREFIX + msg.getTaskId());
        log.info("DLQ message manually retried: messageId={}, taskId={}", messageId, msg.getTaskId());
        return msg;
    }

    public boolean deleteMessage(String messageId) {
        DlqMessage msg = getMessage(messageId);
        if (msg == null) {
            return false;
        }
        streamOps.delete(DetectStreamProducer.DLQ_STREAM_KEY, messageId); //StreamOperations 封装了 Redis Stream 的所有命令，这里是删除
        stringRedisTemplate.delete(DLQ_RETRY_KEY_PREFIX + msg.getTaskId());
        log.info("DLQ message deleted: messageId={}, taskId={}", messageId, msg.getTaskId());
        return true;
    }

    public long getPendingCount() { //获取死信总数
        Long size = streamOps.size(DetectStreamProducer.DLQ_STREAM_KEY);
        return size != null ? size : 0;
    }

    public long getFinalDeadLetterCount() {   //获取最终死信数量
        List<DlqMessage> all = listMessages(100);
        return all.stream().filter(m -> m.getDlqRetryCount() >= 4).count();
    }

    public String getOldestMessageTime() {  //监控死信堆积时长
        var records = streamOps.range(
                DetectStreamProducer.DLQ_STREAM_KEY,
                Range.unbounded());         //var其实就是指代List<MapRecord<String, String, String>>
        if (records == null || records.isEmpty()) {
            return null;
        }
        MapRecord<String, String, String> record = records.get(0);
        DlqMessage msg = DlqMessage.fromStream(
                record.getId().getValue(), record.getValue(), 0);
        return msg.getEnteredAt() != null ? msg.getEnteredAt().toString() : null;
    }

    public void purge() {
        // 删除所有死信
        var keys = stringRedisTemplate.keys(DLQ_RETRY_KEY_PREFIX + "*"); //星号加统一前缀就是获取所有死信键
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        stringRedisTemplate.delete(DetectStreamProducer.DLQ_STREAM_KEY);
        log.warn("DLQ purged entirely");
    }

    private int getDlqRetryCount(String taskId) { //获取重试次数，这是一个私有辅助方法。
        if (taskId == null) return 0;
        String val = stringRedisTemplate.opsForValue().get(DLQ_RETRY_KEY_PREFIX + taskId);
        if (val == null) return 0;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
