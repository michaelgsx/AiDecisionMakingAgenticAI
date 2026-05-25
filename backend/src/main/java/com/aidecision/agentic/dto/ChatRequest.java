package com.aidecision.agentic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        String conversationId,
        @NotBlank @Size(max = 16_000) String message,
        @Size(max = 128) String userId
) {}
