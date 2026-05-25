# 12 — Orchestrator + tools architecture

## Components

| Layer | Responsibility |
|-------|----------------|
| **Orchestrator** | Accept questions, plan DAG, persist workflow, poll status, resume failed runs |
| **Tool registry** | Portal of tools (name, version, schemas, SYNC/ASYNC, type). Built-ins auto-inserted at startup if missing |
| **Workflow planner** | LLM builds execution DAG from question + tool catalog (see README § *LLM workflow planning*) |
| **DAG validator** | No cycles (dead loops), known tools, max step count |
| **Executor** | Runs READY steps when dependencies are COMPLETED |
| **Worker** | Background thread (`@Scheduled`) processes PENDING/RUNNING runs |
| **Feedback** | Thumbs up/down → `qa_feedback` linked by `run_id` |

## API

| Method | Path |
|--------|------|
| POST | `/agent/ask` |
| GET | `/agent/runs/{runId}` | Includes `workflowJson`, `workflowMermaid` |
| GET | `/agent/runs/{runId}/workflow-diagram` | Mermaid DAG for the run |
| POST | `/agent/workflow/diagram` | Mermaid from arbitrary workflow JSON |
| POST | `/agent/runs/{runId}/resume` |
| POST | `/agent/runs/{runId}/human-response` | Accept/reject `human_in_the_loop` proposal |
| GET | `/agent/tools` |
| POST | `/agent/tools` |
| POST | `/agent/feedback` |
| GET | `/agent/evaluations?status=pending` | Human review queue (Q&A list) |
| POST | `/agent/evaluations/{evaluationId}/review` | Accept / reject for further review |
| POST | `/agent/chat` | Legacy sync wait |

Per-tool invoke (version must match `orchestrator_tool.version`, e.g. `1.1.0`):

| Method | Path pattern |
|--------|----------------|
| GET | `/agent/tools/{toolName}/{version}/registry-info` |
| POST | `/agent/tools/{toolName}/{version}/execute` |
| POST | `/agent/tools/{toolName}/{version}/poll` |
| POST | `/agent/tools/{toolName}/{version}/cancel` |

Local DAG viewer: `GET /workflow.html` (static).

## Database

- `orchestrator_tool`, `orchestrator_run`, `orchestrator_step`
- `qa_feedback.run_id` (V3 migration)
- `schema_catalog_table`, `schema_catalog_column` — NL2SQL LLM context (V4/V5)
- `orchestrator_human_request` — async human approval (V4)
- `qa_evaluation` — post-run human accept/reject (V7)
- Migrations: `V1`–`V7`

## Tools

| Tool | Mode | Notes |
|------|------|--------|
| `data_acquisition` | SYNC | schema_catalog → LLM SQL → context rows |
| `ai_decision_rag` | SYNC | `POST {APP_RAG_API_BASE_URL}/rag/assess` |
| `similarity_retrieval` | SYNC | Alias → `ai_decision_rag` |
| `natural_language_to_sql` | SYNC | LLM + `schema_catalog_*`, read-only SELECT |
| `human_in_the_loop` | ASYNC | Poll until user calls `human-response` |
| `llm_answer` | SYNC | Final synthesis |

Async steps stay `RUNNING`; worker calls `AsyncAgentTool.poll()` after user answers.

## Resume

Failed runs store `workflow_json` + per-step status. `POST /agent/runs/{id}/resume` resets failed steps to PENDING and re-executes.
