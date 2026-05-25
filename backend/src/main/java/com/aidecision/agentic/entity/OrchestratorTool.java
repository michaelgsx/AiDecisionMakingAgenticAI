package com.aidecision.agentic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "orchestrator_tool")
public class OrchestratorTool {

    @Id
    @Column(name = "tool_name", length = 64)
    private String toolName;

    @Column(nullable = false, length = 32)
    private String version = "1.0.0";

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "tool_type", nullable = false, length = 64)
    private String toolType;

    @Column(name = "execution_mode", nullable = false, length = 16)
    private String executionMode;

    @Column(name = "request_schema_json", nullable = false, columnDefinition = "nvarchar(max)")
    private String requestSchemaJson;

    @Column(name = "response_schema_json", nullable = false, columnDefinition = "nvarchar(max)")
    private String responseSchemaJson;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getToolType() { return toolType; }
    public void setToolType(String toolType) { this.toolType = toolType; }

    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }

    public String getRequestSchemaJson() { return requestSchemaJson; }
    public void setRequestSchemaJson(String requestSchemaJson) { this.requestSchemaJson = requestSchemaJson; }

    public String getResponseSchemaJson() { return responseSchemaJson; }
    public void setResponseSchemaJson(String responseSchemaJson) { this.responseSchemaJson = responseSchemaJson; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
