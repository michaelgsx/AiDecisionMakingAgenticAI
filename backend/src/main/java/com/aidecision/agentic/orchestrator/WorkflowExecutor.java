package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.repository.OrchestratorStepRepository;
import com.aidecision.agentic.tool.AgentTool;
import com.aidecision.agentic.tool.AsyncAgentTool;
import com.aidecision.agentic.tool.ToolExecutionContext;
import com.aidecision.agentic.tool.ToolRegistryService;
import com.aidecision.agentic.tool.ToolResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkflowExecutor {

    private final OrchestratorStepRepository stepRepo;
    private final ToolRegistryService toolRegistry;
    private final OrchestratorProperties props;
    private final ObjectMapper mapper;

    public WorkflowExecutor(
            OrchestratorStepRepository stepRepo,
            ToolRegistryService toolRegistry,
            OrchestratorProperties props,
            ObjectMapper mapper) {
        this.stepRepo = stepRepo;
        this.toolRegistry = toolRegistry;
        this.props = props;
        this.mapper = mapper;
    }

    /** Execute all READY steps whose dependencies are COMPLETED; returns true if run may continue. */
    @Transactional
    public boolean executeReadySteps(OrchestratorRun run, List<OrchestratorStep> steps) {
        Map<String, OrchestratorStep> byKey = steps.stream()
                .collect(Collectors.toMap(OrchestratorStep::getStepKey, s -> s));

        boolean progressed = false;
        for (OrchestratorStep step : steps) {
            if (!StepStatus.READY.name().equals(step.getStatus())
                    && !StepStatus.RUNNING.name().equals(step.getStatus())) {
                continue;
            }

            if (StepStatus.RUNNING.name().equals(step.getStatus())) {
                if (isTimedOut(step)) {
                    failStep(step, "Step timed out");
                    return false;
                }
                if (pollAsyncStep(run, step, byKey)) {
                    progressed = true;
                }
                continue;
            }

            if (!dependenciesMet(step, byKey)) {
                continue;
            }

            progressed = true;
            runStep(run, step, byKey);
            if (StepStatus.FAILED.name().equals(step.getStatus())
                    || StepStatus.TIMED_OUT.name().equals(step.getStatus())) {
                return false;
            }
        }
        return progressed;
    }

    @Transactional
    public void markReadySteps(List<OrchestratorStep> steps) {
        Map<String, OrchestratorStep> byKey = steps.stream()
                .collect(Collectors.toMap(OrchestratorStep::getStepKey, s -> s));

        for (OrchestratorStep step : steps) {
            if (!StepStatus.PENDING.name().equals(step.getStatus())) {
                continue;
            }
            if (dependenciesMet(step, byKey)) {
                step.setStatus(StepStatus.READY.name());
                stepRepo.save(step);
            }
        }
    }

    private boolean pollAsyncStep(OrchestratorRun run, OrchestratorStep step, Map<String, OrchestratorStep> byKey) {
        AgentTool tool = toolRegistry.requireExecutor(step.getToolName());
        if (!(tool instanceof AsyncAgentTool async)) {
            return false;
        }
        try {
            Map<String, Object> prior = readOutputMap(step);
            if (prior.isEmpty()) {
                return false;
            }
            ToolResult result = async.poll(
                    buildContext(run, step, byKey),
                    prior);
            if (result.asyncPending()) {
                return false;
            }
            if (!result.success()) {
                failStep(step, result.errorMessage());
                return true;
            }
            step.setOutputJson(mapper.writeValueAsString(result.output()));
            step.setFinishedAt(Instant.now());
            step.setStatus(StepStatus.COMPLETED.name());
            stepRepo.save(step);
            return true;
        } catch (Exception e) {
            failStep(step, e.getMessage());
            return true;
        }
    }

    private void runStep(OrchestratorRun run, OrchestratorStep step, Map<String, OrchestratorStep> byKey) {
        step.setStatus(StepStatus.RUNNING.name());
        step.setStartedAt(Instant.now());
        step.setAttemptCount(step.getAttemptCount() + 1);
        stepRepo.save(step);

        try {
            Map<String, String> prior = new HashMap<>();
            List<String> deps = readDeps(step);
            for (String dep : deps) {
                OrchestratorStep d = byKey.get(dep);
                if (d != null && d.getOutputJson() != null) {
                    prior.put(dep, d.getOutputJson());
                }
            }

            Map<String, Object> params = readParams(step);
            AgentTool tool = toolRegistry.requireExecutor(step.getToolName());
            ToolResult result = tool.execute(buildContext(run, step, byKey, prior), params);

            if (!result.success()) {
                failStep(step, result.errorMessage());
                return;
            }

            step.setOutputJson(mapper.writeValueAsString(result.output()));
            if (result.asyncPending()) {
                step.setFinishedAt(null);
                step.setStatus(StepStatus.RUNNING.name());
            } else {
                step.setFinishedAt(Instant.now());
                step.setStatus(StepStatus.COMPLETED.name());
            }
            stepRepo.save(step);
        } catch (Exception e) {
            failStep(step, e.getMessage());
        }
    }

    private boolean dependenciesMet(OrchestratorStep step, Map<String, OrchestratorStep> byKey) {
        for (String dep : readDeps(step)) {
            OrchestratorStep d = byKey.get(dep);
            if (d == null || !StepStatus.COMPLETED.name().equals(d.getStatus())) {
                return false;
            }
        }
        return true;
    }

    private boolean isTimedOut(OrchestratorStep step) {
        if (step.getStartedAt() == null) {
            return false;
        }
        long elapsed = Duration.between(step.getStartedAt(), Instant.now()).toMillis();
        return elapsed > step.getTimeoutMs();
    }

    private void failStep(OrchestratorStep step, String message) {
        step.setStatus(StepStatus.FAILED.name());
        step.setErrorMessage(message == null ? "unknown error" : message.substring(0, Math.min(512, message.length())));
        step.setFinishedAt(Instant.now());
        stepRepo.save(step);
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

    private ToolExecutionContext buildContext(
            OrchestratorRun run, OrchestratorStep step, Map<String, OrchestratorStep> byKey) {
        return buildContext(run, step, byKey, collectPriorOutputs(step, byKey));
    }

    private ToolExecutionContext buildContext(
            OrchestratorRun run,
            OrchestratorStep step,
            Map<String, OrchestratorStep> byKey,
            Map<String, String> prior) {
        return new ToolExecutionContext(run.getRunId(), step.getStepKey(), run.getQuestion(), prior);
    }

    private Map<String, String> collectPriorOutputs(OrchestratorStep step, Map<String, OrchestratorStep> byKey) {
        Map<String, String> prior = new HashMap<>();
        for (String dep : readDeps(step)) {
            OrchestratorStep d = byKey.get(dep);
            if (d != null && d.getOutputJson() != null) {
                prior.put(dep, d.getOutputJson());
            }
        }
        return prior;
    }

    private Map<String, Object> readOutputMap(OrchestratorStep step) {
        try {
            if (step.getOutputJson() == null || step.getOutputJson().isBlank()) {
                return Map.of();
            }
            return mapper.readValue(step.getOutputJson(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Object> readParams(OrchestratorStep step) {
        try {
            if (step.getInputJson() == null || step.getInputJson().isBlank()) {
                return Map.of();
            }
            return mapper.readValue(step.getInputJson(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
