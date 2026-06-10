package org.example.aiscanner_server.stream;

import org.example.aiscanner_server.model.dto.DetectResponse;
import org.example.aiscanner_server.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;

@Component
public class DetectStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(DetectStreamConsumer.class);
    private static final int MAX_RETRIES = 3;  //最大重试次数

    private final DetectionService detectionService;
    private final DetectTaskService taskService;
    private final VideoStorageService videoStorage;
    private final DetectStreamProducer producer;

    public DetectStreamConsumer(DetectionService detectionService,
                                DetectTaskService taskService,
                                VideoStorageService videoStorage,
                                DetectStreamProducer producer) {
        this.detectionService = detectionService;
        this.taskService = taskService;
        this.videoStorage = videoStorage;
        this.producer = producer;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> fields = message.getValue();  //获取一个消息体/任务
        String taskId = fields.get("taskId");  //将 必要的任务信息载入
        String deviceId = fields.get("deviceId");
        String authorId = fields.get("authorId");
        String filePath = fields.get("filePath");
        int retryCount = Integer.parseInt(fields.getOrDefault("retryCount", "0"));

        log.info("Processing task: taskId={}, retryCount={}", taskId, retryCount);

        try {
            taskService.updateStatus(taskId, org.example.aiscanner_server.model.enums.TaskStatus.PROCESSING); //更新任务状态

            byte[] bytes = videoStorage.read(taskId);  //将视频文件装载到字节数组中
            MultipartFile video = wrapAsMultipartFile(taskId, bytes); //将字节数组包装为MultipartFile对象
            DetectResponse result = detectionService.detect(deviceId, authorId, video);

            taskService.markDone(taskId, result);  //当处理机回到这里时，就说明Task已经完成
            videoStorage.delete(taskId);  //根据已完成任务ID删除暂存文件
            log.info("Task completed successfully: taskId={}", taskId);

        } catch (Exception e) {
            log.error("Task processing failed: taskId={}, retryCount={}, error={}",
                    taskId, retryCount, e.getMessage());

            int nextRetry = retryCount + 1; //失败的话重试次数++
            taskService.incrementRetry(taskId);

            if (nextRetry <= MAX_RETRIES) {
                producer.requeue(taskId, deviceId, authorId, filePath, nextRetry); //重新入队进行重试
            } else {
                // 找出错误信息的根因
                String errorMsg = e.getMessage();
                if (e.getCause() != null) {
                    errorMsg = e.getCause().getMessage();
                }
                taskService.markFailed(taskId, errorMsg);  //标记失败状态
                videoStorage.delete(taskId); //删除临时文件
                producer.sendToDlq(taskId, deviceId, authorId, filePath, errorMsg, retryCount);  //发到死信队列
            }
        }
    }

    /** 下面是处理MultipartFile和字节数组的方法*/
    private static MultipartFile wrapAsMultipartFile(String taskId, byte[] bytes) {
        return new MultipartFile() {
            @Override public String getName() { return "video"; }
            @Override public String getOriginalFilename() { return taskId + ".mp4"; }
            @Override public String getContentType() { return "video/mp4"; }
            @Override public boolean isEmpty() { return bytes.length == 0; }
            @Override public long getSize() { return bytes.length; }
            @Override public byte[] getBytes() { return bytes; }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
            @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), bytes); }
        };
    }
}
