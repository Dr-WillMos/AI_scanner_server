package org.example.aiscanner_server.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIHealthIndicatorTest {

    @Mock private RestClient restClient;
    @Mock private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private AIHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new AIHealthIndicator(restClient);
    }

    @Test
    @DisplayName("AI 服务可达 → UP")
    void aiServiceUp() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);

        var health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("AI analysis service", health.getDetails().get("service"));
    }

    @Test
    @DisplayName("AI 服务不可达 → DOWN")
    void aiServiceDown() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenThrow(new RuntimeException("Connection refused"));

        var health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("AI analysis service", health.getDetails().get("service"));
        assertNotNull(health.getDetails().get("error"));
    }
}
