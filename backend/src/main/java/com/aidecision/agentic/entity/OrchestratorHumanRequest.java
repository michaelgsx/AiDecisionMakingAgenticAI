package com.aidecision.agentic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orchestrator_human_request")
public class OrchestratorHumanRequest {

    @Id
    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "step_id", nullable = false)
    private UUID stepId;

    @Column(name = "step_key", length = 64, nullable = false)
    private String stepKey;

    @Column(name = "prompt", length = 1000, nullable = false)
    private String prompt;

    @Column(name = "proposal", columnDefinition = "NVARCHAR(MAX)", nullable = false)
    private String proposal;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "decision", length = 16)
    private String decision;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    public UUID getRequestId() { return requestId; }
    public void setRequestId(UUID requestId) { this.requestId = requestId; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public UUID getStepId() { return stepId; }
    public void setStepId(UUID stepId) { this.stepId = stepId; }

    public String getStepKey() { return stepKey; }
    public void setStepKey(String stepKey) { this.stepKey = stepKey; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getProposal() { return proposal; }
    public void setProposal(String proposal) { this.proposal = proposal; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant answeredAt) { this.answeredAt = answeredAt; }
}
