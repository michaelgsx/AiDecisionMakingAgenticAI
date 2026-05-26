# `natural_language_to_sql` (v1.1.0)

## 能做什么

把自然语言分析问题转换成 **Microsoft SQL Server 的只读 SELECT**，在 Azure SQL 上执行并返回结果行。

适用：计数/分组/过滤/时间窗口统计等分析类问题（比如“最近 7 天冻结率”）。

不适用：写入、DDL、多语句、存储过程；此工具只允许 read-only 单条 SELECT。

## 主要流程

1. **输入**：`question`（必填）+ `maxRows`（可选）
2. **Prompt**：将 `schema_catalog_table/column` 拼成 catalog text，要求 LLM 输出 SQL only
3. **执行**：`ReadOnlySqlExecutor` 执行 SELECT，返回 `rows` + `rowCount`

## Prompt 示例（SQL only）

系统指令（节选，见 `LlmSqlGenerationService`）：

```text
You write Microsoft SQL Server SELECT-only queries for risk analytics.
Use ONLY tables and columns from the schema catalog below.
Output ONLY the SQL (no markdown fences, no explanation).
Rules: single SELECT; no semicolons; use TOP 100 or less; read-only.
```

## 请求示例

Endpoint：`POST /agent/tools/natural_language_to_sql/1.1.0/execute`

```json
{
  "question": "In the last 7 days, how many cases were frozen vs passed? Group by scenario.",
  "maxRows": 50
}
```

## 返回示例

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

