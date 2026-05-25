package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.OrchestratorTool;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class WorkflowDagValidator {

    private final OrchestratorProperties props;

    public WorkflowDagValidator(OrchestratorProperties props) {
        this.props = props;
    }

    public void validate(WorkflowDag dag, Map<String, OrchestratorTool> toolsByName) {
        if (dag == null || dag.steps() == null || dag.steps().isEmpty()) {
            throw new IllegalArgumentException("Workflow must contain at least one step");
        }
        if (dag.steps().size() > props.getMaxStepsPerWorkflow()) {
            throw new IllegalArgumentException("Workflow exceeds max steps: " + props.getMaxStepsPerWorkflow());
        }

        Set<String> ids = new HashSet<>();
        for (WorkflowDag.WorkflowStepDef step : dag.steps()) {
            if (step.id() == null || step.id().isBlank()) {
                throw new IllegalArgumentException("Each step requires a non-blank id");
            }
            if (!ids.add(step.id())) {
                throw new IllegalArgumentException("Duplicate step id: " + step.id());
            }
            if (step.tool() == null || !toolsByName.containsKey(step.tool())) {
                throw new IllegalArgumentException("Unknown or disabled tool: " + step.tool());
            }
            List<String> deps = step.dependsOn() == null ? List.of() : step.dependsOn();
            for (String dep : deps) {
                if (!ids.contains(dep) && !containsId(dag.steps(), dep)) {
                    throw new IllegalArgumentException("Step " + step.id() + " depends on unknown step: " + dep);
                }
            }
        }

        detectCycle(dag);
    }

    private static boolean containsId(List<WorkflowDag.WorkflowStepDef> steps, String id) {
        return steps.stream().anyMatch(s -> s.id().equals(id));
    }

    /** Kahn topological sort — throws if cycle (dead loop). */
    private void detectCycle(WorkflowDag dag) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();
        for (WorkflowDag.WorkflowStepDef s : dag.steps()) {
            inDegree.put(s.id(), 0);
        }
        for (WorkflowDag.WorkflowStepDef s : dag.steps()) {
            List<String> deps = s.dependsOn() == null ? List.of() : s.dependsOn();
            for (String dep : deps) {
                if (!inDegree.containsKey(dep)) {
                    throw new IllegalArgumentException("Step " + s.id() + " depends on unknown step: " + dep);
                }
                adj.computeIfAbsent(dep, k -> new ArrayList<>()).add(s.id());
                inDegree.put(s.id(), inDegree.get(s.id()) + 1);
            }
        }

        Deque<String> q = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) {
                q.add(e.getKey());
            }
        }

        int visited = 0;
        while (!q.isEmpty()) {
            String id = q.removeFirst();
            visited++;
            for (String next : adj.getOrDefault(id, List.of())) {
                int deg = inDegree.get(next) - 1;
                inDegree.put(next, deg);
                if (deg == 0) {
                    q.add(next);
                }
            }
        }

        if (visited != inDegree.size()) {
            throw new IllegalArgumentException("Workflow DAG contains a cycle (dead loop)");
        }
    }
}
