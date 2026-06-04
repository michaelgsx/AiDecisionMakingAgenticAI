package com.aidecision.agentic.dto;

import java.time.Instant;

/** Tool row returned by GET/POST /agent/tools (structured schemas + metadata). */
public record ToolRegistrationResponse(
        String name,
        String version,
        int maxRetry,
        String description,
        String toolType,
        String executionMode,
        ToolSchemaDto inputSchema,
        ToolSchemaDto outputSchema,
        String endpointUrl,
        boolean enabled,
        boolean executorAvailable,
        Instant createdAt,
        Instant updatedAt
) {}
