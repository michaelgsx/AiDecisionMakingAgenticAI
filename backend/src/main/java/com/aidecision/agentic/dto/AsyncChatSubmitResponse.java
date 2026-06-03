package com.aidecision.agentic.dto;

public record AsyncChatSubmitResponse(
        String requestId,
        String status,
        String pollPath
) {}
