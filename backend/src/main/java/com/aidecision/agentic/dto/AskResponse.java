package com.aidecision.agentic.dto;

public record AskResponse(
        String runId,
        String status,
        String pollPath
) {}
