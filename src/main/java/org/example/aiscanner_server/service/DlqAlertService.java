package org.example.aiscanner_server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
/**负责死信积压情况警告*/
@Component
public class DlqAlertService {

    private static final Logger log = LoggerFactory.getLogger(DlqAlertService.class);
    private static final long DEFAULT_THRESHOLD = 10;
    private static final long ALERT_COOLDOWN_MS = 30 * 60 * 1000; // 每次警告的间隔为半小时

    private final DlqService dlqService;
    private final AtomicLong lastAlertTime = new AtomicLong(0);

    public DlqAlertService(DlqService dlqService) {
        this.dlqService = dlqService;
    }

    @Scheduled(fixedRate = 300_000) //每五分钟执行一次，检查情况
    public void checkBacklog() {
        long count = dlqService.getPendingCount();
        if (count >= DEFAULT_THRESHOLD) {
            long now = System.currentTimeMillis();
            long last = lastAlertTime.get();
            if (now - last >= ALERT_COOLDOWN_MS) {   //大于警告间隔
                lastAlertTime.set(now);
                log.warn("DLQ backlog alert: {} pending messages (threshold: {})", count, DEFAULT_THRESHOLD);
            }
        }
    }
}
