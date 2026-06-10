package org.example.aiscanner_server.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyRegisterResponse {

    private String apiKey;
    private LocalDateTime expiresAt;

    public KeyRegisterResponse(String apiKey, LocalDateTime expiresAt) {
        this.apiKey = apiKey;
        this.expiresAt = expiresAt;
    }

    public String getApiKey() { return apiKey; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
