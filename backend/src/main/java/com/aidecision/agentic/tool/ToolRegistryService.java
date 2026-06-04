package com.aidecision.agentic.tool;

import com.aidecision.agentic.dto.RegisterToolRequest;
import com.aidecision.agentic.dto.ToolRegistrationResponse;
import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.repository.OrchestratorToolRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ToolRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistryService.class);

    private final OrchestratorToolRepository toolRepo;
    private final List<AgentTool> agentTools;
    private final ToolSchemaConverter schemaConverter;

    private Map<String, AgentTool> executorsByName = Map.of();

    public ToolRegistryService(
            OrchestratorToolRepository toolRepo,
            List<AgentTool> agentTools,
            ToolSchemaConverter schemaConverter) {
        this.toolRepo = toolRepo;
        this.agentTools = agentTools;
        this.schemaConverter = schemaConverter;
    }

    @PostConstruct
    void initExecutors() {
        executorsByName = agentTools.stream()
                .collect(Collectors.toMap(AgentTool::name, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        log.info("Loaded {} runtime tool executor(s): {}", executorsByName.size(), executorsByName.keySet());
    }

    /**
     * At startup: insert missing built-in tools; always refresh description and schemas from
     * {@link BuiltinToolCatalog} so every parameter has descriptions for the workflow planner.
     */
    @Transactional
    public void registerBuiltinToolsOnStartup() {
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        for (BuiltinToolCatalog.Definition def : BuiltinToolCatalog.all()) {
            if (!executorsByName.containsKey(def.name())) {
                log.warn("No runtime executor for tool {}, skipping DB registration", def.name());
                skipped++;
                continue;
            }
            OrchestratorTool existing = toolRepo.findById(def.name()).orElse(null);
            if (existing != null) {
                syncCatalogMetadata(def, existing);
                updated++;
            } else if (insertTool(def)) {
                inserted++;
            }
        }
        log.info("Orchestrator tool registry: {} inserted, {} schemas synced, {} skipped (no executor)",
                inserted, updated, skipped);
    }

    private boolean insertTool(BuiltinToolCatalog.Definition def) {
        OrchestratorTool row = toEntity(def);
        toolRepo.save(row);
        log.info("Registered orchestrator tool: {} ({}, {})", def.name(), def.toolType(), def.executionMode());
        return true;
    }

    private void syncCatalogMetadata(BuiltinToolCatalog.Definition def, OrchestratorTool row) {
        row.setVersion(def.version());
        row.setMaxRetry(def.maxRetry());
        row.setDescription(def.description());
        row.setToolType(def.toolType());
        row.setExecutionMode(def.executionMode());
        row.setRequestSchemaJson(def.requestSchemaJson());
        row.setResponseSchemaJson(def.responseSchemaJson());
        row.setEndpointUrl(def.endpointUrl());
        row.setEnabled(true);
        toolRepo.save(row);
        log.debug("Synced tool schemas/descriptions: {}", def.name());
    }

    private static OrchestratorTool toEntity(BuiltinToolCatalog.Definition def) {
        OrchestratorTool row = new OrchestratorTool();
        row.setToolName(def.name());
        row.setVersion(def.version());
        row.setMaxRetry(def.maxRetry());
        row.setDescription(def.description());
        row.setToolType(def.toolType());
        row.setExecutionMode(def.executionMode());
        row.setRequestSchemaJson(def.requestSchemaJson());
        row.setResponseSchemaJson(def.responseSchemaJson());
        row.setEndpointUrl(def.endpointUrl());
        row.setEnabled(true);
        return row;
    }

    public List<OrchestratorTool> listEnabledTools() {
        return toolRepo.findByEnabledTrueOrderByToolNameAsc();
    }

    public List<ToolRegistrationResponse> listEnabledToolResponses() {
        return listEnabledTools().stream().map(this::toResponse).toList();
    }

    public Map<String, OrchestratorTool> enabledToolsByName() {
        return listEnabledTools().stream()
                .collect(Collectors.toMap(OrchestratorTool::getToolName, Function.identity()));
    }

    public OrchestratorTool requireRegistered(String toolName, String version) {
        OrchestratorTool row = enabledToolsByName().get(toolName);
        if (row == null) {
            throw new IllegalArgumentException("Unknown or disabled tool: " + toolName);
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Tool version is required");
        }
        if (!row.getVersion().equals(version.trim())) {
            throw new IllegalArgumentException(
                    "Tool version mismatch for " + toolName + ": expected " + row.getVersion() + ", got " + version);
        }
        return row;
    }

    public AgentTool requireExecutor(String toolName) {
        AgentTool tool = executorsByName.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("No executor registered for tool: " + toolName);
        }
        return tool;
    }

    public boolean hasExecutor(String toolName) {
        return executorsByName.containsKey(toolName);
    }

    @Transactional
    public ToolRegistrationResponse register(RegisterToolRequest request) {
        return registerOrUpdate(request, false);
    }

    @Transactional
    public ToolRegistrationResponse upsert(RegisterToolRequest request) {
        return registerOrUpdate(request, true);
    }

    private ToolRegistrationResponse registerOrUpdate(RegisterToolRequest request, boolean allowUpdate) {
        if (!executorsByName.containsKey(request.name())) {
            throw new IllegalArgumentException("Cannot register tool without runtime executor: " + request.name());
        }
        OrchestratorTool existing = toolRepo.findById(request.name()).orElse(null);
        if (existing != null && !allowUpdate) {
            throw new IllegalArgumentException("Tool already exists: " + request.name());
        }

        OrchestratorTool row = existing != null ? existing : new OrchestratorTool();
        row.setToolName(request.name());
        row.setVersion(request.version().trim());
        row.setMaxRetry(request.maxRetry());
        row.setDescription(request.description().trim());
        row.setToolType(request.toolType());
        row.setExecutionMode(request.executionMode());
        row.setRequestSchemaJson(schemaConverter.toJsonSchema(request.inputSchema()));
        row.setResponseSchemaJson(schemaConverter.toJsonSchema(request.outputSchema()));
        row.setEndpointUrl(
                request.endpointUrl() != null && !request.endpointUrl().isBlank()
                        ? request.endpointUrl().trim()
                        : BuiltinToolCatalog.executeUrl(request.name(), request.version().trim()));
        row.setEnabled(request.enabled());

        OrchestratorTool saved = toolRepo.save(row);
        log.info("{} orchestrator tool: {} v{} (maxRetry={})",
                existing == null ? "Registered" : "Updated",
                saved.getToolName(), saved.getVersion(), saved.getMaxRetry());
        return toResponse(saved);
    }

    public ToolRegistrationResponse toResponse(OrchestratorTool tool) {
        return new ToolRegistrationResponse(
                tool.getToolName(),
                tool.getVersion(),
                tool.getMaxRetry(),
                tool.getDescription(),
                tool.getToolType(),
                tool.getExecutionMode(),
                schemaConverter.fromJsonSchema(tool.getRequestSchemaJson()),
                schemaConverter.fromJsonSchema(tool.getResponseSchemaJson()),
                tool.getEndpointUrl(),
                tool.isEnabled(),
                hasExecutor(tool.getToolName()),
                tool.getCreatedAt(),
                tool.getUpdatedAt());
    }

    /** @deprecated use {@link #register(RegisterToolRequest)} */
    @Deprecated
    @Transactional
    public OrchestratorTool register(OrchestratorTool tool) {
        if (!executorsByName.containsKey(tool.getToolName())) {
            throw new IllegalArgumentException("Cannot register tool without runtime executor: " + tool.getToolName());
        }
        if (toolRepo.existsById(tool.getToolName())) {
            throw new IllegalArgumentException("Tool already exists: " + tool.getToolName());
        }
        return toolRepo.save(tool);
    }
}
