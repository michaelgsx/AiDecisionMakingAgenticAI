# `human_in_the_loop` (v1.1.0)

## What it does

An **ASYNC** tool that creates a human approval request in the workflow. The QA UI user chooses **accept** or **reject** (optional comment).

**Typical use:** When `ai_decision_rag.aiLabel = frozen` or the model recommendation is disputed, pause before the final `llm_answer` step for human sign-off.

## Main flow (ASYNC)

1. **execute:** Inserts `orchestrator_human_request`, returns `pendingAsync` (`INPUT_REQUIRED`)
2. **User submits feedback in the UI:** `POST /agent/runs/{runId}/feedback` (or legacy `human-response`)
3. **poll:** The workflow worker polls the step, reads the decision, then runs downstream steps

## Execute request example

Endpoint: `POST /agent/tools/human_in_the_loop/1.1.0/execute`

```json
{
  "prompt": "Is freezing this withdrawal acceptable?",
  "proposal": "Propose: freeze $15k withdrawal for user-demo-001 due to new device + similar frozen cases."
}
```

`proposal` may be omitted (defaults to concatenated prior step outputs).

## Response example (pending)

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

## Response example (after user responds)

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
