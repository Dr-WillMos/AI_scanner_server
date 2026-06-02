package org.example.aiscanner_server.service;

import org.example.aiscanner_server.model.enums.RiskLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class RiskCalculatorTest {

    private final RiskCalculator calculator = new RiskCalculator();

    @Test
    @DisplayName("关键词命中 → HIGH, score=1.0")
    void keywordHitReturnsHigh() {
        var result = calculator.calculate(0.0, 0.0, true);
        assertEquals(RiskLevel.HIGH, result.riskLevel());
        assertEquals(1.0, result.score(), 0.001);
        assertEquals("关键词命中", result.reason());
    }

    @Test
    @DisplayName("综合评分 >= 0.6 → HIGH")
    void compositeScoreHigh() {
        // 0.7*0.9 + 0.3*0.0 = 0.63
        var result = calculator.calculate(0.9, 0.0, false);
        assertEquals(RiskLevel.HIGH, result.riskLevel());
        assertEquals(0.63, result.score(), 0.001);
        assertEquals("综合评分过高", result.reason());
    }

    @Test
    @DisplayName("综合评分恰好 0.6 → HIGH")
    void compositeScoreExactlyHighThreshold() {
        // 0.7*0.6 + 0.3*0.6 = 0.42 + 0.18 = 0.60
        var result = calculator.calculate(0.6, 0.6, false);
        assertEquals(RiskLevel.HIGH, result.riskLevel());
        assertEquals(0.60, result.score(), 0.001);
    }

    @Test
    @DisplayName("综合评分 >= 0.3 且 < 0.6 → MEDIUM")
    void compositeScoreMedium() {
        // 0.7*0.5 + 0.3*0.3 = 0.35 + 0.09 = 0.44
        var result = calculator.calculate(0.5, 0.3, false);
        assertEquals(RiskLevel.MEDIUM, result.riskLevel());
        assertEquals(0.44, result.score(), 0.001);
        assertEquals("综合评分中等", result.reason());
    }

    @Test
    @DisplayName("综合评分恰好 0.3 → MEDIUM")
    void compositeScoreExactlyMediumThreshold() {
        // 0.7*0.3 + 0.3*0.3 = 0.21 + 0.09 = 0.30
        var result = calculator.calculate(0.3, 0.3, false);
        assertEquals(RiskLevel.MEDIUM, result.riskLevel());
        assertEquals(0.30, result.score(), 0.001);
    }

    @Test
    @DisplayName("综合评分 < 0.3 → SAFE")
    void compositeScoreSafe() {
        // 0.7*0.2 + 0.3*0.1 = 0.14 + 0.03 = 0.17
        var result = calculator.calculate(0.2, 0.1, false);
        assertEquals(RiskLevel.SAFE, result.riskLevel());
        assertEquals(0.17, result.score(), 0.001);
        assertNull(result.reason());
    }

    @Test
    @DisplayName("综合评分 0.29 → SAFE")
    void compositeScoreJustBelowMedium() {
        // 0.7*0.29 + 0.3*0.29 = 0.203 + 0.087 = 0.29
        var result = calculator.calculate(0.29, 0.29, false);
        assertEquals(RiskLevel.SAFE, result.riskLevel());
        assertEquals(0.29, result.score(), 0.001);
    }

    @Test
    @DisplayName("aiGlitchProb权重 0.7 占主导")
    void glitchProbHasHigherWeight() {
        // 0.7*0.8 + 0.3*0.1 = 0.56 + 0.03 = 0.59
        var result1 = calculator.calculate(0.8, 0.1, false);
        assertEquals(RiskLevel.MEDIUM, result1.riskLevel());
        assertEquals(0.59, result1.score(), 0.001);

        // 0.7*0.1 + 0.3*0.8 = 0.07 + 0.24 = 0.31
        var result2 = calculator.calculate(0.1, 0.8, false);
        assertEquals(RiskLevel.MEDIUM, result2.riskLevel());
        assertEquals(0.31, result2.score(), 0.001);
    }

    @Test
    @DisplayName("关键词命中时忽略概率值")
    void keywordHitIgnoresProbabilities() {
        var result = calculator.calculate(0.1, 0.1, true);
        assertEquals(RiskLevel.HIGH, result.riskLevel());
        assertEquals(1.0, result.score(), 0.001);
        assertEquals("关键词命中", result.reason());
    }
}
