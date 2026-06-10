package org.example.aiscanner_server.controller;

import org.example.aiscanner_server.common.ApiResponse;
import org.example.aiscanner_server.model.dto.HistoryResponse;
import org.example.aiscanner_server.model.enums.RiskLevel;
import org.example.aiscanner_server.service.HistoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
/** 历史查询接口 */
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
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String authorId,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        HistoryResponse result = historyService.query(
                deviceId, afterId, page, size, authorId, riskLevel, startDate, endDate);
        return ApiResponse.ok(result);
    }
}
