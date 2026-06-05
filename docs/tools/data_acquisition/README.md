# `data_acquisition` (v1.1.0)

## What it does

Given a user question and a **scenario**, loads **context rows** from Azure SQL for downstream workflow steps (`ai_decision_rag`, `llm_answer`). The tool never guesses table names from the LLM planner alone — it grounds every step in:

1. **`user_table_access`** — who may query which tables (authentication / authorization gate)
2. **`schema_catalog_*`** — human-readable table/column/FK descriptions the LLM uses instead of raw DDL
3. **Two-phase hierarchical refinement** — cheap table index first, detailed column + FK text only for tables the LLM actually picks

Then it runs a **single read-only SELECT** (TOP-limited) and returns rows + metadata.

**Good fit:** “Fetch a few rows of case / user / withdrawal / device / decision context to support an answer.”

**Poor fit:** Heavy BI, multi-statement batches, writes, or aggregates across the whole warehouse — use `natural_language_to_sql` for analytics-style questions.

---

## End-to-end pipeline

```text
orchestrator_run.user_id
        │
        ▼
┌───────────────────────┐
│  user_table_access    │  ACL: which table_name rows this user may touch
└───────────┬───────────┘
            │ ∩ enabled schema_catalog_table
            ▼
┌───────────────────────┐
│  scenario + override  │  Narrow candidates (withdrawal_review → 4 tables, qa → all)
└───────────┬───────────┘
            ▼
   Phase 1 — table index     schema_catalog_table.description only
            │                 LLM → {"tables":[...],"reason":"..."}
            ▼
   Phase 2 — schema detail    schema_catalog_column + schema_catalog_foreign_key
            │                 LLM → SELECT ... TOP N
            ▼
   ReadOnlySqlExecutor        validate + execute → rows
```

Implementation: `DataAcquisitionTool` → `DataAcquisitionService` → `DataAcquisitionPlannerService` + `SchemaCatalogService` + `UserTableAccessService`.

---

## Authentication table: `user_table_access`

**Migration:** `db/V19__user_table_access.sql`

| Column | Purpose |
|--------|---------|
| `user_id` | Caller identity (from `orchestrator_run.user_id` or tool HTTP context) |
| `table_name` | One allowed physical table (must also exist and be `enabled` in `schema_catalog_table`) |

**Rules (always applied before any catalog text or SQL is built):**

- Blank / missing `userId` → resolved to **`admin`** (`UserTableAccessService.DEFAULT_USER_ID`).
- `admin` is **seeded with every enabled** `schema_catalog_table` row so the ACL lookup always runs, even in dev.
- Users with **no rows** in `user_table_access` get **zero** candidates → acquisition fails with a permission-style error (no silent fallback to the full catalog).
- Workflow `tableNames` / planner hallucinations (e.g. `"users"`, `"accounts"`) are stripped to names that exist in the enabled catalog, then intersected again with the user ACL.

**Grant access (example):**

```sql
INSERT INTO dbo.user_table_access (user_id, table_name)
VALUES (N'analyst-1', N'risk_features'), (N'analyst-1', N'risk_decisions');
```

`userId` flows from `POST /agent/ask` / `POST /agent/execute` → `orchestrator_run.user_id` → `ToolExecutionContext.userId` on tool invoke.

---

## Schema description tables (what each is for)

These tables are the **semantic layer** between Azure SQL and the LLM. They are **not** the live data tables (`risk_features`, etc.) — they **describe** them.

| Table | Role in `data_acquisition` | Typical content |
|-------|---------------------------|-----------------|
| **`schema_catalog_table`** | **Phase 1 — table index.** One row per queryable table: `table_name`, `schema_name`, `description`, `enabled`. The LLM sees only name + short description to pick a minimal table set (≤ 6). | “Per-case structured risk features at ingest. Join on `record_uuid`…” |
| **`schema_catalog_column`** | **Phase 2 — column detail.** Per table: `column_name`, `data_type`, `description`, optional `sample_hint`, `enabled`. Teaches the model which columns exist and how to filter/join in plain language. | `user_id` — “Synthetic user identifier.” `[example: user-demo-001]` |
| **`schema_catalog_foreign_key`** | **Phase 2 — JOIN hints.** Directed edges `from_table.from_column → to_table.to_column` + description. Keeps generated SQL on documented relationships instead of invented joins. | `risk_features.record_uuid → risk_ingest_records.record_uuid` |

**Migrations:** `V4` (create), `V5` / `V8` (seed demo descriptions), `V12` (foreign keys).

**Governance:** Extend production by inserting/updating rows in these three tables (and toggling `enabled`). The tool only exposes **enabled** tables/columns/FKs. Descriptions should be written for **LLM consumption** — business meaning, join keys, and safe filter columns — not DBA internals.

**Related view (optional):** `V14__table_column_description.sql` merges live `sys.tables` / `sys.columns` with catalog text for ops dashboards; `data_acquisition` reads **`schema_catalog_*` directly**, not that view.

---

## Hierarchical refinement (why two phases)

Sending every column description for every enabled table on every question would blow the context window and add noise. The tool uses **progressive disclosure**:

| Level | Source | What the LLM sees | When |
|-------|--------|-------------------|------|
| **0 — Scenario** | `SchemaCatalogService.SCENARIO_TABLES` | Subset of table *names* for known use cases | Before Phase 1 |
| **1 — ACL** | `user_table_access` | User-specific ∩ enabled catalog names | Before Phase 1 |
| **2 — Table index** | `buildTableIndexText(candidates)` | Bullet list: `table_name (schema): description` | Phase 1 prompt |
| **3 — Schema detail** | `buildColumnAndForeignKeyText(selected)` | Full columns + FOREIGN KEYS block for **selected tables only** | Phase 2 prompt |

**Phase 1** (`selectTables`): small prompt → JSON `tables` + `reason`. Parsed names must stay inside the candidate set; if the model returns nothing valid, up to four allowed candidates are used as fallback.

**Phase 2** (`generateSql`): only the chosen tables’ column/FK text → single `SELECT` with `TOP N` (capped at 100).

If Phase 2 or a downstream `llm_answer` step still hits **context too large**, the orchestrator’s **adaptive retry** may summarize prior step outputs before re-running (see root README — Adaptive retry).

When Azure OpenAI is **not** configured, Phase 1 returns all ACL-filtered candidates and Phase 2 uses a deterministic fallback SQL template against `risk_features` when present.

---

## Scenario mapping

`params.scenario` (default `qa`) pre-filters **candidate table names** before ACL:

| Scenario | Default candidate tables |
|----------|-------------------------|
| `withdrawal_review` | `risk_features`, `risk_ingest_records`, `risk_decisions`, `activity_log` |
| `withdrawal_spike` | same as `withdrawal_review` |
| `login_anomaly` | `risk_features`, `risk_ingest_records`, `risk_decisions` |
| `qa` | **all** enabled catalog tables |

Unknown scenario keys fall back to all enabled tables. Scenario does **not** bypass ACL.

---

## Planner `tableNames` override

The workflow planner may pass `tableNames` in step `params`. These are **hints**, not trusted names:

1. Keep only names that exist in **enabled** `schema_catalog_table`.
2. If none survive (hallucinated list), fall back to the scenario’s default mapping.
3. Intersect with **`user_table_access`** for the run’s `userId`.

This prevents SQL against non-existent objects while still letting the planner narrow scope when it names real catalog tables.

---

## Main flow (summary)

1. **Input:** `question` + `scenario` + optional `maxRows` + optional `tableNames`; `userId` from run context.
2. **Resolve candidates:** scenario → catalog-enabled names → ACL filter.
3. **Phase 1:** LLM picks tables from table index → `tables` + `tableSelectionReason`.
4. **Phase 2:** LLM writes read-only SQL from column + FK detail.
5. **Execute:** `ReadOnlySqlValidator` + `ReadOnlySqlExecutor` → `rows`, `sql`, `features` bundle for RAG / answer steps.

---

## Prompt examples

### Phase 1: table selection (JSON only)

System instruction (excerpt; `DataAcquisitionPlannerService`):

```text
You pick database tables needed to answer a risk/QA question.
You receive ONLY a table index (name + description).
Output ONLY JSON: {"tables":["table_a"],"reason":"one sentence"}.
Rules: table names must come from the index; at least one table; at most 6 tables.
```

User message:

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

User message includes the **column + FOREIGN KEYS** block from `buildColumnAndForeignKeyText`, not the full catalog.

---

## Execute request examples

Endpoint: `POST /agent/tools/data_acquisition/1.1.0/execute`

**Typical workflow body** (orchestrator also passes `question`, `runId`, `stepKey`, `userId`):

```json
{
  "question": "Should we freeze this $15k withdrawal? User user-demo-001 had a new device yesterday.",
  "params": {
    "scenario": "withdrawal_review",
    "maxRows": 50
  }
}
```

**Standalone tool** (explicit params):

```json
{
  "scenario": "withdrawal_review",
  "question": "Should we freeze this $15k withdrawal? User user-demo-001 had a new device yesterday.",
  "maxRows": 50
}
```

**Table override** (planner hint — still ACL-filtered):

```json
{
  "scenario": "qa",
  "question": "Show me the latest decisions for user-demo-001",
  "maxRows": 20,
  "tableNames": ["risk_features", "risk_decisions", "risk_ingest_records"]
}
```

**Restricted user** — ensure `user_table_access` rows exist or the tool returns a permission error.

---

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

| Field | Meaning |
|-------|---------|
| `candidateTables` | After scenario + ACL (+ override sanitization), before LLM table pick |
| `tables` | Subset the LLM chose for this question |
| `tableSelectionReason` | One-line rationale from Phase 1 |
| `features` | Compact bundle for `llm_answer` / logging (includes `sample` first row) |

---

## Errors you may see

| Symptom | Typical cause |
|---------|----------------|
| `No schema tables permitted for user: …` | No `user_table_access` rows for that `userId` (or none overlap enabled catalog) |
| `Data acquisition failed: … permission …` | SQL / DB permission after ACL passed (orchestrator may fail without retry) |
| Context / token limit on a later step | Large `rows` in output — adaptive retry may summarize upstream context |
| Empty `tables` / ACL message in `tableSelectionReason` | Zero candidates after ACL — fix access rows before re-running |

---

## Related code & docs

| Item | Location |
|------|----------|
| Tool executor | `DataAcquisitionTool.java` |
| ACL | `UserTableAccessService.java`, `V19__user_table_access.sql` |
| Catalog builders | `SchemaCatalogService.java` (`buildTableIndexText`, `buildColumnAndForeignKeyText`) |
| Two-phase planner | `DataAcquisitionPlannerService.java` |
| Read-only SQL guard | `ReadOnlySqlValidator.java`, `ReadOnlySqlExecutor.java` |
| Root orchestrator docs | [README — User table ACL & Adaptive retry](../../README.md) |
