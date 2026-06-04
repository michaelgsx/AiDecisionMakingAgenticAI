package com.aidecision.agentic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Register or update a tool in {@code orchestrator_tool} (Azure SQL ai-rag-db-1).
 * Stored schemas are converted to JSON Schema for the LLM workflow planner.
 */
public record RegisterToolRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z][a-z0-9_]*") String name,
        @NotBlank @Size(max = 32) String version,
        @NotNull @Min(0) @Max(10) Integer maxRetry,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Pattern(regexp = "DATA_ACQUISITION|SIMILARITY_RETRIEVAL|LLM_REASONING|AGGREGATE|FEEDBACK|VALIDATION")
        String toolType,
        @NotBlank @Pattern(regexp = "SYNC|ASYNC") String executionMode,
        @NotNull @Valid ToolSchemaDto inputSchema,
        @NotNull @Valid ToolSchemaDto outputSchema,
        @Size(max = 256) String endpointUrl,
        boolean enabled
) {}
