package com.aidecision.agentic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "qa_evaluation")
public class QaEvaluation {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @Column(name = "evaluation_id", columnDefinition = "uniqueidentifier")
    private UUID evaluationId;

    @Column(name = "run_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID runId;

    @Column(nullable = false, columnDefinition = "nvarchar(max)")
    private String question;

    @Column(name = "answer_text", nullable = false, columnDefinition = "nvarchar(max)")
    private String answerText;

    @Column(name = "review_status", nullable = false, length = 16)
    private String reviewStatus;

    @Column(name = "reviewer_id", length = 128)
    private String reviewerId;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @PrePersist
    void onCreate() {
        if (evaluationId == null) {
            evaluationId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getEvaluationId() { return evaluationId; }
    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
