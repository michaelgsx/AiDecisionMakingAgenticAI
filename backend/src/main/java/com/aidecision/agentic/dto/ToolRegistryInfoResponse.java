package com.aidecision.agentic.dto;

public record ToolRegistryInfoResponse(
        String toolName,
        String version,
        String description,
        String toolType,
        String executionMode,
        boolean enabled,
        Object requestSchema,
        Object responseSchema,
        boolean supportsPoll,
        boolean supportsCancel
) {}
