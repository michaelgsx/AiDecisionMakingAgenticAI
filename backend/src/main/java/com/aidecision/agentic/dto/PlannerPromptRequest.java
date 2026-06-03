package com.aidecision.agentic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlannerPromptRequest(
        @NotBlank @Size(max = 16_000) String question
) {}
