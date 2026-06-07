package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.repository.OrchestratorStepRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Evaluates condition gates and marks untaken branches as SKIPPED. */
@Service
public class WorkflowGateRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkflowGateRunner.class);

    private final OrchestratorStepRepository stepRepo;
    private final GateConditionEvaluator conditionEvaluator;
    private final ObjectMapper mapper;

    public WorkflowGateRunner(
            OrchestratorStepRepository stepRepo,
            GateConditionEvaluator conditionEvaluator,
            ObjectMapper mapper) {
        this.stepRepo = stepRepo;
        this.conditionEvaluator = conditionEvaluator;
        this.mapper = mapper;
    }

    public boolean isGateStep(OrchestratorStep step) {
        return WorkflowDag.GATE_TOOL_NAME.equals(step.getToolName());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WorkflowStepRunner.StepRunResult runGate(UUID runId, UUID stepId) {
        OrchestratorStep gate = stepRepo.findById(stepId)
                .orElseThrow(() -> new IllegalStateException("Gate step not found: " + stepId));
        if (!StepStatus.RUNNING.name().equals(gate.getStatus())
                && !StepStatus.READY.name().equals(gate.getStatus())) {
            return new WorkflowStepRunner.StepRunResult(WorkflowStepRunner.StepOutcome.FAILED, "Gate not runnable");
        }

        gate.setStatus(StepStatus.RUNNING.name());
        if (gate.getStartedAt() == null) {
            gate.setStartedAt(Instant.now());
        }
        stepRepo.save(gate);

        try {
            GateConfig config = readGateConfig(gate);
            List<OrchestratorStep> allSteps = stepRepo.findByRunIdOrderByCreatedAtAsc(runId);
            Map<String, String> outputs = completedOutputs(allSteps);
            boolean takenThen = conditionEvaluator.evaluate(config.expression(), outputs);

            List<String> takenRoots = takenThen ? config.thenSteps() : config.elseSteps();
            List<String> skippedRoots = takenThen ? config.elseSteps() : config.thenSteps();
            String branch = takenThen ? "then" : "else";

            Map<String, Object> gateOutput = Map.of(
                    "branch", branch,
                    "result", takenThen,
                    "expression", config.expression(),
                    "takenSteps", takenRoots,
                    "skippedSteps", skippedRoots);
            gate.setOutputJson(mapper.writeValueAsString(gateOutput));
            gate.setStatus(StepStatus.COMPLETED.name());
            gate.setFinishedAt(Instant.now());
            gate.setErrorMessage(null);
            stepRepo.save(gate);

            skipBranch(runId, skippedRoots, allSteps);

            log.info("Run {} gate {} evaluated expression={} branch={} skipped={}",
                    runId, gate.getStepKey(), config.expression(), branch, skippedRoots);
            return new WorkflowStepRunner.StepRunResult(WorkflowStepRunner.StepOutcome.COMPLETED, null);
        } catch (Exception e) {
            gate.setStatus(StepStatus.FAILED.name());
            gate.setFinishedAt(Instant.now());
            gate.setErrorMessage(truncate(e.getMessage()));
            stepRepo.save(gate);
            return new WorkflowStepRunner.StepRunResult(WorkflowStepRunner.StepOutcome.FAILED, e.getMessage());
        }
    }

    private void skipBranch(UUID runId, List<String> rootStepKeys, List<OrchestratorStep> allSteps) {
        if (rootStepKeys == null || rootStepKeys.isEmpty()) {
            return;
        }
        Map<String, List<String>> childrenByDep = buildChildrenIndex(allSteps);
        Set<String> toSkip = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(rootStepKeys);
        while (!queue.isEmpty()) {
            String stepKey = queue.removeFirst();
            if (!toSkip.add(stepKey)) {
                continue;
            }
            for (String child : childrenByDep.getOrDefault(stepKey, List.of())) {
                queue.addLast(child);
            }
        }

        Map<String, OrchestratorStep> byKey = allSteps.stream()
                .collect(Collectors.toMap(OrchestratorStep::getStepKey, s -> s, (a, b) -> a));
        for (String stepKey : toSkip) {
            OrchestratorStep step = byKey.get(stepKey);
            if (step == null) {
                continue;
            }
            String status = step.getStatus();
            if (StepStatus.COMPLETED.name().equals(status) || StepStatus.SKIPPED.name().equals(status)) {
                continue;
            }
            step.setStatus(StepStatus.SKIPPED.name());
            step.setFinishedAt(Instant.now());
            step.setErrorMessage(null);
            stepRepo.save(step);
            log.debug("Run {} step {} SKIPPED by gate branch", runId, stepKey);
        }
    }

    private Map<String, List<String>> buildChildrenIndex(List<OrchestratorStep> steps) {
        Map<String, List<String>> children = new HashMap<>();
        for (OrchestratorStep step : steps) {
            for (String dep : readDeps(step)) {
                children.computeIfAbsent(dep, k -> new ArrayList<>()).add(step.getStepKey());
            }
        }
        return children;
    }

    private Map<String, String> completedOutputs(List<OrchestratorStep> steps) {
        Map<String, String> outputs = new HashMap<>();
        for (OrchestratorStep step : steps) {
            if (StepStatus.COMPLETED.name().equals(step.getStatus()) && step.getOutputJson() != null) {
                outputs.put(step.getStepKey(), step.getOutputJson());
            }
        }
        return outputs;
    }

    private GateConfig readGateConfig(OrchestratorStep gate) throws Exception {
        Map<String, Object> config = mapper.readValue(
                gate.getInputJson() == null || gate.getInputJson().isBlank() ? "{}" : gate.getInputJson(),
                new TypeReference<Map<String, Object>>() {});
        String expression = stringField(config.get("expression"));
        List<String> thenSteps = stringList(config.get("then"));
        List<String> elseSteps = stringList(config.get("else"));
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Gate " + gate.getStepKey() + " missing expression");
        }
        if (thenSteps.isEmpty() && elseSteps.isEmpty()) {
            throw new IllegalArgumentException("Gate " + gate.getStepKey() + " requires then and/or else step lists");
        }
        return new GateConfig(expression, thenSteps, elseSteps);
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
            return out;
        }
        return List.of(String.valueOf(raw));
    }

    private static String stringField(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private List<String> readDeps(OrchestratorStep step) {
        try {
            if (step.getDependsOnJson() == null || step.getDependsOnJson().isBlank()) {
                return List.of();
            }
            return mapper.readValue(step.getDependsOnJson(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "gate evaluation failed";
        }
        return message.substring(0, Math.min(512, message.length()));
    }

    private record GateConfig(String expression, List<String> thenSteps, List<String> elseSteps) {}
}
