package com.aidecision.agentic.dto;

import java.util.List;

/**
 * Result of {@code POST /agent/execute}.
 * When all tools are SYNC, {@code completed} is true and {@code answer} is set.
 * Otherwise {@code pendingAsync} lists steps needing feedback or poll.
 */
public record ExecuteResponse(
        String runId,
        String workflowId,
        String status,
        boolean completed,
        boolean waitingForAsync,
        String question,
        String answer,
        String error,
        String userId,
        String transactionId,
        String workflowJson,
        String workflowMermaid,
        List<RunStatusResponse.StepStatusDto> steps,
        List<PendingAsyncDto> pendingAsync,
        String pollPath,
        String feedbackPath
) {}
