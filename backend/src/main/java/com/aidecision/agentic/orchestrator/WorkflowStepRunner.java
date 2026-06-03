package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.repository.OrchestratorRunRepository;
import com.aidecision.agentic.repository.OrchestratorStepRepository;
import com.aidecision.agentic.tool.AgentTool;
import com.aidecision.agentic.tool.ToolExecutionContext;
import com.aidecision.agentic.tool.ToolRegistryService;
import com.aidecision.agentic.tool.ToolResult;
import com.aidecision.agentic.util.LogSanitizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runs a single workflow step in its own transaction (safe for parallel execution). */
@Service
public class WorkflowStepRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStepRunner.class);

    private final OrchestratorRunRepository runRepo;
    private final OrchestratorStepRepository stepRepo;
    private final ToolRegistryService toolRegistry;
    private final ObjectMapper mapper;

    public WorkflowStepRunner(
            OrchestratorRunRepository runRepo,
            OrchestratorStepRepository stepRepo,
            ToolRegistryService toolRegistry,
            ObjectMapper mapper) {
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.toolRegistry = toolRegistry;
        this.mapper = mapper;
    }

    public enum StepOutcome {
        COMPLETED,
        FAILED,
        RETRY_READY,
        ASYNC_RUNNING
    }

    public record StepRunResult(StepOutcome outcome, String errorMessage) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StepRunResult runSyncStep(UUID runId, UUID stepId) {
        OrchestratorRun run = runRepo.findById(runId)
                .orElseThrow(() -> new IllegalStateException("Run not found (not yet committed?): " + runId));
        OrchestratorStep step = stepRepo.findById(stepId)
                .orElseThrow(() -> new IllegalStateException("Step not found (not yet committed?): " + stepId));
        Map<String, OrchestratorStep> stepsByKey = stepRepo.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .collect(java.util.stream.Collectors.toMap(OrchestratorStep::getStepKey, s -> s, (a, b) -> a));

        step.setStatus(StepStatus.RUNNING.name());
        step.setStartedAt(Instant.now());
        step.setAttemptCount(step.getAttemptCount() + 1);
        stepRepo.save(step);
        log.info("Run {} step {} tool {} attempt {}/{} started",
                runId,
                step.getStepKey(),
                step.getToolName(),
                step.getAttemptCount(),
                maxAttemptsForTool(step.getToolName()));

        try {
            Map<String, String> prior = collectPriorOutputs(step, stepsByKey);
            Map<String, Object> params = readParams(step);
            AgentTool tool = toolRegistry.requireExecutor(step.getToolName());
            ToolResult result = tool.execute(
                    new ToolExecutionContext(run.getRunId(), step.getStepKey(), run.getQuestion(), prior),
                    params);

            if (!result.success()) {
                return failOrRetry(step, result.errorMessage());
            }

            step.setOutputJson(mapper.writeValueAsString(result.output()));
            if (result.asyncPending()) {
                step.setFinishedAt(null);
                step.setStatus(StepStatus.RUNNING.name());
                stepRepo.save(step);
                return new StepRunResult(StepOutcome.ASYNC_RUNNING, null);
            }

            step.setFinishedAt(Instant.now());
            step.setStatus(StepStatus.COMPLETED.name());
            stepRepo.save(step);
            log.info("Run {} step {} tool {} completed output={}",
                    runId,
                    step.getStepKey(),
                    step.getToolName(),
                    LogSanitizer.jsonSummary(step.getOutputJson()));
            return new StepRunResult(StepOutcome.COMPLETED, null);
        } catch (Exception e) {
            log.warn("Run {} step {} tool {} failed: {}",
                    runId,
                    step.getStepKey(),
                    step.getToolName(),
                    LogSanitizer.message(e.getMessage()));
            return failOrRetry(step, e.getMessage());
        }
    }

    private StepRunResult failOrRetry(OrchestratorStep step, String message) {
        int maxAttempts = maxAttemptsForTool(step.getToolName());
        if (step.getAttemptCount() < maxAttempts) {
            step.setStatus(StepStatus.READY.name());
            step.setErrorMessage(null);
            step.setStartedAt(null);
            step.setFinishedAt(null);
            step.setOutputJson(null);
            stepRepo.save(step);
            log.warn("Run {} step {} tool {} retry scheduled ({}/{}): {}",
                    step.getRunId(),
                    step.getStepKey(),
                    step.getToolName(),
                    step.getAttemptCount(),
                    maxAttempts,
                    LogSanitizer.message(message));
            return new StepRunResult(StepOutcome.RETRY_READY, message);
        }
        step.setStatus(StepStatus.FAILED.name());
        step.setErrorMessage(truncate(message));
        step.setFinishedAt(Instant.now());
        stepRepo.save(step);
        return new StepRunResult(StepOutcome.FAILED, truncate(message));
    }

    private int maxAttemptsForTool(String toolName) {
        var tool = toolRegistry.enabledToolsByName().get(toolName);
        return tool == null ? 1 : Math.max(1, tool.getMaxRetry());
    }

    private Map<String, String> collectPriorOutputs(OrchestratorStep step, Map<String, OrchestratorStep> stepsByKey) {
        Map<String, String> prior = new HashMap<>();
        for (String dep : readDeps(step)) {
            OrchestratorStep d = stepsByKey.get(dep);
            if (d != null && d.getOutputJson() != null) {
                prior.put(dep, d.getOutputJson());
            }
        }
        return prior;
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

    private static String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.substring(0, Math.min(512, message.length()));
    }
}
