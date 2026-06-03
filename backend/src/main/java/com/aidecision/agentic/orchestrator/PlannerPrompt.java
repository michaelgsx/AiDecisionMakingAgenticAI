package com.aidecision.agentic.orchestrator;

/** Three-part LLM planner input: system rules, user payload (question + tools + output schema). */
public record PlannerPrompt(
        String systemPrompt,
        String userPrompt,
        String outputJsonSchema
) {}
