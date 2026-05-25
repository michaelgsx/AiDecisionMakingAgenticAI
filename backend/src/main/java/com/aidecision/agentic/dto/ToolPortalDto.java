package com.aidecision.agentic.dto;

public record ToolPortalDto(
        String name,
        String version,
        String description,
        String toolType,
        String executionMode,
        String requestSchemaJson,
        String responseSchemaJson,
        boolean enabled
) {}
