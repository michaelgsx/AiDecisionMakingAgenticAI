package com.aidecision.agentic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Advance a {@link com.aidecision.agentic.orchestrator.AsyncToolKind#POLL_ONLY} step (no user input).
 */
public record AsyncToolPollRequest(
        String requestId,
        @NotBlank String stepKey,
        String toolName,
        @Size(max = 128) String toolVersion
) {}
