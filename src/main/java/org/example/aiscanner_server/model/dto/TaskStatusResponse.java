package org.example.aiscanner_server.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.aiscanner_server.model.enums.TaskStatus;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskStatusResponse {

    private String taskId;
    private TaskStatus status;
    private DetectResponse result;    // DONE only
    private String error;             // FAILED only
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TaskStatusResponse from(DetectTask task) {
        TaskStatusResponse r = new TaskStatusResponse();
        r.taskId = task.getTaskId();
        r.status = task.getStatus();
        r.result = task.getResult();
        r.error = task.getError();
        r.createdAt = task.getCreatedAt();
        r.updatedAt = task.getUpdatedAt();
        return r;
    }

    public String getTaskId() { return taskId; }
    public TaskStatus getStatus() { return status; }
    public DetectResponse getResult() { return result; }
    public String getError() { return error; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
