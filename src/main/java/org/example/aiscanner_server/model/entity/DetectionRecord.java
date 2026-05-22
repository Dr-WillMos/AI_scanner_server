package org.example.aiscanner_server.model.entity;

import org.example.aiscanner_server.model.enums.RiskLevel;

import java.time.LocalDateTime;

public class DetectionRecord {

    private Long id;
    private String deviceId;
    private String authorId;
    private RiskLevel riskLevel;
    private Double score;
    private String rawAiResult;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public String getRawAiResult() { return rawAiResult; }
    public void setRawAiResult(String rawAiResult) { this.rawAiResult = rawAiResult; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
