# `llm_answer` (v1.1.0)

## What it does

Combines outputs from prior workflow steps (e.g. `data_acquisition.rows`, `ai_decision_rag.aiLabel` / `hits`, `human_in_the_loop.accepted`) into a final **user-facing answer**.

**Good fit:** Last step in the DAG that produces the reply shown to the user.

## Main flow

1. **Input:** No required params (reads all prior step outputs from workflow context)
2. **Prompt:** Prior step outputs are concatenated into the user message; the LLM generates the final answer text
3. **Output:** `{ "answer": "...", "confidence": 0.0-1.0 }` — confidence is required for the human evaluation queue.

If upstream context exceeds the model limit, the orchestrator may summarize prior outputs on adaptive retry before re-invoking this tool.

## Request example

Endpoint: `POST /agent/tools/llm_answer/1.1.0/execute`

```json
{}
```

## Response example

```json
{
  "answer": "Based on similar frozen cases and the user's new device, we recommend freezing the withdrawal pending verification...",
  "confidence": 0.88
}
```
