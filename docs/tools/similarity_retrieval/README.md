# `similarity_retrieval` (v1.1.0)

## What it does

**Legacy alias:** Same request and response schema as `ai_decision_rag`; internally delegates to the `ai_decision_rag` implementation.

> New workflows should use `ai_decision_rag` directly. This tool remains for older planner output and historical runs.

## Request / response

- Endpoint: `POST /agent/tools/similarity_retrieval/1.1.0/execute`
- Schema: Identical to `ai_decision_rag` — see [`ai_decision_rag` README](../ai_decision_rag/README.md)
