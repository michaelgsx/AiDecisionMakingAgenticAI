package com.aidecision.agentic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExecuteRequest(
        @NotBlank @Size(max = 16_000) String question,
        String conversationId,
        @Size(max = 128) String userId,
        /** Optional client transaction id stored on the run checkpoint. */
        @Size(max = 128) String transactionId
) {}
