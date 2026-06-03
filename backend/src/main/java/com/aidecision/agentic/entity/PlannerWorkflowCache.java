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
@Table(name = "planner_workflow_cache")
public class PlannerWorkflowCache {

    @Id
    @Column(name = "cache_id", columnDefinition = "uniqueidentifier")
    private UUID cacheId;

    @Column(nullable = false, columnDefinition = "nvarchar(max)")
    private String question;

    @Column(name = "question_hash", nullable = false, length = 64)
    private String questionHash;

    @Column(name = "planner_prompt", nullable = false, columnDefinition = "nvarchar(max)")
    private String plannerPrompt;

    @Column(name = "workflow_json", nullable = false, columnDefinition = "nvarchar(max)")
    private String workflowJson;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME2")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME2")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (cacheId == null) {
            cacheId = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getCacheId() { return cacheId; }
    public void setCacheId(UUID cacheId) { this.cacheId = cacheId; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getQuestionHash() { return questionHash; }
    public void setQuestionHash(String questionHash) { this.questionHash = questionHash; }

    public String getPlannerPrompt() { return plannerPrompt; }
    public void setPlannerPrompt(String plannerPrompt) { this.plannerPrompt = plannerPrompt; }

    public String getWorkflowJson() { return workflowJson; }
    public void setWorkflowJson(String workflowJson) { this.workflowJson = workflowJson; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
