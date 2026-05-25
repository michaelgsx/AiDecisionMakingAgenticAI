package com.aidecision.agentic.dto;

import java.util.Map;

/** Same shape as persisted {@code orchestrator_run.workflow_json} (steps array). */
public record WorkflowDiagramRequest(
        String workflowJson,
        Map<String, String> stepStatuses
) {}
