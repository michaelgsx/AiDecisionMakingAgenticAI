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
import java.util.function.Predicate;

@Component
public class WorkflowDagValidator {

    private final OrchestratorProperties props;

    public WorkflowDagValidator(OrchestratorProperties props) {
        this.props = props;
    }

    /** Throws {@link IllegalArgumentException} on first validation error (legacy). */
    public void validate(WorkflowDag dag, Map<String, OrchestratorTool> toolsByName) {
        WorkflowValidationResult result = validateExecutable(dag, toolsByName, false, tool -> true);
        if (!result.valid()) {
            throw new IllegalArgumentException(String.join("; ", result.errors()));
        }
    }

    /**
     * Validates workflow is executable: known tools, acyclic deps, optional sync-only,
     * runtime executors present, and computes parallel execution waves.
     */
    public WorkflowValidationResult validateExecutable(
            WorkflowDag dag,
            Map<String, OrchestratorTool> toolsByName,
            boolean syncOnly,
            Predicate<String> hasExecutor) {
        List<String> errors = new ArrayList<>();

        if (dag == null || dag.steps() == null || dag.steps().isEmpty()) {
            return WorkflowValidationResult.fail(List.of("Workflow must contain at least one step"));
        }
        if (dag.steps().size() > props.getMaxStepsPerWorkflow()) {
            errors.add("Workflow exceeds max steps: " + props.getMaxStepsPerWorkflow());
        }

        Set<String> ids = new HashSet<>();
        for (WorkflowDag.WorkflowStepDef step : dag.steps()) {
            if (step.id() == null || step.id().isBlank()) {
                errors.add("Each step requires a non-blank id");
                continue;
            }
            if (!ids.add(step.id())) {
                errors.add("Duplicate step id: " + step.id());
            }

            if (step.isGate()) {
                if (step.expression() == null || step.expression().isBlank()) {
                    errors.add("Gate " + step.id() + " requires expression");
                }
                if ((step.thenSteps() == null || step.thenSteps().isEmpty())
                        && (step.elseSteps() == null || step.elseSteps().isEmpty())) {
                    errors.add("Gate " + step.id() + " requires at least one then or else target step");
                }
                continue;
            }

            if (step.tool() == null || step.tool().isBlank()) {
                errors.add("Step " + step.id() + " missing tool name");
                continue;
            }
            OrchestratorTool toolMeta = toolsByName.get(step.tool());
            if (toolMeta == null) {
                errors.add("Unknown or disabled tool: " + step.tool());
            } else if (syncOnly && !"SYNC".equalsIgnoreCase(toolMeta.getExecutionMode())) {
                errors.add("Sync-only execution cannot run ASYNC tool: " + step.tool());
            }
            if (hasExecutor != null && !step.isGate() && !hasExecutor.test(step.tool())) {
                errors.add("No runtime executor for tool: " + step.tool());
            }

            List<String> deps = step.dependsOn() == null ? List.of() : step.dependsOn();
            for (String dep : deps) {
                if (dep == null || dep.isBlank()) {
                    errors.add("Step " + step.id() + " has blank dependency id");
                } else if (dep.equals(step.id())) {
                    errors.add("Step " + step.id() + " depends on itself");
                } else if (!ids.contains(dep) && !containsId(dag.steps(), dep)) {
                    errors.add("Step " + step.id() + " depends on unknown step: " + dep);
                }
            }
        }

        Set<String> allIds = dag.steps().stream()
                .map(WorkflowDag.WorkflowStepDef::id)
                .filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        for (WorkflowDag.WorkflowStepDef step : dag.steps()) {
            if (step.isGate()) {
                validateGateTargets(step, allIds, errors);
            }
        }

        List<List<String>> waves = List.of();
        try {
            waves = computeExecutionWaves(dag);
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
        }

        if (!errors.isEmpty()) {
            return WorkflowValidationResult.fail(errors);
        }
        return WorkflowValidationResult.ok(waves);
    }

    /**
     * Topological layers: steps in the same wave have no dependency on each other and may run in parallel.
     */
    public List<List<String>> computeExecutionWaves(WorkflowDag dag) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();

        for (WorkflowDag.WorkflowStepDef s : dag.steps()) {
            inDegree.putIfAbsent(s.id(), 0);
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

        List<List<String>> waves = new ArrayList<>();
        int visited = 0;
        while (!q.isEmpty()) {
            int waveSize = q.size();
            List<String> wave = new ArrayList<>(waveSize);
            for (int i = 0; i < waveSize; i++) {
                String id = q.removeFirst();
                wave.add(id);
                visited++;
                for (String next : adj.getOrDefault(id, List.of())) {
                    int deg = inDegree.get(next) - 1;
                    inDegree.put(next, deg);
                    if (deg == 0) {
                        q.addLast(next);
                    }
                }
            }
            waves.add(wave);
        }

        if (visited != inDegree.size()) {
            throw new IllegalArgumentException("Workflow DAG contains a cycle (dead loop)");
        }
        return waves;
    }

    private static boolean containsId(List<WorkflowDag.WorkflowStepDef> steps, String id) {
        return steps.stream().anyMatch(s -> s.id().equals(id));
    }

    private static void validateGateTargets(WorkflowDag.WorkflowStepDef gate, Set<String> ids, List<String> errors) {
        for (String target : gate.thenSteps()) {
            if (target == null || target.isBlank()) {
                errors.add("Gate " + gate.id() + " has blank then target");
            } else if (target.equals(gate.id())) {
                errors.add("Gate " + gate.id() + " cannot target itself in then");
            } else if (!ids.contains(target)) {
                errors.add("Gate " + gate.id() + " then target unknown step: " + target);
            }
        }
        for (String target : gate.elseSteps()) {
            if (target == null || target.isBlank()) {
                errors.add("Gate " + gate.id() + " has blank else target");
            } else if (target.equals(gate.id())) {
                errors.add("Gate " + gate.id() + " cannot target itself in else");
            } else if (!ids.contains(target)) {
                errors.add("Gate " + gate.id() + " else target unknown step: " + target);
            }
        }
    }
}
