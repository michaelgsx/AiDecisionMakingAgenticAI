package com.aidecision.agentic.orchestrator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** LLM-planned execution DAG persisted as JSON on orchestrator_run.workflow_json */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowDag(List<WorkflowStepDef> steps) {

    public static final String STEP_TYPE_TOOL = "tool";
    public static final String STEP_TYPE_GATE = "gate";
    /** Persisted orchestrator_step.tool_name for condition gates (no HTTP tool call). */
    public static final String GATE_TOOL_NAME = "_gate";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkflowStepDef(
            String id,
            /** {@code tool} (default) or {@code gate}. Omitted in legacy JSON → tool step. */
            String type,
            /** Registry tool name; omitted for gate steps. */
            String tool,
            List<String> dependsOn,
            Map<String, Object> params,
            Integer maxTimeMs,
            Integer timeoutMs,
            /** Gate only: e.g. {@code steps.s1.output.rowCount > 5} */
            String expression,
            @JsonProperty("then") List<String> thenSteps,
            @JsonProperty("else") List<String> elseSteps
    ) {
        public WorkflowStepDef {
            if (dependsOn == null) {
                dependsOn = List.of();
            }
            if (params == null) {
                params = Map.of();
            }
            if (thenSteps == null) {
                thenSteps = List.of();
            }
            if (elseSteps == null) {
                elseSteps = List.of();
            }
        }

        public boolean isGate() {
            return STEP_TYPE_GATE.equalsIgnoreCase(type);
        }

        public static WorkflowStepDef tool(
                String id,
                String tool,
                List<String> dependsOn,
                Map<String, Object> params,
                Integer maxTimeMs,
                Integer timeoutMs) {
            return new WorkflowStepDef(
                    id, null, tool, dependsOn, params, maxTimeMs, timeoutMs, null, null, null);
        }

        public static WorkflowStepDef gate(
                String id,
                List<String> dependsOn,
                String expression,
                List<String> thenSteps,
                List<String> elseSteps) {
            return new WorkflowStepDef(
                    id,
                    STEP_TYPE_GATE,
                    GATE_TOOL_NAME,
                    dependsOn,
                    Map.of(),
                    null,
                    null,
                    expression,
                    thenSteps,
                    elseSteps);
        }
    }
}
