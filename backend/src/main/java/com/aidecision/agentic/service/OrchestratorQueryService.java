package com.aidecision.agentic.service;

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

import java.util.List;
import java.util.UUID;

@Service
public class OrchestratorQueryService {

    private final OrchestratorRunRepository runRepo;
    private final OrchestratorStepRepository stepRepo;
    private final OrchestratorEngine engine;
    private final HumanInLoopService humanInLoop;
    private final WorkflowDiagramService workflowDiagrams;

    public OrchestratorQueryService(
            OrchestratorRunRepository runRepo,
            OrchestratorStepRepository stepRepo,
            OrchestratorEngine engine,
            HumanInLoopService humanInLoop,
            WorkflowDiagramService workflowDiagrams) {
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.engine = engine;
        this.humanInLoop = humanInLoop;
        this.workflowDiagrams = workflowDiagrams;
    }

    @Transactional(readOnly = true)
    public RunStatusResponse getRun(UUID runId) {
        OrchestratorRun run = runRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown runId: " + runId));
        List<OrchestratorStep> steps = stepRepo.findByRunIdOrderByCreatedAtAsc(runId);

        List<RunStatusResponse.StepStatusDto> stepDtos = steps.stream()
                .map(s -> new RunStatusResponse.StepStatusDto(
                        s.getStepId().toString(),
                        s.getStepKey(),
                        s.getToolName(),
                        s.getStatus(),
                        s.getErrorMessage(),
                        s.getAttemptCount()))
                .toList();

        List<OrchestratorHumanRequest> pending = humanInLoop.pendingForRun(runId);
        List<RunStatusResponse.HumanApprovalDto> approvals = pending.stream()
                .map(r -> new RunStatusResponse.HumanApprovalDto(
                        r.getRequestId().toString(),
                        r.getStepKey(),
                        r.getPrompt(),
                        r.getProposal()))
                .toList();

        String workflowJson = run.getWorkflowJson();
        String mermaid = null;
        if (workflowJson != null && !workflowJson.isBlank()) {
            RunStatusResponse forDiagram = new RunStatusResponse(
                    run.getRunId().toString(),
                    run.getStatus(),
                    run.getQuestion(),
                    run.getAnswerText(),
                    run.getErrorMessage(),
                    workflowJson,
                    null,
                    stepDtos,
                    !approvals.isEmpty(),
                    approvals);
            mermaid = workflowDiagrams.mermaidForRunStatus(forDiagram);
        }
        return new RunStatusResponse(
                run.getRunId().toString(),
                run.getStatus(),
                run.getQuestion(),
                run.getAnswerText(),
                run.getErrorMessage(),
                workflowJson,
                mermaid,
                stepDtos,
                !approvals.isEmpty(),
                approvals);
    }

    @Transactional
    public RunStatusResponse submitHumanResponse(UUID runId, UUID requestId, String decision, String comment) {
        OrchestratorHumanRequest req = humanInLoop.getRequest(requestId);
        if (!req.getRunId().equals(runId)) {
            throw new IllegalArgumentException("requestId does not belong to runId");
        }
        humanInLoop.answer(requestId, decision, comment);
        try {
            engine.processRun(runId);
        } catch (Exception e) {
            throw new IllegalStateException("Could not resume run after human response: " + e.getMessage(), e);
        }
        return getRun(runId);
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
}
