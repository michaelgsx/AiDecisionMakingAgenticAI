package com.aidecision.agentic.orchestrator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders a {@link WorkflowDag} as a Mermaid flowchart (TD) for docs and UI. */
public final class WorkflowMermaidRenderer {

    private WorkflowMermaidRenderer() {}

    public static String toMermaid(WorkflowDag dag) {
        return toMermaid(dag, Map.of());
    }

    public static String toMermaid(WorkflowDag dag, Map<String, String> statusByStepKey) {
        if (dag == null || dag.steps() == null || dag.steps().isEmpty()) {
            return "flowchart TD\n  empty[\"No workflow steps\"]";
        }

        Map<String, String> statuses = statusByStepKey == null ? Map.of() : statusByStepKey;
        StringBuilder sb = new StringBuilder("flowchart TD\n");

        for (WorkflowDag.WorkflowStepDef step : dag.steps()) {
            String nodeId = mermaidNodeId(step.id());
            String label = escapeMermaidLabel(buildLabel(step, statuses.get(step.id())));
            sb.append("  ").append(nodeId).append("[\"").append(label).append("\"]\n");
        }

        for (WorkflowDag.WorkflowStepDef step : dag.steps()) {
            String target = mermaidNodeId(step.id());
            List<String> deps = step.dependsOn() == null ? List.of() : step.dependsOn();
            for (String dep : deps) {
                sb.append("  ").append(mermaidNodeId(dep)).append(" --> ").append(target).append("\n");
            }
        }

        sb.append("""
                  classDef wf_pending fill:#f8f9fa,stroke:#adb5bd,color:#495057
                  classDef wf_ready fill:#e7f1ff,stroke:#0d6efd,color:#084298
                  classDef wf_running fill:#fff3cd,stroke:#ffc107,color:#664d03
                  classDef wf_completed fill:#d1e7dd,stroke:#198754,color:#0f5132
                  classDef wf_failed fill:#f8d7da,stroke:#dc3545,color:#842029
                  classDef wf_skipped fill:#e2e3e5,stroke:#6c757d,color:#495057
                """);

        for (WorkflowDag.WorkflowStepDef step : dag.steps()) {
            String status = statuses.get(step.id());
            String className = classNameForStatus(status);
            if (className != null) {
                sb.append("  class ").append(mermaidNodeId(step.id())).append(" ").append(className).append("\n");
            }
        }

        return sb.toString().trim();
    }

    private static String buildLabel(WorkflowDag.WorkflowStepDef step, String status) {
        StringBuilder label = new StringBuilder();
        label.append(step.id()).append("<br/>").append(step.tool());
        if (status != null && !status.isBlank()) {
            label.append("<br/>").append(status.trim().toUpperCase());
        }
        return label.toString();
    }

    private static String mermaidNodeId(String stepKey) {
        String id = stepKey == null ? "step" : stepKey.replaceAll("[^a-zA-Z0-9_]", "_");
        if (id.isEmpty()) {
            id = "step";
        }
        if (Character.isDigit(id.charAt(0))) {
            id = "n_" + id;
        }
        return id;
    }

    private static String escapeMermaidLabel(String label) {
        return label.replace("\"", "#quot;");
    }

    private static String classNameForStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status.trim().toUpperCase()) {
            case "PENDING" -> "wf_pending";
            case "READY" -> "wf_ready";
            case "RUNNING" -> "wf_running";
            case "COMPLETED" -> "wf_completed";
            case "FAILED", "TIMED_OUT" -> "wf_failed";
            case "SKIPPED" -> "wf_skipped";
            default -> null;
        };
    }

    /** stepKey → status from run poll response. */
    public static Map<String, String> statusMapFromSteps(List<StepStatusSource> steps) {
        Map<String, String> map = new LinkedHashMap<>();
        if (steps == null) {
            return map;
        }
        for (StepStatusSource s : steps) {
            if (s.stepKey() != null && s.status() != null) {
                map.put(s.stepKey(), s.status());
            }
        }
        return map;
    }

    public record StepStatusSource(String stepKey, String status) {}
}
