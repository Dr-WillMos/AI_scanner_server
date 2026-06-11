package org.example.aiscanner_server.model.dto;

import jakarta.validation.constraints.NotBlank;

public record KeyRegisterRequest(@NotBlank String deviceId, String deviceName) {}
