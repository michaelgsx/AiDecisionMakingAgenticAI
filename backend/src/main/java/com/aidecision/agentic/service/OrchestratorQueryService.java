package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.AsyncToolFeedbackRequest;
import com.aidecision.agentic.dto.HumanResponseRequest;
import com.aidecision.agentic.dto.RunStatusResponse;
import com.aidecision.agentic.entity.OrchestratorHumanRequest;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.orchestrator.OrchestratorEngine;
import com.aidecision.agentic.orchestrator.RunStatus;
import com.aidecision.agentic.repository.OrchestratorRunRepository;
import com.aidecision.agentic.repository.OrchestratorStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrchestratorQueryService {

    private final OrchestratorRunRepository runRepo;
    private final OrchestratorStepRepository stepRepo;
    private final OrchestratorEngine engine;
    private final HumanInLoopService humanInLoop;
    private final OrchestratorRunAssembler assembler;
    private final OrchestratorExecuteService executeService;

    public OrchestratorQueryService(
            OrchestratorRunRepository runRepo,
            OrchestratorStepRepository stepRepo,
            OrchestratorEngine engine,
            HumanInLoopService humanInLoop,
            OrchestratorRunAssembler assembler,
            OrchestratorExecuteService executeService) {
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.engine = engine;
        this.humanInLoop = humanInLoop;
        this.assembler = assembler;
        this.executeService = executeService;
    }

    @Transactional(readOnly = true)
    public RunStatusResponse getRun(UUID runId) {
        OrchestratorRun run = runRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown runId: " + runId));
        return assembler.toRunStatus(run, stepRepo.findByRunIdOrderByCreatedAtAsc(runId));
    }

    @Transactional
    public RunStatusResponse submitHumanResponse(UUID runId, UUID requestId, String decision, String comment) {
        OrchestratorStep step = stepRepo.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .filter(s -> {
                    OrchestratorHumanRequest req = humanInLoop.findByRunAndStepKey(runId, s.getStepKey());
                    return req != null && requestId.equals(req.getRequestId());
                })
                .findFirst()
                .orElse(null);
        String stepKey = step != null ? step.getStepKey() : humanInLoop.getRequest(requestId).getStepKey();
        return executeService.submitFeedback(
                runId,
                new AsyncToolFeedbackRequest(
                        requestId.toString(),
                        stepKey,
                        null,
                        null,
                        null,
                        decision,
                        comment,
                        null));
    }

    @Transactional
    public RunStatusResponse submitFeedback(UUID runId, AsyncToolFeedbackRequest body) {
        return executeService.submitFeedback(runId, body);
    }

    @Transactional
    public RunStatusResponse resume(UUID runId) {
        OrchestratorRun run = runRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown runId: " + runId));
        if (!RunStatus.FAILED.name().equals(run.getStatus())) {
            throw new IllegalArgumentException("Only FAILED runs can be resumed");
        }
        try {
            engine.resumeRun(run);
        } catch (Exception e) {
            throw new IllegalStateException("Resume failed: " + e.getMessage(), e);
        }
        return getRun(runId);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return UUID.fromString(raw.trim());
    }
}
