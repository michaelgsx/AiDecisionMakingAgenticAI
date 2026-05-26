# `human_in_the_loop` (v1.1.0)

## 能做什么

一个 **ASYNC** 工具：在 workflow 中发起人工确认请求，让 UI 端用户做 **accept / reject**（可附 comment）。

典型用法：当 `ai_decision_rag.aiLabel = frozen` 或模型建议有争议时，在生成最终回答前请求人工批准。

## 主要流程（ASYNC）

1. **execute**：写入 `orchestrator_human_request`，返回 `pendingAsync`（INPUT_REQUIRED）
2. **用户在前端提交反馈**：`POST /agent/runs/{runId}/feedback`（或 legacy human-response）
3. **poll**：workflow worker 轮询该步骤，拿到决策后继续执行后续步骤

## 请求（execute）示例

Endpoint：`POST /agent/tools/human_in_the_loop/1.1.0/execute`

```json
{
  "prompt": "Is freezing this withdrawal acceptable?",
  "proposal": "Propose: freeze $15k withdrawal for user-demo-001 due to new device + similar frozen cases."
}
```

`proposal` 可省略（会默认拼接前序步骤输出作为 proposal）。

## 返回示例（pending）

```json
{
  "pendingAsync": {
    "kind": "INPUT_REQUIRED",
    "prompt": "Is freezing this withdrawal acceptable?",
    "requestId": "6b7f4c6c-....",
    "runId": "2a2d7d5c-....",
    "stepKey": "s3"
  }
}
```

## 返回示例（已回答后）

```json
{
  "requestId": "6b7f4c6c-....",
  "stepKey": "s3",
  "prompt": "Is freezing this withdrawal acceptable?",
  "proposal": "Propose: freeze ...",
  "status": "ANSWERED",
  "decision": "accept",
  "accepted": true,
  "comment": "OK, proceed",
  "summary": "human_in_the_loop: accepted"
}
```

