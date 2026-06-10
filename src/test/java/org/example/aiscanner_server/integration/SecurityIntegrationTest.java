package org.example.aiscanner_server.integration;

import org.example.aiscanner_server.config.SecurityConfig;
import org.example.aiscanner_server.controller.BlacklistController;
import org.example.aiscanner_server.security.ApiKeyFilter;
import org.example.aiscanner_server.security.RateLimitFilter;
import org.example.aiscanner_server.service.ApiKeyService;
import org.example.aiscanner_server.service.BlacklistService;
import org.example.aiscanner_server.service.RateLimitService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = BlacklistController.class)
@Import({SecurityConfig.class, ApiKeyFilter.class})
class SecurityIntegrationTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private BlacklistService blacklistService;
    @MockitoBean private ApiKeyService apiKeyService;
    @MockitoBean private RateLimitService rateLimitService;
    @MockitoBean private RateLimitFilter rateLimitFilter;

    @Test
    @DisplayName("POST /api/v1/detect 无 API Key → 401")
    void detectWithoutApiKey() throws Exception {
        mvc.perform(post("/api/v1/detect")
                        .param("deviceId", "d1")
                        .param("authorId", "a1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("401")));
    }

    @Test
    @DisplayName("GET /api/v1/history 无 API Key → 401")
    void historyWithoutApiKey() throws Exception {
        mvc.perform(get("/api/v1/history")
                        .param("deviceId", "d1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/blacklist/authority 无 API Key → 401")
    void blacklistWithoutApiKey() throws Exception {
        mvc.perform(get("/api/v1/blacklist/authority"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("携带错误 API Key → 401")
    void wrongApiKey() throws Exception {
        mvc.perform(get("/api/v1/blacklist/authority")
                        .header("X-API-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("携带正确 Root Key → 200（root key 拥有 ADMIN 权限可访问黑名单）")
    void correctApiKey() throws Exception {
        mvc.perform(get("/api/v1/blacklist/authority")
                        .header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk());
    }
}
