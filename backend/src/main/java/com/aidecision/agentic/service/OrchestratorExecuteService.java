package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.AsyncToolFeedbackRequest;
import com.aidecision.agentic.dto.AsyncToolPollRequest;
import com.aidecision.agentic.dto.ExecuteRequest;
import com.aidecision.agentic.dto.ExecuteResponse;
import com.aidecision.agentic.dto.PendingAsyncDto;
import com.aidecision.agentic.dto.RunStatusResponse;
import com.aidecision.agentic.entity.OrchestratorHumanRequest;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.orchestrator.AsyncToolKind;
import com.aidecision.agentic.orchestrator.OrchestratorEngine;
import com.aidecision.agentic.orchestrator.RunStatus;
import com.aidecision.agentic.repository.OrchestratorRunRepository;
import com.aidecision.agentic.repository.OrchestratorStepRepository;
import com.aidecision.agentic.tool.AsyncToolSupport;
import com.aidecision.agentic.tool.ToolRegistryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrchestratorExecuteService {

    private final OrchestratorEngine engine;
    private final OrchestratorRunRepository runRepo;
    private final OrchestratorStepRepository stepRepo;
    private final OrchestratorRunAssembler assembler;
    private final HumanInLoopService humanInLoop;
    private final ToolRegistryService toolRegistry;
    private final ObjectMapper mapper;

    public OrchestratorExecuteService(
            OrchestratorEngine engine,
            OrchestratorRunRepository runRepo,
            OrchestratorStepRepository stepRepo,
            OrchestratorRunAssembler assembler,
            HumanInLoopService humanInLoop,
            ToolRegistryService toolRegistry,
            ObjectMapper mapper) {
        this.engine = engine;
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.assembler = assembler;
        this.humanInLoop = humanInLoop;
        this.toolRegistry = toolRegistry;
        this.mapper = mapper;
    }

    @Transactional
    public ExecuteResponse execute(ExecuteRequest request) throws InterruptedException {
        UUID conversationId = parseUuid(request.conversationId());
        OrchestratorRun run = engine.submitQuestion(request.question(), conversationId, request.userId());
        if (request.transactionId() != null && !request.transactionId().isBlank()) {
            storeTransactionId(run, request.transactionId().trim());
        }

        engine.processRun(run.getRunId());

        for (int i = 0; i < 40; i++) {
            RunStatusResponse status = loadRun(run.getRunId());
            if (isTerminal(status.status())) {
                return assembler.toExecuteResponse(status);
            }
            if (needsClientInput(status)) {
                markWaitingAsync(run.getRunId());
                return assembler.toExecuteResponse(loadRun(run.getRunId()));
            }
            if (hasPollOnlyPending(status)) {
                return assembler.toExecuteResponse(status);
            }
            engine.processRun(run.getRunId());
            Thread.sleep(200);
        }

        return assembler.toExecuteResponse(loadRun(run.getRunId()));
    }

    @Transactional
    public RunStatusResponse submitFeedback(UUID runId, AsyncToolFeedbackRequest body) {
        OrchestratorRun run = runRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown runId: " + runId));
        validateUser(run, body.userId());

        OrchestratorHumanRequest req = humanInLoop.getRequest(UUID.fromString(body.requestId().trim()));
        if (!req.getRunId().equals(runId)) {
            throw new IllegalArgumentException("requestId does not belong to runId");
        }
        if (!body.stepKey().equals(req.getStepKey())) {
            throw new IllegalArgumentException("stepKey does not match requestId");
        }

        OrchestratorStep step = findStep(runId, body.stepKey());

        if (body.toolName() != null && !body.toolName().isBlank()
                && !body.toolName().equals(step.getToolName())) {
            throw new IllegalArgumentException("toolName does not match step");
        }
        if (AsyncToolSupport.kind(step.getToolName()) != AsyncToolKind.INPUT_REQUIRED) {
            throw new IllegalArgumentException(
                    "Tool " + step.getToolName() + " does not accept feedback; use poll if POLL_ONLY");
        }
        if (body.toolVersion() != null && !body.toolVersion().isBlank()) {
            toolRegistry.requireRegistered(step.getToolName(), body.toolVersion().trim());
        }

        humanInLoop.answer(req.getRequestId(), body.result(), body.comment());
        run.setStatus(RunStatus.RUNNING.name());
        runRepo.save(run);

        try {
            engine.processRun(runId);
        } catch (Exception e) {
            throw new IllegalStateException("Could not resume run after feedback: " + e.getMessage(), e);
        }
        return loadRun(runId);
    }

    @Transactional
    public RunStatusResponse pollAsyncStep(UUID runId, AsyncToolPollRequest body) {
        OrchestratorRun run = runRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown runId: " + runId));

        OrchestratorStep step = findStep(runId, body.stepKey());

        if (body.toolName() != null && !body.toolName().isBlank()
                && !body.toolName().equals(step.getToolName())) {
            throw new IllegalArgumentException("toolName does not match step");
        }
        if (AsyncToolSupport.kind(step.getToolName()) != AsyncToolKind.POLL_ONLY) {
            throw new IllegalArgumentException(
                    "Tool " + step.getToolName() + " is not POLL_ONLY; use feedback for INPUT_REQUIRED");
        }
        if (body.toolVersion() != null && !body.toolVersion().isBlank()) {
            toolRegistry.requireRegistered(step.getToolName(), body.toolVersion().trim());
        }

        if (RunStatus.WAITING_ASYNC.name().equals(run.getStatus())) {
            run.setStatus(RunStatus.RUNNING.name());
            runRepo.save(run);
        }

        try {
            engine.processRun(runId);
        } catch (Exception e) {
            throw new IllegalStateException("Poll processing failed: " + e.getMessage(), e);
        }
        return loadRun(runId);
    }

    private RunStatusResponse loadRun(UUID runId) {
        OrchestratorRun run = runRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown runId: " + runId));
        return assembler.toRunStatus(run, stepRepo.findByRunIdOrderByCreatedAtAsc(runId));
    }

    private OrchestratorStep findStep(UUID runId, String stepKey) {
        return stepRepo.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .filter(s -> stepKey.equals(s.getStepKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown stepKey: " + stepKey));
    }

    private void markWaitingAsync(UUID runId) {
        OrchestratorRun run = runRepo.findById(runId).orElseThrow();
        if (!RunStatus.COMPLETED.name().equals(run.getStatus())
                && !RunStatus.FAILED.name().equals(run.getStatus())) {
            run.setStatus(RunStatus.WAITING_ASYNC.name());
            runRepo.save(run);
        }
    }

    private static boolean isTerminal(String status) {
        return RunStatus.COMPLETED.name().equals(status) || RunStatus.FAILED.name().equals(status);
    }

    private static boolean needsClientInput(RunStatusResponse status) {
        return status.pendingAsync().stream()
                .anyMatch(p -> AsyncToolKind.INPUT_REQUIRED.name().equals(p.asyncKind()));
    }

    private static boolean hasPollOnlyPending(RunStatusResponse status) {
        return status.pendingAsync().stream()
                .anyMatch(p -> AsyncToolKind.POLL_ONLY.name().equals(p.asyncKind()));
    }

    private void validateUser(OrchestratorRun run, String userId) {
        if (userId == null || userId.isBlank() || run.getUserId() == null) {
            return;
        }
        if (!userId.trim().equals(run.getUserId())) {
            throw new IllegalArgumentException("userId does not match run");
        }
    }

    private void storeTransactionId(OrchestratorRun run, String transactionId) {
        try {
            ObjectNode cp = mapper.createObjectNode();
            if (run.getCheckpointJson() != null && !run.getCheckpointJson().isBlank()) {
                cp = (ObjectNode) mapper.readTree(run.getCheckpointJson());
            }
            cp.put("transactionId", transactionId);
            run.setCheckpointJson(mapper.writeValueAsString(cp));
            runRepo.save(run);
        } catch (Exception e) {
            throw new IllegalStateException("Could not store transactionId: " + e.getMessage(), e);
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return UUID.fromString(raw.trim());
    }
}
