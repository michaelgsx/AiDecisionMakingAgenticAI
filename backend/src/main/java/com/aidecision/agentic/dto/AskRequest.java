package com.aidecision.agentic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(
        @NotBlank @Size(max = 16_000) String question,
        String conversationId,
        @Size(max = 128) String userId
) {}
