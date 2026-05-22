package org.example.aiscanner_server.controller;

import org.example.aiscanner_server.common.ApiResponse;
import org.example.aiscanner_server.model.dto.HistoryResponse;
import org.example.aiscanner_server.service.HistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/history")
    public ApiResponse<HistoryResponse> queryHistory(
            @RequestParam String deviceId,
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        HistoryResponse result = historyService.query(deviceId, afterId, page, size);
        return ApiResponse.ok(result);
    }
}
