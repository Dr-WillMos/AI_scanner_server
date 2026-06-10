package org.example.aiscanner_server.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class StatsService {

    private final MeterRegistry registry;
    private final BlacklistService blacklistService;

    public StatsService(MeterRegistry registry, BlacklistService blacklistService) {
        this.registry = registry;
        this.blacklistService = blacklistService;
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        // Detection counts by risk level (find() returns empty list if never recorded, get() throws)
        Map<String, Double> byRiskLevel = new HashMap<>();
        double total = 0;
        var counters = registry.find("aiscanner.detection.count").counters();
        for (var c : counters) {
            String level = c.getId().getTag("riskLevel");
            if (level != null) {
                double v = c.count();
                byRiskLevel.put(level, v);
                total += v;
            }
        }
        stats.put("totalDetections", (long) total);
        stats.put("byRiskLevel", byRiskLevel);

        // Blacklist hit count
        double blacklistHits = 0;
        var hitCounter = registry.find("aiscanner.blacklist.hit").counter();
        if (hitCounter != null) {
            blacklistHits = hitCounter.count();
        }
        stats.put("blacklistHits", (long) blacklistHits);

        // AI call duration
        Timer aiTimer = registry.find("aiscanner.ai.call.duration").timer();
        if (aiTimer != null && aiTimer.count() > 0) {
            stats.put("aiAvgDurationMs", aiTimer.mean(TimeUnit.MILLISECONDS));
            stats.put("aiCallCount", aiTimer.count());
        } else {
            stats.put("aiAvgDurationMs", 0.0);
            stats.put("aiCallCount", 0L);
        }

        // Blacklist sizes
        Map<String, Integer> blacklistCounts = new HashMap<>();
        blacklistCounts.put("authority", blacklistService.listAuthority().size());
        blacklistCounts.put("global", blacklistService.listGlobal().size());
        blacklistCounts.put("temp", blacklistService.listTemp().size());
        stats.put("blacklistCounts", blacklistCounts);

        return stats;
    }
}
