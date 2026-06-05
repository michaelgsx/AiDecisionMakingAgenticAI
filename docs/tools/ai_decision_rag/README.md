# `ai_decision_rag` (v1.1.0)

## What it does

Sends case text (and optional metadata) to **AiDecisionMakingBackend** hybrid RAG assess and returns similar-case **hits** plus a model recommendation label (`aiLabel`: passed / rejected / frozen) and reasoning.

**Good fit:** Risk decision support that needs “find similar historical cases + suggested label.”

## Main flow

1. **Input:** `text` (or `query`) + optional `metadata` (JSON string)
2. **Downstream call:** AiDecisionMakingBackend `POST /rag/assess` (implementation: `AiDecisionRagSimilarityTool`)
3. **Output:** `aiLabel` + `aiReason` + `hits` (for workflow branching and `llm_answer`)

## Prompt / behavior

This tool does not build an LLM prompt itself; it delegates to the RAG service, which may use an LLM for `aiReason` when configured.

## Execute request example

Endpoint: `POST /agent/tools/ai_decision_rag/1.1.0/execute`

```json
{
  "text": "Large withdrawal shortly after device change; user-demo-001 new device yesterday.",
  "metadata": "{\"user_id\":\"user-demo-001\",\"scenario\":\"withdrawal_spike\",\"transaction_id\":\"txn-demo-101\"}"
}
```

`query` is an alias for `text`:

```json
{
  "query": "Should we freeze this $15k withdrawal for user-demo-001?"
}
```

## Response example

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
