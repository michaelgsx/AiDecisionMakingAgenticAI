package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.repository.OrchestratorStepRepository;
import com.aidecision.agentic.tool.AsyncAgentTool;
import com.aidecision.agentic.tool.AgentTool;
import com.aidecision.agentic.tool.ToolExecutionContext;
import com.aidecision.agentic.tool.ToolRegistryService;
import com.aidecision.agentic.tool.ToolResult;
import com.aidecision.agentic.util.LogSanitizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class WorkflowExecutor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutor.class);

    private final OrchestratorStepRepository stepRepo;
    private final ToolRegistryService toolRegistry;
    private final WorkflowStepRunner stepRunner;
    private final OrchestratorProperties props;
    private final ObjectMapper mapper;
    private final Executor workflowStepExecutor;

    public WorkflowExecutor(
            OrchestratorStepRepository stepRepo,
            ToolRegistryService toolRegistry,
            WorkflowStepRunner stepRunner,
            OrchestratorProperties props,
            ObjectMapper mapper,
            @Qualifier("workflowStepExecutor") Executor workflowStepExecutor) {
        this.stepRepo = stepRepo;
        this.toolRegistry = toolRegistry;
        this.stepRunner = stepRunner;
        this.props = props;
        this.mapper = mapper;
        this.workflowStepExecutor = workflowStepExecutor;
    }

    /**
     * Runs the workflow to completion assuming SYNC tools only.
     * Steps in the same topological wave execute in parallel.
     *
     * @return true if run may continue (not failed); false if a step failed permanently
     */
    public boolean runSyncWorkflowToCompletion(OrchestratorRun run) {
        int maxRounds = props.getMaxStepsPerWorkflow() * 3;
        for (int round = 0; round < maxRounds; round++) {
            List<OrchestratorStep> steps = stepRepo.findByRunIdOrderByCreatedAtAsc(run.getRunId());
            if (steps.isEmpty()) {
                return true;
            }
            if (anyFailed(steps)) {
                return false;
            }
            if (allCompleted(steps)) {
                return true;
            }

            markReadySteps(steps);
            steps = stepRepo.findByRunIdOrderByCreatedAtAsc(run.getRunId());

            List<OrchestratorStep> ready = steps.stream()
                    .filter(s -> StepStatus.READY.name().equals(s.getStatus()))
                    .toList();

            if (ready.isEmpty()) {
                if (anyRunning(steps)) {
                    log.debug("Run {} waiting on RUNNING steps (async not supported in sync mode)", run.getRunId());
                    return true;
                }
                log.error("Run {} deadlock: pending steps but none READY", run.getRunId());
                return false;
            }

            log.info("Run {} execution round {} running {} step(s): {}",
                    run.getRunId(),
                    round + 1,
                    ready.size(),
                    ready.stream().map(s -> s.getStepKey() + "/" + s.getToolName()).toList());

            if (!executeReadyWaveParallel(run, ready)) {
                return false;
            }
        }
        log.error("Run {} exceeded max workflow execution rounds", run.getRunId());
        return false;
    }

    /** @deprecated prefer {@link #runSyncWorkflowToCompletion}; kept for worker single-tick advance */
    @Transactional
    public boolean executeReadySteps(OrchestratorRun run, List<OrchestratorStep> steps) {
        markReadySteps(steps);
        steps = stepRepo.findByRunIdOrderByCreatedAtAsc(run.getRunId());
        List<OrchestratorStep> ready = steps.stream()
                .filter(s -> StepStatus.READY.name().equals(s.getStatus()))
                .toList();
        if (ready.isEmpty()) {
            return handleRunningSteps(run, steps);
        }
        return executeReadyWaveParallel(run, ready);
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
                log.debug("Run {} step {} marked READY", step.getRunId(), step.getStepKey());
            }
        }
    }

    boolean executeReadyWaveParallel(OrchestratorRun run, List<OrchestratorStep> readySteps) {
        List<CompletableFuture<WorkflowStepRunner.StepRunResult>> futures = new ArrayList<>();
        for (OrchestratorStep step : readySteps) {
            var toolMeta = toolRegistry.enabledToolsByName().get(step.getToolName());
            if (toolMeta != null && !"SYNC".equalsIgnoreCase(toolMeta.getExecutionMode())) {
                failStep(step, "Sync workflow cannot execute ASYNC tool: " + step.getToolName());
                return false;
            }
            UUID stepId = step.getStepId();
            futures.add(CompletableFuture.supplyAsync(
                    () -> stepRunner.runSyncStep(run.getRunId(), stepId),
                    workflowStepExecutor));
        }

        boolean anyFailed = false;
        boolean anyRetry = false;
        for (int i = 0; i < futures.size(); i++) {
            WorkflowStepRunner.StepRunResult result = futures.get(i).join();
            if (result.outcome() == WorkflowStepRunner.StepOutcome.FAILED) {
                anyFailed = true;
            } else if (result.outcome() == WorkflowStepRunner.StepOutcome.RETRY_READY) {
                anyRetry = true;
            } else if (result.outcome() == WorkflowStepRunner.StepOutcome.ASYNC_RUNNING) {
                OrchestratorStep asyncStep = readySteps.get(i);
                failStep(asyncStep, "Unexpected ASYNC tool in sync workflow: " + asyncStep.getToolName());
                return false;
            }
        }

        if (anyFailed) {
            return false;
        }
        if (anyRetry) {
            log.debug("Run {} has steps scheduled for retry", run.getRunId());
        } else {
            log.info("Run {} completed parallel wave of {} step(s)", run.getRunId(), readySteps.size());
        }
        return true;
    }

    private boolean handleRunningSteps(OrchestratorRun run, List<OrchestratorStep> steps) {
        Map<String, OrchestratorStep> byKey = steps.stream()
                .collect(Collectors.toMap(OrchestratorStep::getStepKey, s -> s));
        boolean progressed = false;
        for (OrchestratorStep step : steps) {
            if (!StepStatus.RUNNING.name().equals(step.getStatus())) {
                continue;
            }
            if (isTimedOut(step)) {
                failStep(step, "Step timed out");
                return false;
            }
            if (pollAsyncStep(run, step, byKey)) {
                progressed = true;
            }
        }
        return progressed;
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
            ToolResult result = async.poll(buildContext(run, step, byKey), prior);
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
        log.warn("Run {} step {} tool {} failed: {}",
                step.getRunId(),
                step.getStepKey(),
                step.getToolName(),
                LogSanitizer.message(message));
    }

    private static boolean allCompleted(List<OrchestratorStep> steps) {
        return steps.stream().allMatch(s -> StepStatus.COMPLETED.name().equals(s.getStatus()));
    }

    private static boolean anyFailed(List<OrchestratorStep> steps) {
        return steps.stream().anyMatch(s ->
                StepStatus.FAILED.name().equals(s.getStatus())
                        || StepStatus.TIMED_OUT.name().equals(s.getStatus()));
    }

    private static boolean anyRunning(List<OrchestratorStep> steps) {
        return steps.stream().anyMatch(s -> StepStatus.RUNNING.name().equals(s.getStatus()));
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
        Map<String, String> prior = new HashMap<>();
        for (String dep : readDeps(step)) {
            OrchestratorStep d = byKey.get(dep);
            if (d != null && d.getOutputJson() != null) {
                prior.put(dep, d.getOutputJson());
            }
        }
        return new ToolExecutionContext(run.getRunId(), step.getStepKey(), run.getQuestion(), prior);
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
}
