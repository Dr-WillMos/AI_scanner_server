package org.example.aiscanner_server.model.dto;

public record KeyUpdateRequest(String keyName, String permissions, Integer rateLimit, String expiredAt) {}
