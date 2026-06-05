package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.repository.OrchestratorRunRepository;
import com.aidecision.agentic.repository.OrchestratorStepRepository;
import com.aidecision.agentic.service.AsyncChatStatusService;
import com.aidecision.agentic.service.QaEvaluationService;
import com.aidecision.agentic.tool.ToolRegistryService;
import com.aidecision.agentic.util.LogSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class OrchestratorEngine {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorEngine.class);

    /**
     * Runs currently being driven by a thread in THIS JVM. Multiple paths can ask to process the
     * same run at once (the {@code @Async} chat processor fires immediately while the scheduled
     * {@link OrchestratorWorker} also polls it). Without this guard both threads enter
     * {@link #executeRunningWorkflow}/{@link #planAndPersistWorkflow}, re-plan, and the
     * {@code deleteByRunId} of one wipes the in-flight steps of the other ("Step not found").
     */
    private final Set<UUID> inFlightRuns = ConcurrentHashMap.newKeySet();

    private final OrchestratorRunRepository runRepo;
    private final OrchestratorStepRepository stepRepo;
    private final WorkflowPlannerService planner;
    private final WorkflowExecutor executor;
    private final WorkflowDagValidator dagValidator;
    private final ToolRegistryService toolRegistry;
    private final QaEvaluationService evaluationService;
    private final AsyncChatStatusService asyncChatStatus;
    private final OrchestratorProperties props;
    private final ObjectMapper mapper;
    private final OrchestratorEngine self;

    public OrchestratorEngine(
            OrchestratorRunRepository runRepo,
            OrchestratorStepRepository stepRepo,
            WorkflowPlannerService planner,
            WorkflowExecutor executor,
            WorkflowDagValidator dagValidator,
            ToolRegistryService toolRegistry,
            QaEvaluationService evaluationService,
            AsyncChatStatusService asyncChatStatus,
            OrchestratorProperties props,
            ObjectMapper mapper,
            @Lazy OrchestratorEngine self) {
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.planner = planner;
        this.executor = executor;
        this.dagValidator = dagValidator;
        this.toolRegistry = toolRegistry;
        this.evaluationService = evaluationService;
        this.asyncChatStatus = asyncChatStatus;
        this.props = props;
        this.mapper = mapper;
        this.self = self;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrchestratorRun submitQuestion(String question, UUID conversationId, String userId) {
        OrchestratorRun run = new OrchestratorRun();
        run.setQuestion(question.trim());
        run.setConversationId(conversationId);
        run.setUserId(blankToNull(userId));
        run.setStatus(RunStatus.PENDING.name());
        OrchestratorRun saved = runRepo.save(run);
        log.info("Run {} submitted question={} userId={} conversationId={}",
                saved.getRunId(),
                LogSanitizer.question(saved.getQuestion()),
                LogSanitizer.userId(saved.getUserId()),
                conversationId);
        return saved;
    }

    public void processRun(UUID runId) {
        // Guard against two threads in this JVM driving the same run at once (e.g. the @Async
        // chat processor and the scheduled worker both picking it up). Only one may proceed;
        // the other returns immediately and will re-poll later if there is still work to do.
        if (!inFlightRuns.add(runId)) {
            log.debug("Run {} already being processed in this JVM; skipping", runId);
            return;
        }
        try {
            doProcessRun(runId);
        } finally {
            inFlightRuns.remove(runId);
        }
    }

    private void doProcessRun(UUID runId) {
        OrchestratorRun run = runRepo.findById(runId).orElse(null);
        if (run == null) {
            return;
        }

        try {
            log.debug("Run {} processRun status={}", runId, run.getStatus());
            switch (RunStatus.valueOf(run.getStatus())) {
                case PENDING -> {
                    // Atomically claim the run so a concurrent worker (e.g. an old container
                    // draining during deploy) cannot plan the same run twice.
                    if (runRepo.compareAndSetStatus(
                            runId, RunStatus.PENDING.name(), RunStatus.PLANNING.name()) == 0) {
                        log.debug("Run {} already claimed by another worker; skipping", runId);
                        return;
                    }
                    run.setStatus(RunStatus.PLANNING.name());
                    self.planAndPersistWorkflow(run);
                    executeRunningWorkflow(run.getRunId());
                }
                case RUNNING -> executeRunningWorkflow(run.getRunId());
                default -> { }
            }
        } catch (InsufficientToolsException e) {
            log.warn("Orchestrator insufficient tools for run {}: {}", runId, LogSanitizer.message(e.getMessage()));
            failRun(run, formatInsufficientToolsMessage(e));
        } catch (Exception e) {
            log.error("Orchestrator failed run {}: {}", runId, LogSanitizer.message(e.getMessage()), e);
            failRun(run, e.getMessage());
        }
    }

    /**
     * Plans workflow and persists run + steps. Commits before execution so parallel step workers
     * ({@link WorkflowStepRunner} REQUIRES_NEW) can load rows from the database.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void planAndPersistWorkflow(OrchestratorRun run) throws Exception {
        log.info("Run {} planAndPersistWorkflow question={}", run.getRunId(), LogSanitizer.question(run.getQuestion()));
        run.setStatus(RunStatus.PLANNING.name());
        runRepo.save(run);
        asyncChatStatus.markPlanning(run.getRunId());

        WorkflowDag dag = planner.plan(run.getQuestion());
        WorkflowValidationResult validation = dagValidator.validateExecutable(
                dag, toolRegistry.enabledToolsByName(), true, toolRegistry::hasExecutor);
        if (!validation.valid()) {
            log.warn("Run {} workflow validation failed: {}", run.getRunId(),
                    LogSanitizer.text(String.join("; ", validation.errors())));
            throw new IllegalArgumentException("Workflow validation failed: "
                    + String.join("; ", validation.errors()));
        }
        log.info("Run {} planned {} steps across {} execution wave(s)",
                run.getRunId(), dag.steps().size(), validation.executionWaves().size());

        run.setWorkflowJson(mapper.writeValueAsString(dag));
        persistSteps(run, dag);

        run.setStatus(RunStatus.RUNNING.name());
        runRepo.save(run);
    }

    /** Executes workflow steps after {@link #planAndPersistWorkflow} has committed. */
    public void executeRunningWorkflow(UUID runId) throws Exception {
        OrchestratorRun run = runRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown runId: " + runId));
        executeSyncWorkflow(run);
    }

    private void executeSyncWorkflow(OrchestratorRun run) throws Exception {
        log.info("Run {} executeSyncWorkflow started", run.getRunId());
        if (!executor.runSyncWorkflowToCompletion(run)) {
            List<OrchestratorStep> steps = stepRepo.findByRunIdOrderByCreatedAtAsc(run.getRunId());
            if (steps.stream().anyMatch(s -> StepStatus.FAILED.name().equals(s.getStatus()))) {
                String detail = firstFailedStepMessage(steps);
                log.warn("Run {} failed: {}", run.getRunId(), LogSanitizer.message(detail));
                failRun(run, detail);
            } else {
                log.warn("Run {} failed: deadlock or retry limit exceeded", run.getRunId());
                failRun(run, "Workflow execution deadlock or exceeded retry limit");
            }
            return;
        }

        List<OrchestratorStep> steps = stepRepo.findByRunIdOrderByCreatedAtAsc(run.getRunId());
        if (steps.stream().allMatch(s -> StepStatus.COMPLETED.name().equals(s.getStatus()))) {
            completeRun(run, steps);
        }
    }

    private void persistSteps(OrchestratorRun run, WorkflowDag dag) throws Exception {
        // Bulk-delete + flush before inserts so re-planning never collides with Hibernate's
        // default INSERT-before-DELETE flush ordering on the (run_id, step_key) unique key.
        stepRepo.deleteByRunId(run.getRunId());

        for (WorkflowDag.WorkflowStepDef def : dag.steps()) {
            OrchestratorStep step = new OrchestratorStep();
            step.setRunId(run.getRunId());
            step.setStepKey(def.id());
            step.setToolName(def.tool());
            step.setStatus(StepStatus.PENDING.name());
            step.setDependsOnJson(mapper.writeValueAsString(def.dependsOn() == null ? List.of() : def.dependsOn()));
            step.setInputJson(mapper.writeValueAsString(def.params() == null ? Map.of() : def.params()));
            int maxTime = def.maxTimeMs() != null ? def.maxTimeMs() : (int) props.getDefaultStepMaxTimeMs();
            int timeout = def.timeoutMs() != null ? def.timeoutMs() : (int) props.getDefaultStepTimeoutMs();
            step.setMaxTimeMs(maxTime);
            step.setTimeoutMs(timeout);
            stepRepo.save(step);
            log.debug("Run {} persisted step {} tool={} deps={}",
                    run.getRunId(), def.id(), def.tool(), def.dependsOn());
        }
    }

    private void completeRun(OrchestratorRun run, List<OrchestratorStep> steps) throws Exception {
        String answer = extractFinalAnswer(steps);
        run.setAnswerText(answer);
        run.setStatus(RunStatus.COMPLETED.name());
        run.setErrorMessage(null);
        run.setCheckpointJson(mapper.writeValueAsString(Map.of("phase", "completed")));
        runRepo.save(run);
        asyncChatStatus.markDone(run.getRunId(), answer);
        evaluationService.enqueueCompletedRun(run);
        log.info("Run {} completed answerLen={} steps={}",
                run.getRunId(),
                answer == null ? 0 : answer.length(),
                steps.size());
    }

    private String extractFinalAnswer(List<OrchestratorStep> steps) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            OrchestratorStep s = steps.get(i);
            if ("llm_answer".equals(s.getToolName()) && s.getOutputJson() != null) {
                try {
                    Map<?, ?> out = mapper.readValue(s.getOutputJson(), Map.class);
                    Object a = out.get("answer");
                    if (a != null) {
                        return a.toString();
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return steps.stream()
                .filter(s -> s.getOutputJson() != null)
                .map(OrchestratorStep::getOutputJson)
                .collect(Collectors.joining("\n\n"));
    }

    public void resumeRun(OrchestratorRun run) throws Exception {
        if (run.getWorkflowJson() == null || run.getWorkflowJson().isBlank()) {
            self.planAndPersistWorkflow(run);
        } else {
            self.resetFailedStepsForResume(run);
        }
        executeRunningWorkflow(run.getRunId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void resetFailedStepsForResume(OrchestratorRun run) {
        run.setStatus(RunStatus.RUNNING.name());
        run.setErrorMessage(null);
        runRepo.save(run);

        List<OrchestratorStep> steps = stepRepo.findByRunIdOrderByCreatedAtAsc(run.getRunId());
        for (OrchestratorStep step : steps) {
            if (StepStatus.FAILED.name().equals(step.getStatus()) || StepStatus.TIMED_OUT.name().equals(step.getStatus())) {
                step.setStatus(StepStatus.PENDING.name());
                step.setErrorMessage(null);
                step.setStartedAt(null);
                step.setFinishedAt(null);
                step.setOutputJson(null);
                stepRepo.save(step);
            }
        }
    }

    private void failRun(OrchestratorRun run, String message) {
        run.setStatus(RunStatus.FAILED.name());
        run.setErrorMessage(message);
        runRepo.save(run);
        asyncChatStatus.markFailed(run.getRunId(), message);
        log.warn("Run {} failed: {}", run.getRunId(), LogSanitizer.message(message));
    }

    private static String firstFailedStepMessage(List<OrchestratorStep> steps) {
        return steps.stream()
                .filter(s -> StepStatus.FAILED.name().equals(s.getStatus()))
                .findFirst()
                .map(s -> {
                    String err = s.getErrorMessage();
                    if (err != null && !err.isBlank()) {
                        return "Step " + s.getStepKey() + " (" + s.getToolName() + "): " + err;
                    }
                    return "Step " + s.getStepKey() + " (" + s.getToolName() + ") failed";
                })
                .orElse("One or more workflow steps failed");
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }

    private static String formatInsufficientToolsMessage(InsufficientToolsException e) {
        if (e.missingTools().isEmpty()) {
            return e.getMessage();
        }
        return e.getMessage() + " Missing: " + String.join(", ", e.missingTools());
    }
}
