package org.example.aiscanner_server.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.aiscanner_server.model.enums.TaskStatus;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskSubmitResponse {

    private String taskId;
    private TaskStatus status;
    private LocalDateTime createdAt;

    public TaskSubmitResponse(String taskId, TaskStatus status, LocalDateTime createdAt) {
        this.taskId = taskId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getTaskId() { return taskId; }
    public TaskStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
