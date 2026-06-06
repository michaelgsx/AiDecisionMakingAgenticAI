package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.service.SchemaCatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;

/**
 * Builds the three-part workflow planner prompt:
 * 1) system — role + planning rules + insufficient-tools policy
 * 2) user — question + tool registry (with schemas)
 * 3) output JSON schema — contract for executable workflow JSON
 */
@Component
public class WorkflowPlannerPromptBuilder {

    private final OrchestratorProperties orchProps;
    private final SchemaCatalogService schemaCatalog;
    private final ObjectMapper mapper;

    public WorkflowPlannerPromptBuilder(
            OrchestratorProperties orchProps, SchemaCatalogService schemaCatalog, ObjectMapper mapper) {
        this.orchProps = orchProps;
        this.schemaCatalog = schemaCatalog;
        this.mapper = mapper;
    }

    public PlannerPrompt build(String question, Map<String, OrchestratorTool> tools) throws Exception {
        String outputSchema = buildOutputJsonSchema();
        return new PlannerPrompt(
                buildSystemPrompt(),
                buildUserPrompt(question, tools, outputSchema),
                outputSchema);
    }

    String buildSystemPrompt() {
        int maxSteps = orchProps.getMaxStepsPerWorkflow();
        long defaultMax = orchProps.getDefaultStepMaxTimeMs();
        long defaultTimeout = orchProps.getDefaultStepTimeoutMs();

        return """
                You are the workflow planner for a risk-control agent orchestrator.

                Goal: using ONLY the tools provided in the user message, design a complete execution \
                workflow (a directed acyclic graph of tool steps) that answers the user's question.

                If the provided tools are NOT sufficient to answer the question safely and completely, \
                do NOT invent tools or steps. Instead respond with JSON where status is \
                "insufficient_tools", explain what capability is missing in message, and list each gap \
                in missingTools (e.g. "payment_history_lookup", "real_time_fraud_score_api").

                IMPORTANT — do NOT refuse data lookups that the catalog can serve: the \
                data_acquisition tool generates read-only SQL over the SQL data catalog listed in the \
                user message. Looking up or showing a specific user, account, transaction, device, or \
                record by id or attribute (e.g. "show me the information of user-001") IS supported \
                whenever a catalog table contains the relevant column. For such requests use \
                data_acquisition (then llm_answer) and DO NOT return insufficient_tools.

                When status is "ok":
                - Output ONLY valid JSON matching outputJsonSchema (no markdown, no commentary).
                - Use ONLY tool names from tools[] in the user message.
                - Every step.params must conform to that tool's requestSchema descriptions.
                - dependsOn must be acyclic; step ids unique (s1, s2, ...).
                - End with llm_answer when that tool is available.
                - Default maxTimeMs=%d, timeoutMs=%d unless a step needs more (especially ASYNC tools).
                - At most %d steps.

                Tool-selection hints (only when those tools exist in tools[]):
                - User/profile/transaction lookup by id: data_acquisition then llm_answer.
                - Similar historical cases / policy: ai_decision_rag or similarity_retrieval.
                - Aggregates / counts / analytics SQL: natural_language_to_sql before llm_answer.
                - Human approval before final answer: human_in_the_loop (ASYNC) before llm_answer.

                Compound / multi-part questions (CRITICAL):
                Before planning, enumerate every DISTINCT outcome the user wants — counts, lists, \
                breakdowns, comparisons, time windows, filters, or separate metrics. Signals of \
                multiple outcomes include "and", "also", "plus", "as well as", semicolons, commas \
                between requests, numbered sub-questions, or "both X and Y".

                Splitting rule (natural_language_to_sql and mixed data steps):
                - Each natural_language_to_sql step = exactly ONE read-only SELECT (no semicolons).
                - If the user needs K distinct SQL analytics outcomes, create K natural_language_to_sql \
                steps (K may be 1, 2, 3, 4, or more up to the step limit). Do NOT assume K=2.
                - Never pass the full compound sentence to a single NL2SQL step.
                - Each params.question = one standalone sub-question for ONE outcome only; derive it \
                from the user message, do not copy the whole compound sentence verbatim.
                - Other tools follow the same idea: one focused sub-task per step (e.g. separate \
                data_acquisition lookups or RAG queries when sub-intents differ).

                Parallel vs sequential dependsOn:
                - Sub-intents that do NOT need another step's result rows → dependsOn: [] \
                (executor runs them in the same parallel wave).
                - Sub-intent B only makes sense after A (e.g. "breakdown of those frozen cases", \
                "for the users from step 1") → B dependsOn the step(s) it needs.
                - When dependency is unclear, prefer parallel [] unless wording clearly references \
                prior results ("those", "them", "above", "from that set").

                Finishing the DAG:
                - llm_answer is always the last step when available.
                - llm_answer dependsOn must list EVERY upstream data-gathering step id (all NL2SQL, \
                data_acquisition, RAG, etc. whose outputs the final answer needs).
                - Single-intent questions: one data step + llm_answer is enough; do not over-split.

                Shape (variable N — adapt ids and count to the user message, no fixed template):
                  s1..sN — one data tool step per distinct sub-intent; parallel when independent
                  s(N+1) llm_answer — dependsOn all of s1..sN (or the sequential chain tail)
                """
                .formatted(defaultMax, defaultTimeout, maxSteps);
    }

    String buildUserPrompt(String question, Map<String, OrchestratorTool> tools, String outputJsonSchema)
            throws Exception {
        String toolsJson = buildToolsJson(tools);
        String catalogIndex = safeCatalogIndex();
        return """
                ## 1. User question
                %s

                ## 2. Available tools (use ONLY these; each includes requestSchema and responseSchema)
                %s

                ## 3. SQL data catalog (tables reachable via data_acquisition / natural_language_to_sql)
                Use this to decide whether data_acquisition can answer the question; if a relevant \
                table exists, plan data_acquisition instead of refusing.
                %s

                ## 4. Required output JSON schema
                Respond with a single JSON object that conforms to this schema:
                %s
                """
                .formatted(question == null ? "" : question.trim(), toolsJson, catalogIndex, outputJsonSchema);
    }

    private String safeCatalogIndex() {
        try {
            return schemaCatalog.buildTableIndexText(null);
        } catch (Exception e) {
            return "(schema catalog unavailable)";
        }
    }

    String buildOutputJsonSchema() throws Exception {
        long defaultMax = orchProps.getDefaultStepMaxTimeMs();
        long defaultTimeout = orchProps.getDefaultStepTimeoutMs();

        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");
        root.put("description", "Planner response: executable workflow or insufficient-tools refusal.");

        ObjectNode props = root.putObject("properties");
        props.putObject("status")
                .put("type", "string")
                .put("description", "ok = workflow provided; insufficient_tools = cannot plan with given tools")
                .set("enum", mapper.createArrayNode().add("ok").add("insufficient_tools"));

        props.putObject("message")
                .put("type", "string")
                .put("description", "One-sentence rationale, or why tools are insufficient when status=insufficient_tools");

        ObjectNode missing = props.putObject("missingTools");
        missing.put("type", "array");
        missing.put("description", "Required when status=insufficient_tools: missing capabilities or tool names");
        missing.putObject("items").put("type", "string");

        ObjectNode steps = props.putObject("steps");
        steps.put("type", "array");
        steps.put("description", "Required when status=ok: DAG steps (count varies — one data step "
                + "per distinct sub-intent, then llm_answer last)");
        ObjectNode stepItem = steps.putObject("items");
        stepItem.put("type", "object");
        ObjectNode stepProps = stepItem.putObject("properties");
        stepProps.putObject("id").put("type", "string").put("description", "Unique step id, e.g. s1");
        stepProps.putObject("tool")
                .put("type", "string")
                .put("description", "toolName from tools[]");
        stepProps.putObject("dependsOn")
                .put("type", "array")
                .put("description", "Step ids that must complete first; use [] for none. Steps whose "
                        + "dependencies are all satisfied run in parallel in the same wave.")
                .putArray("items")
                .addObject()
                .put("type", "string");
        stepProps.putObject("params")
                .put("type", "object")
                .put("description", "Tool inputs matching requestSchema");
        stepProps.putObject("maxTimeMs")
                .put("type", "integer")
                .put("description", "Soft budget ms (default " + defaultMax + ")");
        stepProps.putObject("timeoutMs")
                .put("type", "integer")
                .put("description", "Hard timeout ms (default " + defaultTimeout + ")");
        ArrayNode stepRequired = stepItem.putArray("required");
        stepRequired.add("id").add("tool").add("dependsOn").add("params");

        ArrayNode required = root.putArray("required");
        required.add("status").add("message");

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    String buildToolsJson(Map<String, OrchestratorTool> tools) throws Exception {
        ArrayNode registry = mapper.createArrayNode();
        tools.values().stream()
                .sorted(Comparator.comparing(OrchestratorTool::getToolName))
                .forEach(t -> {
                    ObjectNode row = registry.addObject();
                    row.put("toolName", t.getToolName());
                    row.put("version", t.getVersion());
                    row.put("maxRetry", t.getMaxRetry());
                    row.put("toolType", t.getToolType());
                    row.put("executionMode", t.getExecutionMode());
                    row.put("description", t.getDescription());
                    if (t.getEndpointUrl() != null && !t.getEndpointUrl().isBlank()) {
                        row.put("endpointUrl", t.getEndpointUrl());
                    }
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
}
