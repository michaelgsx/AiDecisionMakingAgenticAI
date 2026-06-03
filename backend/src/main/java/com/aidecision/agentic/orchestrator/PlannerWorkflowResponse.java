package com.aidecision.agentic.orchestrator;

import java.util.List;

/** Parsed planner LLM JSON — either an executable workflow or an insufficient-tools refusal. */
public record PlannerWorkflowResponse(
        String status,
        String message,
        List<String> missingTools,
        List<WorkflowDag.WorkflowStepDef> steps
) {
    public static final String STATUS_OK = "ok";
    public static final String STATUS_INSUFFICIENT_TOOLS = "insufficient_tools";

    public boolean isOk() {
        return STATUS_OK.equalsIgnoreCase(status);
    }

    public boolean isInsufficientTools() {
        return STATUS_INSUFFICIENT_TOOLS.equalsIgnoreCase(status);
    }

    public WorkflowDag toDag() {
        if (!isOk()) {
            throw new IllegalStateException("Cannot build DAG from non-ok planner response: " + status);
        }
        return new WorkflowDag(steps == null ? List.of() : steps);
    }
}
