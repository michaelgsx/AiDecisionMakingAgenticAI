package com.aidecision.agentic.dto;

public record EvaluationDto(
        String evaluationId,
        String runId,
        String question,
        String answer,
        String reviewStatus,
        String reviewerId,
        String comment,
        String createdAt,
        String reviewedAt
) {}
