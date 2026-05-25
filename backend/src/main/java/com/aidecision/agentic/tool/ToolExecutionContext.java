package com.aidecision.agentic.tool;

import java.util.Map;
import java.util.UUID;

public record ToolExecutionContext(
        UUID runId,
        String stepKey,
        String question,
        Map<String, String> priorOutputsByStepKey
) {}
