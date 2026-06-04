package com.aidecision.agentic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "qa_feedback")
public class QaFeedback {

    @Id
    @Column(name = "feedback_id", columnDefinition = "uniqueidentifier")
    private UUID feedbackId;

    @Column(name = "conversation_id", columnDefinition = "uniqueidentifier")
    private UUID conversationId;

    // Orchestrator answers are not qa_message rows; the FK was relaxed in V18 so feedback can
    // reference an orchestrator run (run_id) using the runId as message_id.
    @Column(name = "message_id", columnDefinition = "uniqueidentifier")
    private UUID messageId;

    @Column(name = "run_id", columnDefinition = "uniqueidentifier")
    private UUID runId;

    @Column(name = "rating", nullable = false, length = 8)
    private String rating;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME2")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (feedbackId == null) {
            feedbackId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getFeedbackId() { return feedbackId; }
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Instant getCreatedAt() { return createdAt; }
}
