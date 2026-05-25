package com.aidecision.agentic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.orchestrator")
public class OrchestratorProperties {

    private int maxStepsPerWorkflow = 20;
    private int maxPlanningRetries = 2;
    private long workerPollIntervalMs = 2000;
    private long defaultStepMaxTimeMs = 30_000;
    private long defaultStepTimeoutMs = 120_000;
    private long maxRunAgeMs = 3_600_000;

    public int getMaxStepsPerWorkflow() { return maxStepsPerWorkflow; }
    public void setMaxStepsPerWorkflow(int maxStepsPerWorkflow) { this.maxStepsPerWorkflow = maxStepsPerWorkflow; }

    public int getMaxPlanningRetries() { return maxPlanningRetries; }
    public void setMaxPlanningRetries(int maxPlanningRetries) { this.maxPlanningRetries = maxPlanningRetries; }

    public long getWorkerPollIntervalMs() { return workerPollIntervalMs; }
    public void setWorkerPollIntervalMs(long workerPollIntervalMs) { this.workerPollIntervalMs = workerPollIntervalMs; }

    public long getDefaultStepMaxTimeMs() { return defaultStepMaxTimeMs; }
    public void setDefaultStepMaxTimeMs(long defaultStepMaxTimeMs) { this.defaultStepMaxTimeMs = defaultStepMaxTimeMs; }

    public long getDefaultStepTimeoutMs() { return defaultStepTimeoutMs; }
    public void setDefaultStepTimeoutMs(long defaultStepTimeoutMs) { this.defaultStepTimeoutMs = defaultStepTimeoutMs; }

    public long getMaxRunAgeMs() { return maxRunAgeMs; }
    public void setMaxRunAgeMs(long maxRunAgeMs) { this.maxRunAgeMs = maxRunAgeMs; }
}
