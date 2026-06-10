package org.example.aiscanner_server.service;

import org.example.aiscanner_server.model.dto.DetectTask;
import org.example.aiscanner_server.model.enums.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
/**用来操作消息队列的服务类*/
@Service
public class DetectTaskService {

    private static final Logger log = LoggerFactory.getLogger(DetectTaskService.class);

    private static final String TASK_KEY_PREFIX = "detect:task:";
    private static final Duration TASK_TTL = Duration.ofHours(24);  //任务生存时间为24小时

    private final StringRedisTemplate redisTemplate;

    public DetectTaskService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public DetectTask createTask(String taskId, String deviceId, String authorId) {//新建任务
        DetectTask task = new DetectTask(taskId, TaskStatus.PENDING, deviceId, authorId);
        save(task);
        log.info("Task created: taskId={}, deviceId={}, authorId={}", taskId, deviceId, authorId);
        return task;
    }

    public void updateStatus(String taskId, TaskStatus status) {   //更新任务状态
        Optional<DetectTask> opt = findById(taskId);   //根据任务ID找出DetectTask对象
        if (opt.isEmpty()) return;
        DetectTask task = opt.get();
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        save(task);
    }

    public void markDone(String taskId, Object result) {   //标记已完成任务
        Optional<DetectTask> opt = findById(taskId);
        if (opt.isEmpty()) return;
        DetectTask task = opt.get();
        task.setStatus(TaskStatus.DONE);
        task.setResult((org.example.aiscanner_server.model.dto.DetectResponse) result);  //强制类型转换
        task.setUpdatedAt(LocalDateTime.now());
        save(task);  //更新到Redis
        log.info("Task completed: taskId={}", taskId);
    }

    public void markFailed(String taskId, String error) {
        Optional<DetectTask> opt = findById(taskId);
        if (opt.isEmpty()) return;
        DetectTask task = opt.get();
        task.setStatus(TaskStatus.FAILED);
        task.setError(error);
        task.setUpdatedAt(LocalDateTime.now());
        save(task);
        log.warn("Task failed: taskId={}, error={}", taskId, error);
    }

    public void incrementRetry(String taskId) {   //增加任务重试次数
        Optional<DetectTask> opt = findById(taskId);
        if (opt.isEmpty()) return;
        DetectTask task = opt.get();
        task.setRetryCount(task.getRetryCount() + 1);
        task.setUpdatedAt(LocalDateTime.now());
        save(task);
    }

    public Optional<DetectTask> findById(String taskId) {
        try {
            String json = redisTemplate.opsForValue().get(TASK_KEY_PREFIX + taskId);
            if (json == null) return Optional.empty();
            return Optional.of(DetectTask.fromJson(json));
        } catch (Exception e) {
            log.warn("Failed to read task from Redis: taskId={}", taskId, e);
            return Optional.empty();
        }
    }

    private void save(DetectTask task) {
        try {
            redisTemplate.opsForValue().set(
                    TASK_KEY_PREFIX + task.getTaskId(),
                    task.toJson(),
                    TASK_TTL);
        } catch (Exception e) {
            log.warn("Failed to save task to Redis: taskId={}", task.getTaskId(), e);
        }
    }
}
