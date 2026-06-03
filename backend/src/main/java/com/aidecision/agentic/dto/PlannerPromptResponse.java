package com.aidecision.agentic.dto;

public record PlannerPromptResponse(
        String systemPrompt,
        String userPrompt,
        String outputJsonSchema
) {}
