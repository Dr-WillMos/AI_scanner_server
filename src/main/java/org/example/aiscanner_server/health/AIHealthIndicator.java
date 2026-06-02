package org.example.aiscanner_server.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AIHealthIndicator implements HealthIndicator {

    private final RestClient restClient;

    public AIHealthIndicator(RestClient aiRestClient) {
        this.restClient = aiRestClient;
    }

    @Override
    public Health health() {
        try {
            restClient.get().uri("/").exchange((req, resp) -> resp.getStatusCode());
            return Health.up().withDetail("service", "AI analysis service").build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("service", "AI analysis service")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
