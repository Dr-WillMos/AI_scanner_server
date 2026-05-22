package org.example.aiscanner_server.service;

import org.example.aiscanner_server.model.enums.RiskLevel;
import org.springframework.stereotype.Component;

@Component
public class RiskCalculator {

    public Result calculate(double aiGlitchProb, double violenceProb, boolean keywordHit) {
        if (keywordHit) {
            return new Result(RiskLevel.HIGH, 1.0, "关键词命中");
        }

        double score = 0.7 * aiGlitchProb + 0.3 * violenceProb;

        if (score >= 0.6) {
            return new Result(RiskLevel.HIGH, score, "综合评分过高");
        } else if (score >= 0.3) {
            return new Result(RiskLevel.MEDIUM, score, "综合评分中等");
        } else {
            return new Result(RiskLevel.SAFE, score, null);
        }
    }

    public record Result(RiskLevel riskLevel, double score, String reason) {}
}
