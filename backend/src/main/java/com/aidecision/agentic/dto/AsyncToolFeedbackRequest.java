package com.aidecision.agentic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Submit user input for an {@link com.aidecision.agentic.orchestrator.AsyncToolKind#INPUT_REQUIRED} step.
 */
public record AsyncToolFeedbackRequest(
        @NotBlank String requestId,
        @NotBlank String stepKey,
        @Size(max = 128) String userId,
        String toolName,
        @Size(max = 128) String toolVersion,
        /** accept | reject for human_in_the_loop */
        @NotBlank String result,
        String comment,
        Map<String, Object> metadata
) {}
