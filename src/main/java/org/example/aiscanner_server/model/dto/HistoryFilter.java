package org.example.aiscanner_server.model.dto;

import org.example.aiscanner_server.model.enums.RiskLevel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class HistoryFilter {

    private final String deviceId;
    private final String authorId;
    private final RiskLevel riskLevel;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;

    private HistoryFilter(String deviceId, String authorId, RiskLevel riskLevel,
                          LocalDate startDate, LocalDate endDate) {
        this.deviceId = deviceId;
        this.authorId = (authorId != null && !authorId.isBlank()) ? authorId : null;
        this.riskLevel = riskLevel;
        this.startDate = startDate != null ? startDate.atStartOfDay() : null;
        this.endDate = endDate != null ? endDate.atTime(LocalTime.MAX) : null;
    }

    public static HistoryFilter of(String deviceId, String authorId, RiskLevel riskLevel,
                                   LocalDate startDate, LocalDate endDate) {
        return new HistoryFilter(deviceId, authorId, riskLevel, startDate, endDate);
    }

    public String getDeviceId() { return deviceId; }
    public String getAuthorId() { return authorId; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public LocalDateTime getStartDate() { return startDate; }
    public LocalDateTime getEndDate() { return endDate; }
}
