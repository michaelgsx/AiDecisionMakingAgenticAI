package com.aidecision.agentic.tool;

import java.util.List;

/** Metadata for built-in {@link AgentTool} beans persisted to {@code orchestrator_tool}. */
public final class BuiltinToolCatalog {

    private BuiltinToolCatalog() {}

    public record Definition(
            String name,
            String version,
            String description,
            String toolType,
            String executionMode,
            String requestSchemaJson,
            String responseSchemaJson
    ) {}

    public static List<Definition> all() {
        return List.of(
                new Definition(
                        "data_acquisition", "1.0.0",
                        "Fetch risk context / features for the current question.",
                        "DATA_ACQUISITION", "SYNC",
                        "{\"type\":\"object\",\"properties\":{\"scenario\":{\"type\":\"string\"}}}",
                        "{\"type\":\"object\",\"properties\":{\"features\":{\"type\":\"object\"}}}"),
                new Definition(
                        "similarity_retrieval", "1.0.0",
                        "Legacy alias — delegates to ai_decision_rag (/rag/assess).",
                        "SIMILARITY_RETRIEVAL", "SYNC",
                        "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}",
                        "{\"type\":\"object\",\"properties\":{\"hits\":{\"type\":\"array\"},\"summary\":{\"type\":\"string\"}}}"),
                new Definition(
                        "ai_decision_rag", "1.0.0",
                        "AiDecisionMakingBackend hybrid RAG assess for similar cases.",
                        "SIMILARITY_RETRIEVAL", "SYNC",
                        "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"},\"metadata\":{\"type\":\"string\"}}}",
                        "{\"type\":\"object\",\"properties\":{\"hits\":{\"type\":\"array\"},\"aiLabel\":{\"type\":\"string\"},\"summary\":{\"type\":\"string\"}}}"),
                new Definition(
                        "natural_language_to_sql", "1.0.0",
                        "Natural language → read-only SQL using schema_catalog descriptions.",
                        "AGGREGATE", "SYNC",
                        "{\"type\":\"object\",\"properties\":{\"question\":{\"type\":\"string\"},\"maxRows\":{\"type\":\"integer\"}}}",
                        "{\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\"},\"rows\":{\"type\":\"array\"},\"rowCount\":{\"type\":\"integer\"}}}"),
                new Definition(
                        "human_in_the_loop", "1.0.0",
                        "Async user approval: is the proposed solution acceptable?",
                        "VALIDATION", "ASYNC",
                        "{\"type\":\"object\",\"properties\":{\"proposal\":{\"type\":\"string\"},\"prompt\":{\"type\":\"string\"}}}",
                        "{\"type\":\"object\",\"properties\":{\"decision\":{\"type\":\"string\"},\"accepted\":{\"type\":\"boolean\"}}}"),
                new Definition(
                        "llm_answer", "1.0.0",
                        "Synthesize final answer from prior workflow step outputs.",
                        "LLM_REASONING", "SYNC",
                        "{\"type\":\"object\",\"properties\":{}}",
                        "{\"type\":\"object\",\"properties\":{\"answer\":{\"type\":\"string\"}}}")
        );
    }
}
