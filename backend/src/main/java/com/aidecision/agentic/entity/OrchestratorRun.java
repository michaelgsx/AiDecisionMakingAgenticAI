package com.aidecision.agentic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orchestrator_run")
public class OrchestratorRun {

    @Id
    @Column(name = "run_id", columnDefinition = "uniqueidentifier")
    private UUID runId;

    @Column(name = "conversation_id", columnDefinition = "uniqueidentifier")
    private UUID conversationId;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(nullable = false, columnDefinition = "nvarchar(max)")
    private String question;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "workflow_json", columnDefinition = "nvarchar(max)")
    private String workflowJson;

    @Column(name = "answer_text", columnDefinition = "nvarchar(max)")
    private String answerText;

    @Column(name = "error_message", columnDefinition = "nvarchar(max)")
    private String errorMessage;

    @Column(name = "checkpoint_json", columnDefinition = "nvarchar(max)")
    private String checkpointJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (runId == null) {
            runId = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getWorkflowJson() { return workflowJson; }
    public void setWorkflowJson(String workflowJson) { this.workflowJson = workflowJson; }

    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getCheckpointJson() { return checkpointJson; }
    public void setCheckpointJson(String checkpointJson) { this.checkpointJson = checkpointJson; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
