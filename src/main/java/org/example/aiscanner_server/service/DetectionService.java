package org.example.aiscanner_server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.aiscanner_server.client.AiClient;
import org.example.aiscanner_server.mapper.DetectionRecordMapper;
import org.example.aiscanner_server.model.dto.AiResult;
import org.example.aiscanner_server.model.dto.DetectResponse;
import org.example.aiscanner_server.model.entity.DetectionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DetectionService {

    private static final Logger log = LoggerFactory.getLogger(DetectionService.class);

    private final BlacklistService blacklistService;
    private final AiClient aiClient;
    private final RiskCalculator riskCalculator;
    private final DetectionRecordMapper mapper;
    private final ObjectMapper objectMapper;

    public DetectionService(BlacklistService blacklistService, AiClient aiClient,
                            RiskCalculator riskCalculator, DetectionRecordMapper mapper,
                            ObjectMapper objectMapper) {
        this.blacklistService = blacklistService;
        this.aiClient = aiClient;
        this.riskCalculator = riskCalculator;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public DetectResponse detect(String deviceId, String authorId, MultipartFile video) {
        BlacklistService.BlacklistHit hit = blacklistService.checkBlacklist(authorId);
        if (hit.hit()) {
            log.info("黑名单命中, authorId={}, source={}", authorId, hit.source());
            return DetectResponse.blacklisted(hit.source(), hit.reason());
        }

        AiResult aiResult = aiClient.analyze(video);
        log.info("AI 分析完成, authorId={}, keywordHit={}, aiGlitchProb={}, violenceProb={}",
                authorId, aiResult.isKeywordHit(), aiResult.getAiGlitchProb(), aiResult.getViolenceProb());

        RiskCalculator.Result calcResult = riskCalculator.calculate(
                aiResult.getAiGlitchProb(), aiResult.getViolenceProb(), aiResult.isKeywordHit());

        DetectionRecord record = new DetectionRecord();
        record.setDeviceId(deviceId);
        record.setAuthorId(authorId);
        record.setRiskLevel(calcResult.riskLevel());
        record.setScore(calcResult.score());
        record.setRawAiResult(toJson(aiResult));
        mapper.insert(record);

        log.info("检测记录已保存, id={}, authorId={}, riskLevel={}",
                record.getId(), authorId, calcResult.riskLevel());

        // HIGH risk → auto-add to temp blacklist with 24h expiry
        if (calcResult.riskLevel() == org.example.aiscanner_server.model.enums.RiskLevel.HIGH) {
            blacklistService.addToTemp(authorId, calcResult.reason());
        }

        return DetectResponse.from(
                calcResult.riskLevel(),
                calcResult.score(),
                calcResult.reason(),
                aiResult.getTranscription());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("AI 结果序列化失败", e);
            return null;
        }
    }
}
