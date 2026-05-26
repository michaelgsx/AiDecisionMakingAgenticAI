# `data_acquisition` (v1.1.0)

## 能做什么

根据用户问题与场景（`scenario`），从 **schema catalog**（`schema_catalog_table/column/foreign_key`）中：

1. **先选表**（table index：表名 + 表描述）
2. **再写 SQL**（columns + foreign keys：列描述 + JOIN 边）
3. 执行 **只读 SELECT**（带 TOP 限制），返回用于后续工具（RAG、`llm_answer`）的 **上下文行**

适用：需要“取几行关键上下文数据”来支撑后续解释/决策的问题（case/user/withdrawal/device/review outcome 等）。

不适用：复杂 BI 报表/多语句/写入；此工具强制 read-only 单条 SELECT。

## 主要流程

1. **输入**：`question` + `scenario`（可选 `tableNames` 做候选表覆盖）
2. **Phase 1（选表）**：把候选表的“表名+表描述”拼成 table index，提示 LLM 输出 JSON：`{"tables":[...],"reason":"..."}`  
3. **Phase 2（写 SQL）**：只给 LLM 看“列信息 + FOREIGN KEYS”，要求输出 **SQL only**
4. **执行**：`ReadOnlySqlExecutor.executeSelect(sql, maxRows)` 返回 `rows`

## Prompt 示例

### Phase 1：选表（输出 JSON）

系统指令（节选，真实实现见 `DataAcquisitionPlannerService`）：

```text
You pick database tables needed to answer a risk/QA question.
You receive ONLY a table index (name + description).
Output ONLY JSON: {"tables":["table_a"],"reason":"one sentence"}.
Rules: at least one table; at most 6 tables.
```

用户内容形态：

```text
Table index:
- risk_features (dbo): One row per risk case...
- risk_decisions (dbo): Audit trail...
- risk_ingest_records (dbo): Cases ingested...

User question:
Should we freeze this $15k withdrawal? User user-demo-001 had a new device yesterday.
```

### Phase 2：生成 SQL（输出 SQL only）

系统指令（节选）：

```text
Use ONLY tables, columns, and FOREIGN KEYS from the schema detail below.
Join tables using the documented foreign keys when multiple tables are needed.
Output ONLY the SQL.
Rules: single SELECT; no semicolons; use TOP N or less; read-only.
```

## 请求（execute）示例

Endpoint：`POST /agent/tools/data_acquisition/1.1.0/execute`

```json
{
  "scenario": "withdrawal_review",
  "question": "Should we freeze this $15k withdrawal? User user-demo-001 had a new device yesterday.",
  "maxRows": 50
}
```

可选覆盖候选表（跳过 scenario 默认映射）：

```json
{
  "scenario": "qa",
  "question": "Show me the latest decisions for user-demo-001",
  "maxRows": 20,
  "tableNames": ["risk_features", "risk_decisions", "risk_ingest_records"]
}
```

## 返回示例

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

