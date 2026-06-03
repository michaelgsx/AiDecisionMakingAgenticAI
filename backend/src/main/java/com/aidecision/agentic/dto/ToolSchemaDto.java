package com.aidecision.agentic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Structured tool input/output schema for registration API. */
public record ToolSchemaDto(
        @Size(max = 2000) String description,
        @NotEmpty @Valid List<ToolSchemaFieldDto> fields
) {}
