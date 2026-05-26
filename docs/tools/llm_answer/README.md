# `llm_answer` (v1.1.0)

## 能做什么

把 workflow 前序步骤的输出（例如 `data_acquisition.rows`、`ai_decision_rag.aiLabel/hits`、`human_in_the_loop.accepted`）
汇总成最终的 **用户可读回答**。

适用：作为 DAG 最后一步生成最终答复。

## 主要流程

1. **输入**：无必填参数（从 workflow context 读取所有 step outputs）
2. **Prompt**：把已执行步骤输出拼进 user message，让 LLM 生成最终回答文本
3. **输出**：`{ "answer": "..." }`

## 请求示例

Endpoint：`POST /agent/tools/llm_answer/1.1.0/execute`

```json
{}
```

## 返回示例

```json
{
  "answer": "Based on similar frozen cases and the user's new device, we recommend freezing the withdrawal pending verification..."
}
```

