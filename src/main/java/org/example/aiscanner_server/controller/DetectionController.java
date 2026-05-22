package org.example.aiscanner_server.controller;

import org.example.aiscanner_server.common.ApiResponse;
import org.example.aiscanner_server.model.dto.DetectResponse;
import org.example.aiscanner_server.service.DetectionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class DetectionController {

    private final DetectionService detectionService;

    public DetectionController(DetectionService detectionService) {
        this.detectionService = detectionService;
    }

    @PostMapping("/detect")
    public ApiResponse<DetectResponse> detect(
            @RequestParam String deviceId,
            @RequestParam String authorId,
            @RequestParam MultipartFile video) {
        DetectResponse result = detectionService.detect(deviceId, authorId, video);
        return ApiResponse.ok(result);
    }
}
