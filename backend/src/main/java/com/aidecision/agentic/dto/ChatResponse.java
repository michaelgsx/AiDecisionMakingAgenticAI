package com.aidecision.agentic.dto;

public record ChatResponse(
        String conversationId,
        String messageId,
        String answer,
        String model,
        boolean mock
) {}
