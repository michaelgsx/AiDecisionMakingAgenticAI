package com.aidecision.agentic.dto;

public record ToolCancelResponse(
        String toolName,
        boolean cancelled,
        String message
) {}
