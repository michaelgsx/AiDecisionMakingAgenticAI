package com.aidecision.agentic.tool;

import java.util.List;

/** Metadata for built-in {@link AgentTool} beans persisted to {@code orchestrator_tool}. */
public final class BuiltinToolCatalog {

    private BuiltinToolCatalog() {}

    public record Definition(
            String name,
            String version,
            int maxRetry,
            String description,
            String toolType,
            String executionMode,
            String requestSchemaJson,
            String responseSchemaJson,
            String endpointUrl
    ) {}

    public static List<Definition> all() {
        return List.of(
                define(
                        "data_acquisition", "1.1.0", 3,
                        "LLM generates read-only SQL from schema_catalog + user question; returns context rows.",
                        "DATA_ACQUISITION", "SYNC",
                        ToolJsonSchemas.DATA_ACQUISITION_REQUEST,
                        ToolJsonSchemas.DATA_ACQUISITION_RESPONSE),
                define(
                        "similarity_retrieval", "1.1.0", 3,
                        "Legacy alias — delegates to ai_decision_rag (/rag/assess).",
                        "SIMILARITY_RETRIEVAL", "SYNC",
                        ToolJsonSchemas.SIMILARITY_REQUEST,
                        ToolJsonSchemas.RAG_RESPONSE),
                define(
                        "ai_decision_rag", "1.1.0", 3,
                        "AiDecisionMakingBackend hybrid RAG assess for similar cases.",
                        "SIMILARITY_RETRIEVAL", "SYNC",
                        ToolJsonSchemas.SIMILARITY_REQUEST,
                        ToolJsonSchemas.RAG_RESPONSE),
                define(
                        "natural_language_to_sql", "1.1.0", 3,
                        "Natural language → read-only SQL using schema_catalog descriptions.",
                        "AGGREGATE", "SYNC",
                        ToolJsonSchemas.NL2SQL_REQUEST,
                        ToolJsonSchemas.NL2SQL_RESPONSE),
                define(
                        "human_in_the_loop", "1.1.0", 1,
                        "Async user approval: is the proposed solution acceptable?",
                        "VALIDATION", "ASYNC",
                        ToolJsonSchemas.HUMAN_REQUEST,
                        ToolJsonSchemas.HUMAN_RESPONSE),
                define(
                        "llm_answer", "1.1.0", 3,
                        "Synthesize final answer from prior workflow step outputs.",
                        "LLM_REASONING", "SYNC",
                        ToolJsonSchemas.LLM_ANSWER_REQUEST,
                        ToolJsonSchemas.LLM_ANSWER_RESPONSE)
        );
    }

    private static Definition define(
            String name, String version, int maxRetry, String description,
            String toolType, String executionMode, String requestSchema, String responseSchema) {
        return new Definition(
                name, version, maxRetry, description, toolType, executionMode,
                compact(requestSchema), compact(responseSchema), executeUrl(name, version));
    }

    /** Relative execute endpoint exposed by each tool controller. */
    public static String executeUrl(String name, String version) {
        return "/agent/tools/" + name + "/" + version + "/execute";
    }

    /** Single-line JSON for SQL storage (planner parses as object). */
    private static String compact(String prettyJson) {
        return prettyJson.replaceAll("\\s+", " ").trim();
    }
}
