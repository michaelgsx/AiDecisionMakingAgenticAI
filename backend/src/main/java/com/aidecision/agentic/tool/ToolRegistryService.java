package com.aidecision.agentic.tool;

import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.repository.OrchestratorToolRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper mapper;

    private Map<String, AgentTool> executorsByName = Map.of();

    public ToolRegistryService(
            OrchestratorToolRepository toolRepo,
            List<AgentTool> agentTools,
            ObjectMapper mapper) {
        this.toolRepo = toolRepo;
        this.agentTools = agentTools;
        this.mapper = mapper;
    }

    @PostConstruct
    void initExecutors() {
        executorsByName = agentTools.stream()
                .collect(Collectors.toMap(AgentTool::name, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    @Transactional
    public void seedDefaultsIfEmpty() {
        if (toolRepo.count() > 0) {
            ensureBuiltinTools();
            return;
        }
        log.info("Seeding orchestrator tool registry");
        seedAllBuiltinTools();
    }

    /** Upsert built-in tools so new deploys pick up ai_decision_rag, NL2SQL, human_in_the_loop. */
    @Transactional
    public void ensureBuiltinTools() {
        seedAllBuiltinTools();
    }

    private void seedAllBuiltinTools() {
        upsertTool("data_acquisition", "1.0.0", "Fetch risk context / features",
                "DATA_ACQUISITION", "SYNC",
                "{\"type\":\"object\",\"properties\":{\"scenario\":{\"type\":\"string\"}}}",
                "{\"type\":\"object\",\"properties\":{\"features\":{\"type\":\"object\"}}}");
        upsertTool("similarity_retrieval", "1.0.0",
                "Legacy alias — delegates to ai_decision_rag (/rag/assess).",
                "SIMILARITY_RETRIEVAL", "SYNC",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}",
                "{\"type\":\"object\",\"properties\":{\"hits\":{\"type\":\"array\"},\"summary\":{\"type\":\"string\"}}}");
        upsertTool("ai_decision_rag", "1.0.0",
                "AiDecisionMakingBackend hybrid RAG assess for similar cases.",
                "SIMILARITY_RETRIEVAL", "SYNC",
                "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"},\"metadata\":{\"type\":\"string\"}}}",
                "{\"type\":\"object\",\"properties\":{\"hits\":{\"type\":\"array\"},\"aiLabel\":{\"type\":\"string\"},\"summary\":{\"type\":\"string\"}}}");
        upsertTool("natural_language_to_sql", "1.0.0",
                "NL question → read-only SQL using schema_catalog descriptions.",
                "AGGREGATE", "SYNC",
                "{\"type\":\"object\",\"properties\":{\"question\":{\"type\":\"string\"},\"maxRows\":{\"type\":\"integer\"}}}",
                "{\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\"},\"rows\":{\"type\":\"array\"},\"rowCount\":{\"type\":\"integer\"}}}");
        upsertTool("human_in_the_loop", "1.0.0",
                "Async user approval: is the proposed solution acceptable?",
                "VALIDATION", "ASYNC",
                "{\"type\":\"object\",\"properties\":{\"proposal\":{\"type\":\"string\"},\"prompt\":{\"type\":\"string\"}}}",
                "{\"type\":\"object\",\"properties\":{\"decision\":{\"type\":\"string\"},\"accepted\":{\"type\":\"boolean\"}}}");
        upsertTool("llm_answer", "1.0.0", "Synthesize final answer from prior steps",
                "LLM_REASONING", "SYNC",
                "{\"type\":\"object\",\"properties\":{}}",
                "{\"type\":\"object\",\"properties\":{\"answer\":{\"type\":\"string\"}}}");
    }

    private void upsertTool(
            String name, String version, String description, String type, String mode,
            String requestSchema, String responseSchema) {
        OrchestratorTool t = toolRepo.findById(name).orElseGet(OrchestratorTool::new);
        t.setToolName(name);
        t.setVersion(version);
        t.setDescription(description);
        t.setToolType(type);
        t.setExecutionMode(mode);
        t.setRequestSchemaJson(requestSchema);
        t.setResponseSchemaJson(responseSchema);
        t.setEnabled(true);
        toolRepo.save(t);
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
        return toolRepo.save(tool);
    }
}
