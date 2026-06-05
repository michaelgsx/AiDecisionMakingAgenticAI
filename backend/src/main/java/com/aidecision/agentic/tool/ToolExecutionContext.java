package com.aidecision.agentic.tool;

import java.util.Map;
import java.util.UUID;

public record ToolExecutionContext(
        UUID runId,
        String stepKey,
        String question,
        Map<String, String> priorOutputsByStepKey,
        /** Run owner; blank resolves to {@link com.aidecision.agentic.service.UserTableAccessService#DEFAULT_USER_ID}. */
        String userId
) {}
