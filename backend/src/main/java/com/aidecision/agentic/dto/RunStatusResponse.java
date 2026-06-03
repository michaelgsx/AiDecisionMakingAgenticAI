package com.aidecision.agentic.dto;

import java.util.List;

public record RunStatusResponse(
        String runId,
        String status,
        String question,
        String answer,
        String error,
        String workflowJson,
        String workflowMermaid,
        String userId,
        String transactionId,
        List<StepStatusDto> steps,
        boolean waitingForAsync,
        List<PendingAsyncDto> pendingAsync,
        boolean waitingForHuman,
        List<HumanApprovalDto> pendingApprovals,
        RunPaths paths
) {
    public record RunPaths(String pollPath, String feedbackPath, String asyncPollPath) {}

    public record HumanApprovalDto(
            String requestId,
            String stepKey,
            String prompt,
            String proposal
    ) {}

    public record StepStatusDto(
            String stepId,
            String stepKey,
            String toolName,
            String toolVersion,
            String status,
            String error,
            Integer attemptCount,
            String outputJson
    ) {}

    public String pollPath() {
        return paths != null ? paths.pollPath() : null;
    }

    public String feedbackPath() {
        return paths != null ? paths.feedbackPath() : null;
    }
}
