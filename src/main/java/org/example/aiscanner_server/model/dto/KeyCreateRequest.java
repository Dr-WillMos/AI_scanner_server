package org.example.aiscanner_server.model.dto;

public record KeyCreateRequest(String keyName, String permissions, Integer rateLimit, String expiredAt) {}
