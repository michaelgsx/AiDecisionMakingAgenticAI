package com.aidecision.agentic.dto;

import java.util.List;

/**
 * A workflow step blocked on an async tool — returned by {@code POST /agent/execute} and
 * {@code GET /agent/runs/{runId}} for frontend feedback or polling.
 */
public record PendingAsyncDto(
        /** Transaction / correlation id for this wait (human requestId or step-scoped id). */
        String requestId,
        String runId,
        String workflowId,
        String userId,
        /** DAG step key (unique within the run; same tool may appear on multiple steps). */
        String stepKey,
        String stepId,
        String toolName,
        String toolVersion,
        /** INPUT_REQUIRED | POLL_ONLY */
        String asyncKind,
        String prompt,
        String proposal,
        List<String> allowedDecisions,
        String feedbackPath,
        String pollPath
) {}
