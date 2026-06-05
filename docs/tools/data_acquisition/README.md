# `data_acquisition` (v1.1.0)

## What it does

Given a user question and a **scenario** (`scenario`), uses the **schema catalog** (`schema_catalog_table` / `column` / `foreign_key`) to:

1. **Select tables** (table index: name + description)
2. **Generate SQL** (columns + foreign keys: column descriptions + JOIN edges)
3. Run a **read-only SELECT** (with TOP limit) and return **context rows** for downstream tools (RAG, `llm_answer`)

**Good fit:** Questions that need a few key context rows to support explanation or decisions (case / user / withdrawal / device / review outcome, etc.).

**Poor fit:** Complex BI reports, multi-statement batches, or writes — this tool enforces a single read-only SELECT.

## Main flow

1. **Input:** `question` + `scenario` (optional `tableNames` to override candidate tables)
2. **Phase 1 (table selection):** Build a table index (name + description) for candidates; prompt the LLM for JSON: `{"tables":[...],"reason":"..."}`
3. **Phase 2 (SQL generation):** Show the LLM column detail + FOREIGN KEYS only; require **SQL only** output
4. **Execute:** `ReadOnlySqlExecutor.executeSelect(sql, maxRows)` → `rows`

User table ACL (`user_table_access`) is applied before catalog lookup; see root README.

## Prompt examples

### Phase 1: table selection (JSON output)

System instruction (excerpt; full text in `DataAcquisitionPlannerService`):

```text
You pick database tables needed to answer a risk/QA question.
You receive ONLY a table index (name + description).
Output ONLY JSON: {"tables":["table_a"],"reason":"one sentence"}.
Rules: at least one table; at most 6 tables.
```

User message shape:

```text
Table index:
- risk_features (dbo): One row per risk case...
- risk_decisions (dbo): Audit trail...
- risk_ingest_records (dbo): Cases ingested...

User question:
Should we freeze this $15k withdrawal? User user-demo-001 had a new device yesterday.
```

### Phase 2: SQL generation (SQL only)

System instruction (excerpt):

```text
Use ONLY tables, columns, and FOREIGN KEYS from the schema detail below.
Join tables using the documented foreign keys when multiple tables are needed.
Output ONLY the SQL.
Rules: single SELECT; no semicolons; use TOP N or less; read-only.
```

## Execute request example

Endpoint: `POST /agent/tools/data_acquisition/1.1.0/execute`

```json
{
  "scenario": "withdrawal_review",
  "question": "Should we freeze this $15k withdrawal? User user-demo-001 had a new device yesterday.",
  "maxRows": 50
}
```

Optional candidate table override (skips scenario default mapping):

```json
{
  "scenario": "qa",
  "question": "Show me the latest decisions for user-demo-001",
  "maxRows": 20,
  "tableNames": ["risk_features", "risk_decisions", "risk_ingest_records"]
}
```

## Response example

```json
{
  "scenario": "withdrawal_review",
  "candidateTables": ["risk_features", "risk_ingest_records", "risk_decisions", "activity_log"],
  "tables": ["risk_features", "risk_decisions"],
  "tableSelectionReason": "Need case context and decision outcome.",
  "sql": "SELECT TOP 20 ...",
  "rows": [
    { "request_id": "1111...", "user_id": "user-demo-001", "withdraw_amount": 15000.00 }
  ],
  "rowCount": 1,
  "features": {
    "scenario": "withdrawal_review",
    "source": "schema_catalog_two_phase_sql",
    "candidateTables": ["risk_features", "risk_ingest_records", "risk_decisions", "activity_log"],
    "tables": ["risk_features", "risk_decisions"],
    "tableSelectionReason": "Need case context and decision outcome.",
    "rowCount": 1,
    "sample": { "request_id": "1111...", "user_id": "user-demo-001" }
  },
  "note": "Loaded 1 row(s) from 2 table(s) (scenario withdrawal_review)."
}
```
