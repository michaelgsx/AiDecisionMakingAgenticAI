package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.AzureOpenAiProperties;
import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.tool.ToolRegistryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowPlannerService {

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
        if (openAi.chatConfigured()) {
            try {
                WorkflowDag dag = parseDag(callPlannerLlm(question, tools));
                validator.validate(dag, tools);
                return dag;
            } catch (Exception e) {
                // fall through to default DAG
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

        StringBuilder catalog = new StringBuilder();
        for (OrchestratorTool t : tools.values()) {
            catalog.append("- ").append(t.getToolName()).append(" v").append(t.getVersion())
                    .append(" [").append(t.getToolType()).append(", ").append(t.getExecutionMode()).append("]: ")
                    .append(t.getDescription()).append("\n  requestSchema: ").append(t.getRequestSchemaJson())
                    .append("\n  responseSchema: ").append(t.getResponseSchemaJson()).append("\n");
        }

        String system = """
                You are a workflow planner for a risk-control agent. Output ONLY valid JSON (no markdown):
                {"steps":[{"id":"s1","tool":"<tool_name>","dependsOn":[],"params":{},"maxTimeMs":30000,"timeoutMs":120000}]}
                Rules: use only tools from the catalog; no cycles; max %d steps; last step should use llm_answer when possible.
                Use ai_decision_rag for similar-case search; natural_language_to_sql for analytics questions;
                human_in_the_loop before llm_answer when a proposal needs user sign-off (ASYNC — user approves via API).
                """.formatted(orchProps.getMaxStepsPerWorkflow());

        ObjectNode root = mapper.createObjectNode();
        root.put("temperature", 0.1);
        root.put("max_tokens", 4096);
        root.set("response_format", mapper.createObjectNode().put("type", "json_object"));
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content",
                "Question:\n" + question + "\n\nTool catalog:\n" + catalog);

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
