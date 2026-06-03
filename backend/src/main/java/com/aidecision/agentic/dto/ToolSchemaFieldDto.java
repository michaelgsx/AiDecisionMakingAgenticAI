package com.aidecision.agentic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One input or output field for tool registration (planner reads descriptions). */
public record ToolSchemaFieldDto(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 64) String type,
        @NotBlank @Size(max = 2000) String description
) {}
