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
@Table(name = "orchestrator_step")
public class OrchestratorStep {

    @Id
    @Column(name = "step_id", columnDefinition = "uniqueidentifier")
    private UUID stepId;

    @Column(name = "run_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID runId;

    @Column(name = "step_key", nullable = false, length = 64)
    private String stepKey;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "depends_on_json", columnDefinition = "nvarchar(max)")
    private String dependsOnJson;

    @Column(name = "input_json", columnDefinition = "nvarchar(max)")
    private String inputJson;

    @Column(name = "output_json", columnDefinition = "nvarchar(max)")
    private String outputJson;

    @Column(name = "max_time_ms", nullable = false)
    private int maxTimeMs = 30_000;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs = 120_000;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "started_at", columnDefinition = "DATETIME2")
    private Instant startedAt;

    @Column(name = "finished_at", columnDefinition = "DATETIME2")
    private Instant finishedAt;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME2")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME2")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (stepId == null) {
            stepId = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getStepId() { return stepId; }
    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public String getStepKey() { return stepKey; }
    public void setStepKey(String stepKey) { this.stepKey = stepKey; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDependsOnJson() { return dependsOnJson; }
    public void setDependsOnJson(String dependsOnJson) { this.dependsOnJson = dependsOnJson; }

    public String getInputJson() { return inputJson; }
    public void setInputJson(String inputJson) { this.inputJson = inputJson; }

    public String getOutputJson() { return outputJson; }
    public void setOutputJson(String outputJson) { this.outputJson = outputJson; }

    public int getMaxTimeMs() { return maxTimeMs; }
    public void setMaxTimeMs(int maxTimeMs) { this.maxTimeMs = maxTimeMs; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
