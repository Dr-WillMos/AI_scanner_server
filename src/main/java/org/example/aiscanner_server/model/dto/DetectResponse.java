package org.example.aiscanner_server.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.aiscanner_server.model.enums.RiskLevel;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DetectResponse {

    private RiskLevel riskLevel;
    private String reason;
    private Double score;
    private String transcription;
    private String source;

    public static DetectResponse blacklisted(String source, String reason) {
        DetectResponse r = new DetectResponse();
        r.riskLevel = RiskLevel.HIGH;
        r.reason = reason;
        r.source = source;
        return r;
    }

    public static DetectResponse from(RiskLevel riskLevel, Double score, String reason, String transcription) {
        DetectResponse r = new DetectResponse();
        r.riskLevel = riskLevel;
        r.score = score;
        r.reason = reason;
        r.transcription = transcription;
        return r;
    }

    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getReason() { return reason; }
    public Double getScore() { return score; }
    public String getTranscription() { return transcription; }
    public String getSource() { return source; }
}
