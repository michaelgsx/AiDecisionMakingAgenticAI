package com.aidecision.agentic.dto;

import java.util.UUID;

public record ToolCancelRequest(
        UUID requestId,
        UUID runId,
        String stepKey,
        String reason
) {}
