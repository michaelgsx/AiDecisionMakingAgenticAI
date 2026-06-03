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
            String responseSchemaJson
    ) {}

    public static List<Definition> all() {
        return List.of(
                new Definition(
                        "data_acquisition", "1.1.0", 3,
                        "LLM generates read-only SQL from schema_catalog + user question; returns context rows.",
                        "DATA_ACQUISITION", "SYNC",
                        compact(ToolJsonSchemas.DATA_ACQUISITION_REQUEST),
                        compact(ToolJsonSchemas.DATA_ACQUISITION_RESPONSE)),
                new Definition(
                        "similarity_retrieval", "1.1.0", 3,
                        "Legacy alias — delegates to ai_decision_rag (/rag/assess).",
                        "SIMILARITY_RETRIEVAL", "SYNC",
                        compact(ToolJsonSchemas.SIMILARITY_REQUEST),
                        compact(ToolJsonSchemas.RAG_RESPONSE)),
                new Definition(
                        "ai_decision_rag", "1.1.0", 3,
                        "AiDecisionMakingBackend hybrid RAG assess for similar cases.",
                        "SIMILARITY_RETRIEVAL", "SYNC",
                        compact(ToolJsonSchemas.SIMILARITY_REQUEST),
                        compact(ToolJsonSchemas.RAG_RESPONSE)),
                new Definition(
                        "natural_language_to_sql", "1.1.0", 3,
                        "Natural language → read-only SQL using schema_catalog descriptions.",
                        "AGGREGATE", "SYNC",
                        compact(ToolJsonSchemas.NL2SQL_REQUEST),
                        compact(ToolJsonSchemas.NL2SQL_RESPONSE)),
                new Definition(
                        "human_in_the_loop", "1.1.0", 1,
                        "Async user approval: is the proposed solution acceptable?",
                        "VALIDATION", "ASYNC",
                        compact(ToolJsonSchemas.HUMAN_REQUEST),
                        compact(ToolJsonSchemas.HUMAN_RESPONSE)),
                new Definition(
                        "llm_answer", "1.1.0", 3,
                        "Synthesize final answer from prior workflow step outputs.",
                        "LLM_REASONING", "SYNC",
                        compact(ToolJsonSchemas.LLM_ANSWER_REQUEST),
                        compact(ToolJsonSchemas.LLM_ANSWER_RESPONSE))
        );
    }

    /** Single-line JSON for SQL storage (planner parses as object). */
    private static String compact(String prettyJson) {
        return prettyJson.replaceAll("\\s+", " ").trim();
    }
}
