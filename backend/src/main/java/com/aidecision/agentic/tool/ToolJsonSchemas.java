package com.aidecision.agentic.tool;

/** JSON Schema strings for orchestrator_tool; every property includes a description for the workflow planner. */
final class ToolJsonSchemas {

    private ToolJsonSchemas() {}

    static final String DATA_ACQUISITION_REQUEST = """
            {
              "type": "object",
              "description": "NL question + scenario; LLM generates read-only SQL from schema_catalog descriptions.",
              "properties": {
                "scenario": {
                  "type": "string",
                  "description": "Risk use-case id (withdrawal_review, withdrawal_spike, login_anomaly, qa). Scopes catalog tables in the prompt."
                },
                "question": {
                  "type": "string",
                  "description": "Natural-language question driving SQL generation. Defaults to the workflow user question."
                },
                "maxRows": {
                  "type": "integer",
                  "description": "Max rows to return from the generated SELECT (1–100, default 50)."
                },
                "tableNames": {
                  "type": "array",
                  "items": { "type": "string" },
                  "description": "Optional override: only these schema_catalog tables in the LLM prompt."
                }
              }
            }
            """;

    static final String DATA_ACQUISITION_RESPONSE = """
            {
              "type": "object",
              "description": "SQL-backed context for downstream tools (RAG metadata, llm_answer).",
              "properties": {
                "scenario": { "type": "string", "description": "Scenario id used for table scoping." },
                "tables": {
                  "type": "array",
                  "items": { "type": "string" },
                  "description": "schema_catalog tables included in the LLM prompt."
                },
                "sql": { "type": "string", "description": "Generated read-only SELECT executed against Azure SQL." },
                "rows": {
                  "type": "array",
                  "description": "Result rows (objects keyed by column name)."
                },
                "rowCount": { "type": "integer", "description": "Number of rows returned." },
                "features": {
                  "type": "object",
                  "description": "Summary bundle: scenario, tables, rowCount, sample first row for RAG metadata."
                },
                "note": { "type": "string", "description": "Human-readable acquisition summary." }
              }
            }
            """;

    static final String SIMILARITY_REQUEST = """
            {
              "type": "object",
              "description": "Similar-case search inputs (legacy tool; same as ai_decision_rag).",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "Natural-language case text to find similar historical cases. Alias for text."
                },
                "text": {
                  "type": "string",
                  "description": "Case notes / question text sent to RAG assess."
                },
                "metadata": {
                  "type": "string",
                  "description": "JSON string of case metadata (user_id, scenario, transaction_id) for hybrid search."
                }
              }
            }
            """;

    static final String RAG_RESPONSE = """
            {
              "type": "object",
              "description": "AiDecision RAG assess result; use aiLabel and hits for branching.",
              "properties": {
                "query": { "type": "string", "description": "Echo of the search text." },
                "risk": { "type": "string", "description": "Coarse risk level from search (e.g. high, medium, low)." },
                "searchReason": { "type": "string", "description": "Short Azure AI Search summary (not full LLM write-up)." },
                "aiLabel": {
                  "type": "string",
                  "description": "Model recommendation: passed | rejected | frozen. Use for if/else before human_in_the_loop."
                },
                "aiReason": { "type": "string", "description": "Full analyst-style explanation from LLM when configured." },
                "hits": {
                  "type": "array",
                  "description": "Similar records: recordId, score, snippet, reviewOutcome, scenario."
                },
                "summary": { "type": "string", "description": "One-line tool summary for logs." },
                "source": { "type": "string", "description": "AiDecisionMakingBackend or demo." }
              }
            }
            """;

    static final String NL2SQL_REQUEST = """
            {
              "type": "object",
              "description": "Natural language analytics question over Azure SQL (read-only).",
              "properties": {
                "question": {
                  "type": "string",
                  "description": "Analytics question in plain English (counts, filters, aggregates)."
                },
                "maxRows": {
                  "type": "integer",
                  "description": "Max rows to return (1-200). Default 50 if omitted."
                }
              },
              "required": ["question"]
            }
            """;

    static final String NL2SQL_RESPONSE = """
            {
              "type": "object",
              "description": "Executed SELECT result; branch on rowCount for empty vs non-empty.",
              "properties": {
                "sql": { "type": "string", "description": "Generated read-only SQL that was executed." },
                "rows": { "type": "array", "description": "Result rows as objects (column label → value)." },
                "rowCount": {
                  "type": "integer",
                  "description": "Number of rows returned. 0 means no data — adjust question or skip llm_answer claims."
                },
                "summary": { "type": "string", "description": "Short execution summary." }
              }
            }
            """;

    static final String HUMAN_REQUEST = """
            {
              "type": "object",
              "description": "Async human approval before final answer.",
              "properties": {
                "prompt": {
                  "type": "string",
                  "description": "Question shown to the reviewer in the QA UI (e.g. Is this freeze acceptable?)."
                },
                "proposal": {
                  "type": "string",
                  "description": "Full proposed action text; defaults to concatenated prior step outputs if omitted."
                }
              }
            }
            """;

    static final String HUMAN_RESPONSE = """
            {
              "type": "object",
              "description": "After user responds via POST /agent/runs/{runId}/human-response.",
              "properties": {
                "requestId": { "type": "string", "description": "UUID for the human request row." },
                "stepKey": { "type": "string", "description": "Workflow step id this approval belongs to." },
                "prompt": { "type": "string", "description": "Prompt shown to the user." },
                "proposal": { "type": "string", "description": "Proposal text that was reviewed." },
                "status": { "type": "string", "description": "WAITING until answered, then ANSWERED." },
                "decision": {
                  "type": "string",
                  "description": "accept | reject after user responds. Use for branching to llm_answer tone."
                },
                "accepted": {
                  "type": "boolean",
                  "description": "true if decision=accept; false if reject. Primary branch flag."
                },
                "comment": { "type": "string", "description": "Optional reviewer comment." },
                "summary": { "type": "string", "description": "Short outcome summary." }
              }
            }
            """;

    static final String LLM_ANSWER_REQUEST = """
            {
              "type": "object",
              "description": "No required inputs; reads prior step JSON from workflow context.",
              "properties": {}
            }
            """;

    static final String LLM_ANSWER_RESPONSE = """
            {
              "type": "object",
              "description": "Final user-facing answer.",
              "properties": {
                "answer": {
                  "type": "string",
                  "description": "Complete natural-language response to the original user question."
                }
              },
              "required": ["answer"]
            }
            """;
}
