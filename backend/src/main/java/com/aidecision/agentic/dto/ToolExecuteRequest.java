package com.aidecision.agentic.dto;

import java.util.Map;
import java.util.UUID;

public record ToolExecuteRequest(
        Map<String, Object> params,
        String question,
        UUID runId,
        String stepKey,
        Map<String, String> priorOutputsByStepKey
) {}
