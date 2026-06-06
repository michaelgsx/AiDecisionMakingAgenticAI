# `natural_language_to_sql` (v1.1.0)

## What it does

Turns a natural-language analytics question into a **read-only Microsoft SQL Server SELECT**, runs it on Azure SQL, and returns result rows.

**Good fit:** Counts, grouping, filters, time-window stats (e.g. “freeze rate in the last 7 days”).

**Poor fit:** Writes, DDL, multi-statement batches, stored procedures — only a single read-only SELECT is allowed.

User table ACL (`user_table_access`) limits which catalog tables may appear in generated SQL.

## Main flow

1. **Input:** `question` (required) + optional `maxRows`
2. **Prompt:** `schema_catalog_table` / `column` text is sent to the LLM; output must be SQL only
3. **Execute:** `ReadOnlySqlExecutor` runs the SELECT → `rows` + `rowCount`

## Prompt example (SQL only)

System instruction (excerpt; see `LlmSqlGenerationService` — **Microsoft SQL Server T-SQL only**):

```text
TARGET DIALECT: Microsoft SQL Server (T-SQL) on Azure SQL.
Write T-SQL only — NOT PostgreSQL/MySQL. Use TOP (never LIMIT), dbo.table_name.
NEVER use COUNT(DISTINCT ...) OVER () — SQL Server rejects it; use CTE + scalar subquery instead.
You write T-SQL SELECT-only queries for risk analytics.
Output ONLY the SQL (no markdown fences, no explanation).
Rules: single SELECT; no semicolons; use TOP 100 or less; read-only.
```

## Request example

Endpoint: `POST /agent/tools/natural_language_to_sql/1.1.0/execute`

```json
{
  "question": "In the last 7 days, how many cases were frozen vs passed? Group by scenario.",
  "maxRows": 50
}
```

## Response example

```json
{
  "sql": "SELECT TOP 50 scenario, decision, COUNT(*) AS cnt FROM dbo.risk_decisions ...",
  "rows": [
    { "scenario": "withdrawal_spike", "decision": "freeze", "cnt": 12 },
    { "scenario": "withdrawal_spike", "decision": "pass", "cnt": 40 }
  ],
  "rowCount": 2,
  "summary": "Executed SELECT; 2 row(s)."
}
```
