package com.aidecision.agentic.dto;

public record FeedbackResponse(
        boolean ok,
        String feedbackId,
        String message
) {}
