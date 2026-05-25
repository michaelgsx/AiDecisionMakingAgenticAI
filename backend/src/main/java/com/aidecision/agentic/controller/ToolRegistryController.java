package com.aidecision.agentic.controller;

import com.aidecision.agentic.dto.ToolPortalDto;
import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.tool.ToolRegistryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/agent/tools")
public class ToolRegistryController {

    private final ToolRegistryService registry;

    public ToolRegistryController(ToolRegistryService registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<ToolPortalDto> listTools() {
        registry.seedDefaultsIfEmpty();
        return registry.listEnabledTools().stream().map(this::toDto).toList();
    }

    @PostMapping
    public ToolPortalDto register(@Valid @RequestBody ToolPortalDto body) {
        OrchestratorTool t = new OrchestratorTool();
        t.setToolName(body.name());
        t.setVersion(body.version() == null ? "1.0.0" : body.version());
        t.setDescription(body.description());
        t.setToolType(body.toolType());
        t.setExecutionMode(body.executionMode());
        t.setRequestSchemaJson(body.requestSchemaJson());
        t.setResponseSchemaJson(body.responseSchemaJson());
        t.setEnabled(body.enabled());
        return toDto(registry.register(t));
    }

    private ToolPortalDto toDto(OrchestratorTool t) {
        return new ToolPortalDto(
                t.getToolName(),
                t.getVersion(),
                t.getDescription(),
                t.getToolType(),
                t.getExecutionMode(),
                t.getRequestSchemaJson(),
                t.getResponseSchemaJson(),
                t.isEnabled());
    }
}
