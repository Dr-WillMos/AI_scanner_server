package org.example.aiscanner_server.service;

import org.example.aiscanner_server.client.AiClient;
import org.example.aiscanner_server.mapper.DetectionRecordMapper;
import org.example.aiscanner_server.metrics.DetectionMetrics;
import org.example.aiscanner_server.model.dto.AiResult;
import org.example.aiscanner_server.model.entity.DetectionRecord;
import org.example.aiscanner_server.model.enums.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DetectionServiceTest {

    @Mock private BlacklistService blacklistService;
    @Mock private AiClient aiClient;
    @Mock private DetectionRecordMapper mapper;
    @Mock private MultipartFile video;
    @Mock private DetectionMetrics metrics;

    private RiskCalculator riskCalculator;
    private DetectionService service;

    @BeforeEach
    void setUp() {
        riskCalculator = new RiskCalculator();
        service = new DetectionService(blacklistService, aiClient, riskCalculator,
                mapper, new ObjectMapper(), metrics);
    }

    @Test
    @DisplayName("黑名单命中 → 直接返回 HIGH，不调用 AI，不写 DB")
    void blacklistHitShortCircuits() {
        when(blacklistService.checkBlacklist("author123"))
                .thenReturn(new BlacklistService.BlacklistHit(true, "global", "全局黑名单发布者"));

        var result = service.detect("device1", "author123", video);

        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        assertEquals("global", result.getSource());
        assertEquals("全局黑名单发布者", result.getReason());
        assertNull(result.getScore());

        verifyNoInteractions(aiClient);
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("正常检测流程 → AI 调用 → 评分 → 入库 → 返回结果")
    void normalDetectionFlow() {
        when(blacklistService.checkBlacklist("author456"))
                .thenReturn(BlacklistService.BlacklistHit.miss());

        AiResult aiResult = new AiResult();
        aiResult.setAiGlitchProb(0.7);
        aiResult.setViolenceProb(0.5);
        aiResult.setKeywordHit(false);
        aiResult.setTranscription("test transcription");
        when(aiClient.analyze(video)).thenReturn(aiResult);

        var result = service.detect("device1", "author456", video);

        assertNotNull(result);
        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        assertNotNull(result.getScore());
        assertNull(result.getSource());

        verify(aiClient).analyze(video);

        ArgumentCaptor<DetectionRecord> captor = ArgumentCaptor.forClass(DetectionRecord.class);
        verify(mapper).insert(captor.capture());
        DetectionRecord record = captor.getValue();
        assertEquals("device1", record.getDeviceId());
        assertEquals("author456", record.getAuthorId());
        assertEquals(RiskLevel.HIGH, record.getRiskLevel());
        assertNotNull(record.getRawAiResult());
        assertNotNull(record.getScore());
    }

    @Test
    @DisplayName("HIGH 风险自动加入临时黑名单")
    void highRiskAddsToTempBlacklist() {
        when(blacklistService.checkBlacklist("author789"))
                .thenReturn(BlacklistService.BlacklistHit.miss());

        AiResult aiResult = new AiResult();
        aiResult.setAiGlitchProb(0.9);
        aiResult.setViolenceProb(0.8);
        aiResult.setKeywordHit(false);
        when(aiClient.analyze(video)).thenReturn(aiResult);

        service.detect("device1", "author789", video);

        verify(blacklistService).addToTemp(eq("author789"), anyString());
        verify(mapper).insert(any());
    }

    @Test
    @DisplayName("SAFE 风险不加入临时黑名单")
    void safeRiskDoesNotAddToTempBlacklist() {
        when(blacklistService.checkBlacklist("author000"))
                .thenReturn(BlacklistService.BlacklistHit.miss());

        AiResult aiResult = new AiResult();
        aiResult.setAiGlitchProb(0.1);
        aiResult.setViolenceProb(0.1);
        aiResult.setKeywordHit(false);
        when(aiClient.analyze(video)).thenReturn(aiResult);

        service.detect("device1", "author000", video);

        verify(blacklistService, never()).addToTemp(anyString(), anyString());
        verify(mapper).insert(any());
    }

    @Test
    @DisplayName("AI 调用异常时抛出 RuntimeException")
    void aiCallFailureThrowsRuntimeException() {
        when(blacklistService.checkBlacklist("author456"))
                .thenReturn(BlacklistService.BlacklistHit.miss());
        when(aiClient.analyze(video)).thenThrow(new RuntimeException("AI 服务调用失败: 连接超时"));

        var ex = assertThrows(RuntimeException.class,
                () -> service.detect("device1", "author456", video));
        assertTrue(ex.getMessage().contains("AI 服务调用失败"));
        verify(mapper, never()).insert(any());
    }

    @Test
    @DisplayName("关键词命中 → HIGH, score=1.0")
    void keywordHitReturnsHigh() {
        when(blacklistService.checkBlacklist("author111"))
                .thenReturn(BlacklistService.BlacklistHit.miss());

        AiResult aiResult = new AiResult();
        aiResult.setAiGlitchProb(0.1);
        aiResult.setViolenceProb(0.1);
        aiResult.setKeywordHit(true);
        aiResult.setTranscription("敏感内容");
        when(aiClient.analyze(video)).thenReturn(aiResult);

        var result = service.detect("device1", "author111", video);

        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        assertEquals(1.0, result.getScore());
        verify(blacklistService).addToTemp(eq("author111"), anyString());
    }
}
