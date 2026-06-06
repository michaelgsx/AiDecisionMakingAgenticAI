package com.aidecision.agentic.dto;

public record EvaluationDto(
        String evaluationId,
        String runId,
        /** RUN = end-to-end Q&A; STEP = single workflow step output. */
        String evaluationScope,
        String stepKey,
        String stepId,
        String toolName,
        /** Model-reported confidence in [0, 1] for this evaluation item. */
        double confidence,
        String question,
        String answer,
        String reviewStatus,
        String reviewerId,
        String comment,
        String createdAt,
        String reviewedAt
) {}
