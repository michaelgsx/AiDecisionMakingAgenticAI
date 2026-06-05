# Tools documentation

This directory has **one README per tool**. Each file answers:

- **What the tool does** (good fit vs. poor fit)
- **Main flow** (input → key steps → output)
- **Prompt examples** (system instructions / user message shape; two-phase or async where relevant)
- **Request / response examples** (aligned with `/agent/tools/{tool}/{version}` JSON schemas)

## Built-in tools

| Tool | Version | Summary |
|------|---------|---------|
| [`data_acquisition`](./data_acquisition/README.md) | `1.1.0` | Two-phase table selection + read-only SQL → context rows |
| [`ai_decision_rag`](./ai_decision_rag/README.md) | `1.1.0` | AiDecision RAG assess for similar cases |
| [`similarity_retrieval`](./similarity_retrieval/README.md) | `1.1.0` | Legacy alias → delegates to `ai_decision_rag` |
| [`natural_language_to_sql`](./natural_language_to_sql/README.md) | `1.1.0` | Natural language → read-only SQL (analytics / aggregates) |
| [`human_in_the_loop`](./human_in_the_loop/README.md) | `1.1.0` | **ASYNC** human approval (accept / reject + comment) |
| [`llm_answer`](./llm_answer/README.md) | `1.1.0` | Synthesize prior step outputs into the final answer |

## HTTP entry points

Every tool exposes the same controller shape:

- `POST /agent/tools/{toolName}/{version}/execute`
- (ASYNC tools) `POST /agent/tools/{toolName}/{version}/poll`
- (where supported) `POST /agent/tools/{toolName}/{version}/cancel`

In production, prefer the orchestrator workflow APIs (`/agent/execute`, `/agent/runs/{id}/feedback`, `/agent/runs/{id}/poll`). Standalone tool `execute` is mainly for debugging and integration tests.

## Adaptive retry

Step failures are retried according to each tool’s `max_retry` in `orchestrator_tool`. The orchestrator classifies errors before retrying — for example, **context too large** triggers upstream output summarization; **database connection / permission** errors fail immediately. See the root [README](../../README.md#orchestrator-capabilities) (**Adaptive retry**).
