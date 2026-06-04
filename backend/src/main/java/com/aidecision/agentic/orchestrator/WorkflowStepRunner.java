package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.repository.OrchestratorRunRepository;
import com.aidecision.agentic.repository.OrchestratorStepRepository;
import com.aidecision.agentic.service.AsyncChatStatusService;
import com.aidecision.agentic.tool.HttpToolInvoker;
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
    private final AsyncChatStatusService asyncChatStatus;
    private final HttpToolInvoker httpToolInvoker;
    private final OrchestratorProperties props;
    private final ObjectMapper mapper;
    private final WorkflowStepRunner self;

    public WorkflowStepRunner(
            OrchestratorRunRepository runRepo,
            OrchestratorStepRepository stepRepo,
            ToolRegistryService toolRegistry,
            AsyncChatStatusService asyncChatStatus,
            HttpToolInvoker httpToolInvoker,
            OrchestratorProperties props,
            ObjectMapper mapper,
            @org.springframework.context.annotation.Lazy WorkflowStepRunner self) {
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.toolRegistry = toolRegistry;
        this.asyncChatStatus = asyncChatStatus;
        this.httpToolInvoker = httpToolInvoker;
        this.props = props;
        this.mapper = mapper;
        this.self = self;
    }

    public enum StepOutcome {
        COMPLETED,
        FAILED,
        RETRY_READY,
        ASYNC_RUNNING
    }

    public record StepRunResult(StepOutcome outcome, String errorMessage) {}

    /** Data captured from the DB while marking a step RUNNING, used to invoke the tool with no tx held. */
    private record StepInvocation(
            String stepKey,
            String toolName,
            String question,
            Map<String, Object> params,
            Map<String, String> priorOutputs) {}

    /**
     * Runs one step in three phases so that NO database transaction (and therefore no row lock) is
     * held while the tool executes over HTTP: (1) mark RUNNING and commit, (2) invoke the tool with
     * no transaction open, (3) persist the outcome in a fresh transaction. Holding a lock across the
     * blocking loopback HTTP call could otherwise stall the orchestrator's reader threads.
     */
    public StepRunResult runSyncStep(UUID runId, UUID stepId) {
        StepInvocation inv = self.beginStep(runId, stepId);

        ToolExecutionContext ctx =
                new ToolExecutionContext(runId, inv.stepKey(), inv.question(), inv.priorOutputs());
        ToolResult result;
        try {
            result = invokeTool(inv.toolName(), ctx, inv.params());
        } catch (Exception e) {
            log.warn("Run {} step {} tool {} failed: {}",
                    runId, inv.stepKey(), inv.toolName(), LogSanitizer.message(e.getMessage()));
            result = ToolResult.fail(e.getMessage());
        }

        return self.finishStep(runId, stepId, result);
    }

    /** Phase 1: claim the step (RUNNING, attempt++) and snapshot what the tool needs. Commits fast. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StepInvocation beginStep(UUID runId, UUID stepId) {
        // The planning transaction commits run + steps just before this REQUIRES_NEW worker runs.
        // Under load that commit can be momentarily invisible to this fresh transaction, so re-query
        // a few times (READ_COMMITTED sees newer commits on each statement) before giving up.
        OrchestratorRun run = loadWithRetry(() -> runRepo.findById(runId).orElse(null),
                "Run not found (not yet committed?): " + runId);
        OrchestratorStep step = loadWithRetry(() -> stepRepo.findById(stepId).orElse(null),
                "Step not found (not yet committed?): " + stepId);
        Map<String, OrchestratorStep> stepsByKey = stepRepo.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .collect(java.util.stream.Collectors.toMap(OrchestratorStep::getStepKey, s -> s, (a, b) -> a));

        step.setStatus(StepStatus.RUNNING.name());
        step.setStartedAt(Instant.now());
        step.setAttemptCount(step.getAttemptCount() + 1);
        stepRepo.save(step);
        asyncChatStatus.markStepStarted(runId, step.getStepKey(), step.getToolName());
        log.info("Run {} step {} tool {} attempt {}/{} started",
                runId,
                step.getStepKey(),
                step.getToolName(),
                step.getAttemptCount(),
                maxAttemptsForTool(step.getToolName()));

        return new StepInvocation(
                step.getStepKey(),
                step.getToolName(),
                run.getQuestion(),
                readParams(step),
                collectPriorOutputs(step, stepsByKey));
    }

    /** Phase 3: persist the tool outcome (COMPLETED / FAILED / retry / async-pending) in its own tx. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StepRunResult finishStep(UUID runId, UUID stepId, ToolResult result) {
        OrchestratorStep step = loadWithRetry(() -> stepRepo.findById(stepId).orElse(null),
                "Step not found (not yet committed?): " + stepId);
        try {
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
            log.warn("Run {} step {} tool {} persist failed: {}",
                    runId,
                    step.getStepKey(),
                    step.getToolName(),
                    LogSanitizer.message(e.getMessage()));
            return failOrRetry(step, e.getMessage());
        }
    }

    /**
     * Invokes the tool over HTTP at its registered endpointUrl when enabled, otherwise calls the
     * in-process executor bean directly (fallback / local dev).
     */
    private ToolResult invokeTool(String toolName, ToolExecutionContext ctx, Map<String, Object> params) {
        if (props.isInvokeToolsOverHttp()) {
            return httpToolInvoker.invoke(toolName, ctx, params);
        }
        return toolRegistry.requireExecutor(toolName).execute(ctx, params);
    }

    /** Re-queries an entity a few times to tolerate brief commit-visibility lag across transactions. */
    private <T> T loadWithRetry(java.util.function.Supplier<T> loader, String notFoundMessage) {
        for (int attempt = 1; attempt <= 6; attempt++) {
            T value = loader.get();
            if (value != null) {
                return value;
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException(notFoundMessage);
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
