package com.aidecision.agentic.dto;

import java.util.Map;
import java.util.UUID;

public record ToolPollRequest(
        Map<String, Object> priorOutput,
        String question,
        UUID runId,
        String stepKey,
        Map<String, String> priorOutputsByStepKey
) {}
