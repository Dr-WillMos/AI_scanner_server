package org.example.aiscanner_server.integration;

import org.example.aiscanner_server.service.ApiKeyService;
import org.example.aiscanner_server.service.BlacklistService;
import org.example.aiscanner_server.service.DetectionService;
import org.example.aiscanner_server.service.HistoryService;
import org.example.aiscanner_server.service.RateLimitService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorHealthIntegrationTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private RedisConnectionFactory redisConnectionFactory;
    @MockitoBean private DetectionService detectionService;
    @MockitoBean private BlacklistService blacklistService;
    @MockitoBean private HistoryService historyService;
    @MockitoBean private ApiKeyService apiKeyService;
    @MockitoBean private RateLimitService rateLimitService;

    @Test
    @DisplayName("GET /actuator/health 无需 API Key 即可访问")
    void actuatorHealthWithoutApiKey() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("GET /actuator/info 无需 API Key → 200")
    void actuatorInfoWithoutApiKey() throws Exception {
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("业务接口需要 API Key")
    void businessEndpointRequiresApiKey() throws Exception {
        mvc.perform(get("/api/v1/blacklist/authority"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("正确 Root Key 可以访问业务接口")
    void businessEndpointWithCorrectApiKey() throws Exception {
        mvc.perform(get("/api/v1/blacklist/authority")
                        .header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk());
    }
}
