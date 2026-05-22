package org.example.aiscanner_server.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.aiscanner_server.model.enums.RiskLevel;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistoryItem {

    private Long id;
    private String deviceId;
    private String authorId;
    private RiskLevel riskLevel;
    private Double score;
    private LocalDateTime createdAt;

    public static HistoryItem from(org.example.aiscanner_server.model.entity.DetectionRecord record) {
        HistoryItem item = new HistoryItem();
        item.id = record.getId();
        item.deviceId = record.getDeviceId();
        item.authorId = record.getAuthorId();
        item.riskLevel = record.getRiskLevel();
        item.score = record.getScore();
        item.createdAt = record.getCreatedAt();
        return item;
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public String getAuthorId() { return authorId; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public Double getScore() { return score; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
