package org.example.aiscanner_server.controller;

import org.example.aiscanner_server.common.ApiResponse;
import org.example.aiscanner_server.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
//系统健康度监控接口
@RestController
@RequestMapping("/api/v1")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        return ApiResponse.ok(statsService.getDashboardStats());
    }
}
