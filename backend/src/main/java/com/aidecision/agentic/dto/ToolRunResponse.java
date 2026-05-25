package com.aidecision.agentic.dto;

import java.util.Map;

public record ToolRunResponse(
        String toolName,
        boolean success,
        Map<String, Object> output,
        String error,
        boolean asyncPending
) {}
