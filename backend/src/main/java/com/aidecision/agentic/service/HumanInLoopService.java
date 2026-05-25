package com.aidecision.agentic.service;

import com.aidecision.agentic.entity.OrchestratorHumanRequest;
import com.aidecision.agentic.repository.OrchestratorHumanRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class HumanInLoopService {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_ANSWERED = "ANSWERED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private final OrchestratorHumanRequestRepository repo;

    public HumanInLoopService(OrchestratorHumanRequestRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public OrchestratorHumanRequest createRequest(
            UUID runId, UUID stepId, String stepKey, String prompt, String proposal) {
        OrchestratorHumanRequest req = new OrchestratorHumanRequest();
        req.setRequestId(UUID.randomUUID());
        req.setRunId(runId);
        req.setStepId(stepId);
        req.setStepKey(stepKey);
        req.setPrompt(prompt);
        req.setProposal(proposal);
        req.setStatus(STATUS_WAITING);
        req.setCreatedAt(Instant.now());
        return repo.save(req);
    }

    @Transactional(readOnly = true)
    public List<OrchestratorHumanRequest> pendingForRun(UUID runId) {
        return repo.findByRunIdAndStatusOrderByCreatedAtAsc(runId, STATUS_WAITING);
    }

    @Transactional
    public OrchestratorHumanRequest answer(UUID requestId, String decision, String comment) {
        OrchestratorHumanRequest req = repo.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown human request: " + requestId));
        if (!STATUS_WAITING.equals(req.getStatus())) {
            throw new IllegalArgumentException("Request already answered");
        }
        String d = decision == null ? "" : decision.trim().toLowerCase();
        if (!"accept".equals(d) && !"reject".equals(d)) {
            throw new IllegalArgumentException("decision must be accept or reject");
        }
        req.setDecision(d);
        req.setComment(comment == null || comment.isBlank() ? null : comment.trim());
        req.setStatus(STATUS_ANSWERED);
        req.setAnsweredAt(Instant.now());
        return repo.save(req);
    }

    @Transactional(readOnly = true)
    public OrchestratorHumanRequest findByStepId(UUID stepId) {
        return repo.findByStepId(stepId).orElse(null);
    }

    @Transactional(readOnly = true)
    public OrchestratorHumanRequest getRequest(UUID requestId) {
        return repo.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown human request: " + requestId));
    }

    @Transactional(readOnly = true)
    public OrchestratorHumanRequest findByRunAndStepKey(UUID runId, String stepKey) {
        return repo.findByRunIdAndStepKey(runId, stepKey).orElse(null);
    }

    @Transactional
    public void cancel(UUID requestId, String reason) {
        OrchestratorHumanRequest req = getRequest(requestId);
        if (!STATUS_WAITING.equals(req.getStatus())) {
            throw new IllegalArgumentException("Only WAITING human requests can be cancelled");
        }
        req.setStatus(STATUS_EXPIRED);
        req.setComment(reason == null || reason.isBlank() ? "cancelled via API" : reason.trim());
        req.setAnsweredAt(Instant.now());
        repo.save(req);
    }
}
