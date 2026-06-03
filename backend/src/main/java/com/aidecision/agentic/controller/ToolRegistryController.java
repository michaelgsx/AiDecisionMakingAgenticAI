package com.aidecision.agentic.controller;

import com.aidecision.agentic.dto.RegisterToolRequest;
import com.aidecision.agentic.dto.ToolRegistrationResponse;
import com.aidecision.agentic.tool.ToolRegistryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tool registry for the LLM workflow planner ({@code orchestrator_tool} in Azure SQL ai-rag-db-1).
 * Schemas are stored as JSON Schema; this API accepts structured input/output field lists.
 */
@RestController
@RequestMapping("/agent/tools")
public class ToolRegistryController {

    private final ToolRegistryService registry;

    public ToolRegistryController(ToolRegistryService registry) {
        this.registry = registry;
    }

    /** List enabled tools with structured schemas (used by portal + planner debugging). */
    @GetMapping
    public List<ToolRegistrationResponse> listTools() {
        return registry.listEnabledToolResponses();
    }

    /**
     * Register tool metadata. A matching {@link com.aidecision.agentic.tool.AgentTool} Spring bean
     * must exist at runtime. Returns 400 if the tool name is already registered (use PUT to update).
     */
    @PostMapping
    public ToolRegistrationResponse register(@Valid @RequestBody RegisterToolRequest body) {
        return registry.register(body);
    }

    /** Update an existing tool row (same payload shape as POST). */
    @PutMapping("/{toolName}")
    public ToolRegistrationResponse update(
            @PathVariable String toolName,
            @Valid @RequestBody RegisterToolRequest body) {
        if (!toolName.equals(body.name())) {
            throw new IllegalArgumentException("Path toolName must match body.name");
        }
        return registry.upsert(body);
    }
}
