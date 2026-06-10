package org.example.aiscanner_server.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.aiscanner_server.model.enums.TaskStatus;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DetectTask {

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private String taskId;
    private TaskStatus status;
    private String deviceId;
    private String authorId;
    private int retryCount;
    private DetectResponse result;  // DONE only
    private String error;           // FAILED only
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DetectTask() {}

    public DetectTask(String taskId, TaskStatus status, String deviceId, String authorId) {
        this.taskId = taskId;
        this.status = status;
        this.deviceId = deviceId;
        this.authorId = authorId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public String toJson() {
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize DetectTask", e);
        }
    }

    public static DetectTask fromJson(String json) {
        try {
            return mapper.readValue(json, DetectTask.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize DetectTask", e);
        }
    }

    // ── Getters & Setters ────────────────────────────────────

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public DetectResponse getResult() { return result; }
    public void setResult(DetectResponse result) { this.result = result; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
