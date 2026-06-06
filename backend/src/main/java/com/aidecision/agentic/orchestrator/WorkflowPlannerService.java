package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.AzureOpenAiProperties;
import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.entity.PlannerWorkflowCache;
import com.aidecision.agentic.service.PlannerWorkflowCacheService;
import com.aidecision.agentic.tool.ToolRegistryService;
import com.aidecision.agentic.util.LogSanitizer;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkflowPlannerService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowPlannerService.class);

    private final AzureOpenAiProperties openAi;
    private final OrchestratorProperties orchProps;
    private final ToolRegistryService toolRegistry;
    private final WorkflowDagValidator validator;
    private final WorkflowPlannerPromptBuilder promptBuilder;
    private final PlannerWorkflowCacheService workflowCache;
    private final CompoundWorkflowExpander compoundExpander;
    private final ObjectMapper mapper;
    private final RestClient http;

    public WorkflowPlannerService(
            AzureOpenAiProperties openAi,
            OrchestratorProperties orchProps,
            ToolRegistryService toolRegistry,
            WorkflowDagValidator validator,
            WorkflowPlannerPromptBuilder promptBuilder,
            PlannerWorkflowCacheService workflowCache,
            CompoundWorkflowExpander compoundExpander,
            ObjectMapper mapper,
            RestClient http) {
        this.openAi = openAi;
        this.orchProps = orchProps;
        this.toolRegistry = toolRegistry;
        this.validator = validator;
        this.promptBuilder = promptBuilder;
        this.workflowCache = workflowCache;
        this.compoundExpander = compoundExpander;
        this.mapper = mapper;
        this.http = http;
    }

    /** Builds the three-part planner prompt without calling the LLM (for inspection / debugging). */
    public PlannerPrompt buildPrompt(String question) throws Exception {
        return promptBuilder.build(question, toolRegistry.enabledToolsByName());
    }

    public WorkflowDag plan(String question) throws Exception {
        log.info("Planning workflow question={}", LogSanitizer.question(question));
        Map<String, OrchestratorTool> tools = toolRegistry.enabledToolsByName();
        if (tools.isEmpty()) {
            log.warn("No enabled tools in orchestrator_tool; using default DAG only");
        }
        if (openAi.chatConfigured() && !tools.isEmpty()) {
            try {
                PlannerWorkflowResponse response = callPlannerLlm(question, tools);
                if (response.isInsufficientTools()) {
                    log.warn("Planner refused question={} missingTools={}",
                            LogSanitizer.question(question),
                            response.missingTools());
                    throw new InsufficientToolsException(
                            response.message() == null || response.message().isBlank()
                                    ? "Not enough tools to answer this question."
                                    : response.message(),
                            response.missingTools());
                }
                WorkflowDag dag = compoundExpander.expandIfNeeded(question, response.toDag(), tools);
                validator.validate(dag, tools);
                log.info("Planner produced {} step(s) after compound expansion", dag.steps().size());
                return dag;
            } catch (InsufficientToolsException e) {
                throw e;
            } catch (Exception e) {
                log.warn("LLM workflow planning failed, using default DAG: {}",
                        LogSanitizer.message(e.getMessage()));
            }
        }
        WorkflowDag fallback = defaultDag(question);
        validator.validate(fallback, tools);
        log.info("Using default workflow with {} step(s)", fallback.steps().size());
        return fallback;
    }

    private WorkflowDag defaultDag(String question) {
        return new WorkflowDag(List.of(
                new WorkflowDag.WorkflowStepDef("s1", "data_acquisition", List.of(),
                        Map.of("scenario", "qa", "question", question),
                        (int) orchProps.getDefaultStepMaxTimeMs(), (int) orchProps.getDefaultStepTimeoutMs()),
                new WorkflowDag.WorkflowStepDef("s2", "ai_decision_rag", List.of("s1"), Map.of("text", question),
                        (int) orchProps.getDefaultStepMaxTimeMs(), (int) orchProps.getDefaultStepTimeoutMs()),
                new WorkflowDag.WorkflowStepDef("s3", "llm_answer", List.of("s1", "s2"), Map.of(),
                        (int) orchProps.getDefaultStepMaxTimeMs(), (int) orchProps.getDefaultStepTimeoutMs())
        ));
    }

    private PlannerWorkflowResponse callPlannerLlm(String question, Map<String, OrchestratorTool> tools)
            throws Exception {
        PlannerPrompt prompt = promptBuilder.build(question, tools);
        Optional<PlannerWorkflowCache> cached = workflowCache.findByQuestion(question);
        if (cached.isPresent() && workflowCache.matchesPlannerPrompt(cached.get(), prompt)) {
            log.info("Using cached planner workflow question={}", LogSanitizer.question(question));
            return parseResponse(cached.get().getWorkflowJson());
        }
        if (cached.isPresent()) {
            log.info("Planner workflow cache stale (prompt or tools changed), replanning question={}",
                    LogSanitizer.question(question));
        }

        String raw = invokeChat(prompt.systemPrompt(), prompt.userPrompt());
        return parseResponse(raw);
    }

    /**
     * Saves question + workflow to planner cache after thumbs-up. Skips when run is incomplete
     * or has no persisted workflow.
     */
    public void cacheWorkflowFromThumbsUp(OrchestratorRun run) {
        if (run == null) {
            return;
        }
        String question = run.getQuestion();
        String workflowJson = run.getWorkflowJson();
        if (question == null || question.isBlank() || workflowJson == null || workflowJson.isBlank()) {
            log.debug("Skip planner cache for run {} — missing question or workflow", run.getRunId());
            return;
        }
        if (!RunStatus.COMPLETED.name().equals(run.getStatus())) {
            log.debug("Skip planner cache for run {} — status={}", run.getRunId(), run.getStatus());
            return;
        }
        try {
            PlannerPrompt prompt = promptBuilder.build(question, toolRegistry.enabledToolsByName());
            workflowCache.save(question, prompt, workflowJson.trim());
            log.info("Planner workflow cached after thumbs-up run={} question={}",
                    run.getRunId(), LogSanitizer.question(question));
        } catch (Exception e) {
            log.warn("Failed to cache planner workflow after thumbs-up run={}: {}",
                    run.getRunId(), LogSanitizer.message(e.getMessage()));
        }
    }

    String invokeChat(String systemPrompt, String userPrompt) throws Exception {
        String base = openAi.getEndpoint().replaceAll("/+$", "");
        URI uri = URI.create(base + "/openai/deployments/" + openAi.getChatDeployment()
                + "/chat/completions?api-version=" + openAi.getEffectiveChatApiVersion());

        ObjectNode root = mapper.createObjectNode();
        root.put("max_completion_tokens", 4096);
        root.set("response_format", mapper.createObjectNode().put("type", "json_object"));
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

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

    PlannerWorkflowResponse parseResponse(String json) throws Exception {
        JsonNode root = mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        String status = root.path("status").asText("").trim();
        if (status.isBlank()) {
            // Backward compatibility: bare { "steps": [...] }
            if (root.path("steps").isArray()) {
                status = PlannerWorkflowResponse.STATUS_OK;
            } else {
                throw new IllegalArgumentException("Planner JSON missing status field");
            }
        }

        String message = root.path("message").asText("").trim();
        List<String> missingTools = new ArrayList<>();
        for (JsonNode n : root.path("missingTools")) {
            String item = n.asText("").trim();
            if (!item.isBlank()) {
                missingTools.add(item);
            }
        }

        if (PlannerWorkflowResponse.STATUS_INSUFFICIENT_TOOLS.equalsIgnoreCase(status)) {
            if (message.isBlank()) {
                message = "Not enough tools to answer this question.";
            }
            return new PlannerWorkflowResponse(status, message, missingTools, List.of());
        }

        if (!PlannerWorkflowResponse.STATUS_OK.equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Unknown planner status: " + status);
        }

        JsonNode stepsNode = root.path("steps");
        if (!stepsNode.isArray() || stepsNode.isEmpty()) {
            throw new IllegalArgumentException("Planner status=ok requires non-empty steps array");
        }

        List<WorkflowDag.WorkflowStepDef> steps = new ArrayList<>();
        for (JsonNode n : stepsNode) {
            List<String> deps = new ArrayList<>();
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

        if (message.isBlank()) {
            message = "Planned " + steps.size() + " step(s).";
        }
        return new PlannerWorkflowResponse(status, message, List.of(), steps);
    }
}
