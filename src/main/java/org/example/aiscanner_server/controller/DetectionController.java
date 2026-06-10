package org.example.aiscanner_server.controller;

import org.example.aiscanner_server.common.ApiResponse;
import org.example.aiscanner_server.model.dto.DetectResponse;
import org.example.aiscanner_server.model.dto.DetectTask;
import org.example.aiscanner_server.model.dto.TaskStatusResponse;
import org.example.aiscanner_server.model.dto.TaskSubmitResponse;
import org.example.aiscanner_server.model.enums.TaskStatus;
import org.example.aiscanner_server.service.DetectTaskService;
import org.example.aiscanner_server.service.DetectionService;
import org.example.aiscanner_server.service.VideoStorageService;
import org.example.aiscanner_server.service.VideoValidator;
import org.example.aiscanner_server.stream.DetectStreamProducer;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;
/** 本Controller用于是全项目的核心逻辑。 */
@RestController
@RequestMapping("/api/v1")
public class DetectionController {

    private final DetectionService detectionService;
    private final DetectTaskService taskService;
    private final VideoStorageService videoStorage;
    private final DetectStreamProducer producer;
    private final VideoValidator videoValidator;

    public DetectionController(DetectionService detectionService,
                               DetectTaskService taskService,
                               VideoStorageService videoStorage,
                               DetectStreamProducer producer,
                               VideoValidator videoValidator) {
        this.detectionService = detectionService;
        this.taskService = taskService;
        this.videoStorage = videoStorage;
        this.producer = producer;
        this.videoValidator = videoValidator;
    }

    /** 同步的，待优化 **/
    @PostMapping("/detect")
    public ApiResponse<DetectResponse> detect(
            @RequestParam String deviceId,
            @RequestParam String authorId,
            @RequestParam MultipartFile video) {
        videoValidator.validate(video);
        DetectResponse result = detectionService.detect(deviceId, authorId, video);
        return ApiResponse.ok(result);
    }

    /** 异步的，依托消息队列  **/
    @PostMapping("/detect/async")
    public ApiResponse<TaskSubmitResponse> detectAsync(
            @RequestParam String deviceId,
            @RequestParam String authorId,
            @RequestParam MultipartFile video) {
        videoValidator.validate(video);  //视频认证，保证在10M以下
        String taskId = UUID.randomUUID().toString();  //新增任务ID

        // 保存视频到磁盘，使得消费者能够读取
        var filePath = videoStorage.save(taskId, video);

        // 创建任务并将其推到消息队列
        var task = taskService.createTask(taskId, deviceId, authorId);
        producer.send(taskId, deviceId, authorId, filePath.toString());

        return ApiResponse.ok(202, "任务已提交",
                new TaskSubmitResponse(taskId, TaskStatus.PENDING, task.getCreatedAt()));
    }

    /** 查询异步检测结果 */
    @GetMapping("/detect/{taskId}/status")
    public ApiResponse<TaskStatusResponse> getTaskStatus(@PathVariable String taskId) {
        Optional<DetectTask> opt = taskService.findById(taskId);
        if (opt.isEmpty()) {
            return ApiResponse.error(404, "任务不存在或已过期");
        }
        return ApiResponse.ok(TaskStatusResponse.from(opt.get()));
    }
}
