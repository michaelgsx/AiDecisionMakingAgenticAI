package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.AzureOpenAiProperties;
import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.tool.ToolRegistryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowPlannerService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowPlannerService.class);

    private final AzureOpenAiProperties openAi;
    private final OrchestratorProperties orchProps;
    private final ToolRegistryService toolRegistry;
    private final WorkflowDagValidator validator;
    private final ObjectMapper mapper;
    private final RestClient http;

    public WorkflowPlannerService(
            AzureOpenAiProperties openAi,
            OrchestratorProperties orchProps,
            ToolRegistryService toolRegistry,
            WorkflowDagValidator validator,
            ObjectMapper mapper,
            RestClient http) {
        this.openAi = openAi;
        this.orchProps = orchProps;
        this.toolRegistry = toolRegistry;
        this.validator = validator;
        this.mapper = mapper;
        this.http = http;
    }

    public WorkflowDag plan(String question) throws Exception {
        Map<String, OrchestratorTool> tools = toolRegistry.enabledToolsByName();
        if (tools.isEmpty()) {
            log.warn("No enabled tools in orchestrator_tool; using default DAG only");
        }
        if (openAi.chatConfigured() && !tools.isEmpty()) {
            try {
                WorkflowDag dag = parseDag(callPlannerLlm(question, tools));
                validator.validate(dag, tools);
                return dag;
            } catch (Exception e) {
                log.warn("LLM workflow planning failed, using default DAG: {}", e.getMessage());
            }
        }
        WorkflowDag fallback = defaultDag(question);
        validator.validate(fallback, tools);
        return fallback;
    }

    private WorkflowDag defaultDag(String question) {
        return new WorkflowDag(List.of(
                new WorkflowDag.WorkflowStepDef("s1", "data_acquisition", List.of(), Map.of("scenario", "qa"),
                        (int) orchProps.getDefaultStepMaxTimeMs(), (int) orchProps.getDefaultStepTimeoutMs()),
                new WorkflowDag.WorkflowStepDef("s2", "ai_decision_rag", List.of("s1"), Map.of("text", question),
                        (int) orchProps.getDefaultStepMaxTimeMs(), (int) orchProps.getDefaultStepTimeoutMs()),
                new WorkflowDag.WorkflowStepDef("s3", "llm_answer", List.of("s1", "s2"), Map.of(),
                        (int) orchProps.getDefaultStepMaxTimeMs(), (int) orchProps.getDefaultStepTimeoutMs())
        ));
    }

    private String callPlannerLlm(String question, Map<String, OrchestratorTool> tools) throws Exception {
        String base = openAi.getEndpoint().replaceAll("/+$", "");
        URI uri = URI.create(base + "/openai/deployments/" + openAi.getChatDeployment()
                + "/chat/completions?api-version=" + openAi.getEffectiveChatApiVersion());

        String toolRegistryJson = buildToolRegistryJson(tools);
        String system = buildSystemPrompt();
        String user = buildUserPrompt(question, toolRegistryJson);

        ObjectNode root = mapper.createObjectNode();
        root.put("temperature", 0.1);
        root.put("max_tokens", 4096);
        root.set("response_format", mapper.createObjectNode().put("type", "json_object"));
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);

        String body = http.post()
                .uri(uri)
                .header("api-key", openAi.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(root.toString())
                .retrieve()
                .body(String.class);

        JsonNode json = mapper.readTree(body == null ? "{}" : body);
        return json.path("choices").path(0).path("message").path("content").asText("");
    }

    /** Full orchestrator_tool rows as JSON for the planner (sorted by tool_name). */
    String buildToolRegistryJson(Map<String, OrchestratorTool> tools) throws Exception {
        ArrayNode registry = mapper.createArrayNode();
        tools.values().stream()
                .sorted(Comparator.comparing(OrchestratorTool::getToolName))
                .forEach(t -> {
                    ObjectNode row = registry.addObject();
                    row.put("toolName", t.getToolName());
                    row.put("version", t.getVersion());
                    row.put("toolType", t.getToolType());
                    row.put("executionMode", t.getExecutionMode());
                    row.put("description", t.getDescription());
                    row.set("requestSchema", parseSchemaNode(t.getRequestSchemaJson()));
                    row.set("responseSchema", parseSchemaNode(t.getResponseSchemaJson()));
                });
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(registry);
    }

    private JsonNode parseSchemaNode(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(schemaJson);
        } catch (Exception e) {
            return mapper.createObjectNode().put("raw", schemaJson);
        }
    }

    private String buildSystemPrompt() {
        int maxSteps = orchProps.getMaxStepsPerWorkflow();
        long defaultMax = orchProps.getDefaultStepMaxTimeMs();
        long defaultTimeout = orchProps.getDefaultStepTimeoutMs();
        return """
                You are the workflow planner for a risk-control agent orchestrator.

                Your task: given a user question and the TOOL REGISTRY (orchestrator_tool), \
                CREATE a complete execution workflow — a directed acyclic graph (DAG) of tool steps \
                that answers the question. Output ONLY one JSON object (no markdown, no commentary).

                Required output shape:
                {
                  "steps": [
                    {
                      "id": "s1",
                      "tool": "<toolName from registry>",
                      "dependsOn": [],
                      "params": { },
                      "maxTimeMs": %d,
                      "timeoutMs": %d
                    }
                  ]
                }

                Planning rules:
                1. Use ONLY toolName values present in toolRegistry (enabled tools).
                2. Every step.id must be unique (e.g. s1, s2, s3).
                3. dependsOn lists step ids that must finish before this step; no cycles.
                4. params must match that tool's requestSchema; read each property's description to choose values and branching.
                5. Use responseSchema property descriptions (e.g. aiLabel, accepted, rowCount) to decide later steps / conditional paths.
                6. At most %d steps. The LAST step should be llm_answer when possible.
                7. Similar-case / policy questions: include ai_decision_rag (or similarity_retrieval).
                8. Counts, aggregates, SQL analytics: include natural_language_to_sql before llm_answer.
                9. When a human must approve a recommendation before the final answer: insert human_in_the_loop \
                   (ASYNC) before llm_answer; downstream steps depend on it.
                10. ASYNC tools stay RUNNING until the user responds via API — give them longer timeoutMs if needed.
                11. data_acquisition is optional context at the start when features/scenario help later tools.
                """
                .formatted(defaultMax, defaultTimeout, maxSteps);
    }

    private String buildUserPrompt(String question, String toolRegistryJson) {
        return """
                Create the workflow DAG for the following user question.

                userQuestion:
                %s

                toolRegistry (from orchestrator_tool — use only these tools):
                %s
                """
                .formatted(question.trim(), toolRegistryJson);
    }

    private WorkflowDag parseDag(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode stepsNode = root.path("steps");
        if (!stepsNode.isArray()) {
            throw new IllegalArgumentException("Planner JSON missing steps array");
        }
        List<WorkflowDag.WorkflowStepDef> steps = new java.util.ArrayList<>();
        for (JsonNode n : stepsNode) {
            List<String> deps = new java.util.ArrayList<>();
            for (JsonNode d : n.path("dependsOn")) {
                deps.add(d.asText());
            }
            Map<String, Object> params = mapper.convertValue(n.path("params"),
                    mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            steps.add(new WorkflowDag.WorkflowStepDef(
                    n.path("id").asText(),
                    n.path("tool").asText(),
                    deps,
                    params == null ? Map.of() : params,
                    n.has("maxTimeMs") ? n.path("maxTimeMs").asInt() : null,
                    n.has("timeoutMs") ? n.path("timeoutMs").asInt() : null
            ));
        }
        return new WorkflowDag(steps);
    }
}
