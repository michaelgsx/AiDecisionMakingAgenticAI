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
@Table(name = "async_chat_status")
public class AsyncChatStatus {

    @Id
    @Column(name = "request_id", columnDefinition = "uniqueidentifier")
    private UUID requestId;

    @Column(name = "conversation_id", columnDefinition = "uniqueidentifier")
    private UUID conversationId;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(nullable = false, columnDefinition = "nvarchar(max)")
    private String question;

    @Column(columnDefinition = "nvarchar(max)")
    private String answer;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "status_detail", nullable = false, length = 256)
    private String statusDetail;

    @Column(name = "run_id", columnDefinition = "uniqueidentifier")
    private UUID runId;

    @Column(name = "error_message", columnDefinition = "nvarchar(max)")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME2")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME2")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (requestId == null) {
            requestId = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getRequestId() { return requestId; }
    public void setRequestId(UUID requestId) { this.requestId = requestId; }

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusDetail() { return statusDetail; }
    public void setStatusDetail(String statusDetail) { this.statusDetail = statusDetail; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
