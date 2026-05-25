package com.aidecision.agentic.tool;

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

    private Map<String, AgentTool> executorsByName = Map.of();

    public ToolRegistryService(OrchestratorToolRepository toolRepo, List<AgentTool> agentTools) {
        this.toolRepo = toolRepo;
        this.agentTools = agentTools;
    }

    @PostConstruct
    void initExecutors() {
        executorsByName = agentTools.stream()
                .collect(Collectors.toMap(AgentTool::name, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        log.info("Loaded {} runtime tool executor(s): {}", executorsByName.size(), executorsByName.keySet());
    }

    /**
     * Called once at startup. Inserts rows into {@code orchestrator_tool} for each built-in tool
     * that has a runtime executor; skips tools already present in the table.
     */
    @Transactional
    public void registerBuiltinToolsOnStartup() {
        int inserted = 0;
        int skipped = 0;
        for (BuiltinToolCatalog.Definition def : BuiltinToolCatalog.all()) {
            if (insertToolIfAbsent(def)) {
                inserted++;
            } else {
                skipped++;
            }
        }
        log.info("Orchestrator tool registry: {} inserted, {} already existed", inserted, skipped);
    }

    private boolean insertToolIfAbsent(BuiltinToolCatalog.Definition def) {
        if (toolRepo.existsById(def.name())) {
            log.debug("Tool already registered, skipping: {}", def.name());
            return false;
        }
        if (!executorsByName.containsKey(def.name())) {
            log.warn("No runtime executor for tool {}, skipping DB registration", def.name());
            return false;
        }

        OrchestratorTool row = new OrchestratorTool();
        row.setToolName(def.name());
        row.setVersion(def.version());
        row.setDescription(def.description());
        row.setToolType(def.toolType());
        row.setExecutionMode(def.executionMode());
        row.setRequestSchemaJson(def.requestSchemaJson());
        row.setResponseSchemaJson(def.responseSchemaJson());
        row.setEnabled(true);
        toolRepo.save(row);
        log.info("Registered orchestrator tool: {} ({}, {})", def.name(), def.toolType(), def.executionMode());
        return true;
    }

    public List<OrchestratorTool> listEnabledTools() {
        return toolRepo.findByEnabledTrueOrderByToolNameAsc();
    }

    public Map<String, OrchestratorTool> enabledToolsByName() {
        return listEnabledTools().stream()
                .collect(Collectors.toMap(OrchestratorTool::getToolName, Function.identity()));
    }

    public AgentTool requireExecutor(String toolName) {
        AgentTool tool = executorsByName.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("No executor registered for tool: " + toolName);
        }
        return tool;
    }

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
