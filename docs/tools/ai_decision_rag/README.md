# `ai_decision_rag` (v1.1.0)

## 能做什么

把一段 case 文本（以及可选 metadata）发给 **AiDecisionMakingBackend** 的 hybrid RAG assess 接口，
返回相似案例命中（hits）与模型建议标签（`aiLabel`: passed/rejected/frozen）及原因。

适用：需要“找类似历史案例 + 给出建议标签”的风控决策辅助。

## 主要流程

1. **输入**：`text`（或 `query`）+ 可选 `metadata`（JSON string）
2. **调用下游**：AiDecisionMakingBackend `/rag/assess`（实现见 `AiDecisionRagSimilarityTool`）
3. **输出**：`aiLabel` + `aiReason` + `hits`（供 workflow 分支与 `llm_answer` 汇总）

## Prompt/行为说明

此 tool 本身不直接构造 LLM prompt（而是调用下游 RAG 服务，由下游决定是否使用 LLM 生成 `aiReason`）。

## 请求（execute）示例

Endpoint：`POST /agent/tools/ai_decision_rag/1.1.0/execute`

```json
{
  "text": "Large withdrawal shortly after device change; user-demo-001 new device yesterday.",
  "metadata": "{\"user_id\":\"user-demo-001\",\"scenario\":\"withdrawal_spike\",\"transaction_id\":\"txn-demo-101\"}"
}
```

也可以用 `query` 作为别名：

```json
{
  "query": "Should we freeze this $15k withdrawal for user-demo-001?"
}
```

## 返回示例

```json
{
  "query": "Should we freeze this $15k withdrawal for user-demo-001?",
  "risk": "high",
  "searchReason": "Matched similar frozen withdrawals after new device + geo change.",
  "aiLabel": "frozen",
  "aiReason": "Recommendation: freeze pending verification due to new device + high amount...",
  "hits": [
    { "recordId": "rec-001", "score": 0.83, "snippet": "Large withdrawal after device change...", "reviewOutcome": "frozen", "scenario": "withdrawal_spike" }
  ],
  "summary": "ai_decision_rag: 1 hit(s), label=frozen",
  "source": "AiDecisionMakingBackend"
}
```

