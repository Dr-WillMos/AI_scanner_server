package org.example.aiscanner_server.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.example.aiscanner_server.model.enums.RiskLevel;
import org.springframework.stereotype.Component;
/**Metrics（度量指标） 不属于业务逻辑层（如 Controller、Service、DAO），而是属于基础设施层或可观测性层（Observability）,由监控系统或
 * 框架来暴露系统运行时的数据，如QPS，响应时间，以供运维和管理人员进行实时监控和事后分析。*/
@Component
public class DetectionMetrics {

    private final Counter blacklistHitCounter;
    private final Counter rateLimitExceededCounter;
    private final Timer aiCallTimer;
    private final MeterRegistry registry;

    public DetectionMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.blacklistHitCounter = Counter.builder("aiscanner.blacklist.hit")
                .description("Number of blacklist hits")
                .register(registry);
        this.rateLimitExceededCounter = Counter.builder("aiscanner.rate.limit.exceeded")
                .description("Number of rate limit exceeded events")
                .register(registry);
        this.aiCallTimer = Timer.builder("aiscanner.ai.call.duration")
                .description("AI service call duration")
                .register(registry);
    }

    public void recordDetection(RiskLevel riskLevel) {
        Counter.builder("aiscanner.detection.count")
                .description("Detection requests by risk level")
                .tag("riskLevel", riskLevel.name())
                .register(registry)
                .increment();
    }

    public void recordBlacklistHit() {
        blacklistHitCounter.increment();
    }

    public void recordRateLimitExceeded() {
        rateLimitExceededCounter.increment();
    }

    public Timer.Sample startAiTimer() {
        return Timer.start(registry);
    }

    public void stopAiTimer(Timer.Sample sample) {
        sample.stop(aiCallTimer);
    }

    public void recordDetectionBlacklisted() {
        Counter.builder("aiscanner.detection.count")
                .description("Detection requests by risk level")
                .tag("riskLevel", "BLACKLISTED")
                .register(registry)
                .increment();
    }
}
