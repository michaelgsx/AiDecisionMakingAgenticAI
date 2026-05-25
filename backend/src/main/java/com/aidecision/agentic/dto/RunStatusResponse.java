package com.aidecision.agentic.dto;

import java.util.List;

public record RunStatusResponse(
        String runId,
        String status,
        String question,
        String answer,
        String error,
        String workflowJson,
        List<StepStatusDto> steps,
        boolean waitingForHuman,
        List<HumanApprovalDto> pendingApprovals
) {
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
            String status,
            String error,
            Integer attemptCount
    ) {}
}
