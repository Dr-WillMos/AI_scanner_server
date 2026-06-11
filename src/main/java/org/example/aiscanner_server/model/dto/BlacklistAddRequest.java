package org.example.aiscanner_server.model.dto;

import jakarta.validation.constraints.NotBlank;

public record BlacklistAddRequest(@NotBlank String authorId, String reason) {}
