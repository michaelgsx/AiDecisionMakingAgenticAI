package com.aidecision.agentic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(
        @NotBlank String runId,
        @NotBlank String messageId,
        String conversationId,
        @NotBlank @Pattern(regexp = "up|down", message = "rating must be up or down") String rating,
        @Size(max = 2000) String comment
) {}
