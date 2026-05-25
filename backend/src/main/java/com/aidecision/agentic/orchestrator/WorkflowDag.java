package com.aidecision.agentic.orchestrator;

import java.util.List;
import java.util.Map;

/** LLM-planned execution DAG persisted as JSON on orchestrator_run.workflow_json */
public record WorkflowDag(List<WorkflowStepDef> steps) {

    public record WorkflowStepDef(
            String id,
            String tool,
            List<String> dependsOn,
            Map<String, Object> params,
            Integer maxTimeMs,
            Integer timeoutMs
    ) {}
}
