package org.example.aiscanner_server.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DlqMessage {

    private String messageId;
    private String taskId;
    private String deviceId;
    private String authorId;
    private String filePath;
    private String error;
    private int retryCount;
    private int dlqRetryCount;
    private LocalDateTime enteredAt;

    public DlqMessage() {}

    public static DlqMessage fromStream(String streamMessageId, java.util.Map<String, String> fields,
                                        int dlqRetryCount) {
        DlqMessage msg = new DlqMessage();
        msg.messageId = streamMessageId;
        msg.taskId = fields.get("taskId");
        msg.deviceId = fields.get("deviceId");
        msg.authorId = fields.get("authorId");
        msg.filePath = fields.get("filePath");
        msg.error = fields.get("error");
        msg.retryCount = Integer.parseInt(fields.getOrDefault("retryCount", "0"));
        msg.dlqRetryCount = dlqRetryCount;

        // Extract timestamp from Redis Stream message ID (format: <msTime>-<seq>)
        long ms = Long.parseLong(streamMessageId.split("-")[0]);
        msg.enteredAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
        return msg;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public int getDlqRetryCount() { return dlqRetryCount; }
    public void setDlqRetryCount(int dlqRetryCount) { this.dlqRetryCount = dlqRetryCount; }

    public LocalDateTime getEnteredAt() { return enteredAt; }
    public void setEnteredAt(LocalDateTime enteredAt) { this.enteredAt = enteredAt; }
}
