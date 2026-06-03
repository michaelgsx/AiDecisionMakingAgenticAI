package com.aidecision.agentic.dto;

import java.time.Instant;

public record AsyncChatPollResponse(
        String requestId,
        String status,
        String statusDetail,
        String question,
        String answer,
        String runId,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {}
