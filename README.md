# AI Decision Making — Agentic AI (Backend)

Spring Boot **orchestrator + tool registry** API for risk-control Q&A. Deployed to App Service **`ai-rag-agentic-ai`**. Azure OpenAI chat deployment **`ai-rag-agentic-ai`** on resource **`ai-reg-embedding`**.

Companion UI: **[AiDecisionMakingQAFront](https://github.com/michaelgsx/AiDecisionMakingQAFront)** (SWA **`ai-rag-agentic-qa`**).

> **Synthetic data:** Schema catalog text, seed risk rows, and demo evaluations are AI-generated for development only.

UI screenshots live in **[AiDecisionMakingQAFront](https://github.com/michaelgsx/AiDecisionMakingQAFront)** README (Chat and Evaluation tabs).

## What it does

1. **Accept** a natural-language question (`POST /agent/ask`).
2. **Plan** a workflow DAG with Azure OpenAI (or use a default DAG).
3. **Validate** the DAG (known tools, no cycles, step limit).
4. **Execute** tools asynchronously via a background worker (~2s poll).
5. **Persist** run + step state in Azure SQL for polling and resume.
6. **Enqueue** completed Q&A for the human **evaluation** queue.

## Architecture

```text
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  QA Front   │────▶│ OrchestratorEngine│────▶│ Tool executors  │
│  (SWA)      │     │ Planner / Executor│     │ (Spring beans)  │
└─────────────┘     └────────┬─────────┘     └────────┬────────┘
                           │                         │
                           ▼                         ▼
                    orchestrator_*              AiDecision RAG API
                    qa_evaluation               Azure SQL (read-only NL2SQL)
                    schema_catalog_*            Human-in-the-loop (async)
```

Deep dive: [`.ai/12-orchestrator-architecture.md`](./.ai/12-orchestrator-architecture.md)

## LLM workflow planning (examples)

When `POST /agent/ask` is received, `WorkflowPlannerService` builds a **tool catalog** from `orchestrator_tool`, then calls **Azure OpenAI Chat Completions** (`WorkflowPlannerService.callPlannerLlm`). The model must return **JSON only** (`response_format: json_object`). That JSON is validated (known tools, acyclic DAG, ≤ `app.orchestrator.max-steps-per-workflow` steps) and stored on `orchestrator_run.workflow_json`.

Implementation: `backend/src/main/java/com/aidecision/agentic/orchestrator/WorkflowPlannerService.java`

### Example user question

```text
Should we freeze this $15k withdrawal? User user-demo-001 had a new device yesterday.
```

### Example planner request (Azure OpenAI)

`POST {AZURE_OPENAI_ENDPOINT}/openai/deployments/ai-rag-agentic-ai/chat/completions?api-version=2024-02-01`

```json
{
  "temperature": 0.1,
  "max_tokens": 4096,
  "response_format": { "type": "json_object" },
  "messages": [
    {
      "role": "system",
      "content": "You are a workflow planner for a risk-control agent. Output ONLY valid JSON (no markdown):\n{\"steps\":[{\"id\":\"s1\",\"tool\":\"<tool_name>\",\"dependsOn\":[],\"params\":{},\"maxTimeMs\":30000,\"timeoutMs\":120000}]}\nRules: use only tools from the catalog; no cycles; max 20 steps; last step should use llm_answer when possible.\nUse ai_decision_rag for similar-case search; natural_language_to_sql for analytics questions;\nhuman_in_the_loop before llm_answer when a proposal needs user sign-off (ASYNC — user approves via API)."
    },
    {
      "role": "user",
      "content": "Question:\nShould we freeze this $15k withdrawal? User user-demo-001 had a new device yesterday.\n\nTool catalog:\n- data_acquisition v1.0.0 [DATA_ACQUISITION, SYNC]: Fetch risk context / features for the current question.\n  requestSchema: {\"type\":\"object\",\"properties\":{\"scenario\":{\"type\":\"string\"}}}\n  responseSchema: {\"type\":\"object\",\"properties\":{\"features\":{\"type\":\"object\"}}}\n- ai_decision_rag v1.0.0 [SIMILARITY_RETRIEVAL, SYNC]: AiDecisionMakingBackend hybrid RAG assess for similar cases.\n  requestSchema: {\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"},\"metadata\":{\"type\":\"string\"}}}\n  responseSchema: {\"type\":\"object\",\"properties\":{\"hits\":{\"type\":\"array\"},\"aiLabel\":{\"type\":\"string\"},\"summary\":{\"type\":\"string\"}}}\n- natural_language_to_sql v1.0.0 [AGGREGATE, SYNC]: Natural language → read-only SQL using schema_catalog descriptions.\n  requestSchema: {\"type\":\"object\",\"properties\":{\"question\":{\"type\":\"string\"},\"maxRows\":{\"type\":\"integer\"}}}\n  responseSchema: {\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\"},\"rows\":{\"type\":\"array\"},\"rowCount\":{\"type\":\"integer\"}}}\n- human_in_the_loop v1.0.0 [VALIDATION, ASYNC]: Async user approval: is the proposed solution acceptable?\n  requestSchema: {\"type\":\"object\",\"properties\":{\"proposal\":{\"type\":\"string\"},\"prompt\":{\"type\":\"string\"}}}\n  responseSchema: {\"type\":\"object\",\"properties\":{\"decision\":{\"type\":\"string\"},\"accepted\":{\"type\":\"boolean\"}}}\n- llm_answer v1.0.0 [LLM_REASONING, SYNC]: Synthesize final answer from prior workflow step outputs.\n  requestSchema: {\"type\":\"object\",\"properties\":{}}\n  responseSchema: {\"type\":\"object\",\"properties\":{\"answer\":{\"type\":\"string\"}}}\n"
    }
  ]
}
```

The **user** message is always:

```text
Question:
<user question from POST /agent/ask>

Tool catalog:
<one line per row in orchestrator_tool: name, type, mode, description, request/response JSON schemas>
```

### Example LLM response (assistant `content`)

Illustrative JSON the planner expects (field names must match; no markdown fences):

```json
{
  "steps": [
    {
      "id": "s1",
      "tool": "data_acquisition",
      "dependsOn": [],
      "params": { "scenario": "withdrawal_review" },
      "maxTimeMs": 30000,
      "timeoutMs": 120000
    },
    {
      "id": "s2",
      "tool": "ai_decision_rag",
      "dependsOn": ["s1"],
      "params": {
        "text": "Should we freeze this $15k withdrawal? User user-demo-001 had a new device yesterday.",
        "metadata": "{\"user_id\":\"user-demo-001\",\"scenario\":\"withdrawal_spike\"}"
      },
      "maxTimeMs": 30000,
      "timeoutMs": 120000
    },
    {
      "id": "s3",
      "tool": "human_in_the_loop",
      "dependsOn": ["s1", "s2"],
      "params": {
        "prompt": "Is this freeze recommendation acceptable?",
        "proposal": "Recommend freeze pending review based on similar cases and new device."
      },
      "maxTimeMs": 30000,
      "timeoutMs": 300000
    },
    {
      "id": "s4",
      "tool": "llm_answer",
      "dependsOn": ["s1", "s2", "s3"],
      "params": {},
      "maxTimeMs": 30000,
      "timeoutMs": 120000
    }
  ]
}
```

### Persisted on the run (`orchestrator_run.workflow_json`)

After planning, the same structure is saved (serialized `WorkflowDag`):

```json
{
  "steps": [
    { "id": "s1", "tool": "data_acquisition", "dependsOn": [], "params": { "scenario": "withdrawal_review" }, "maxTimeMs": 30000, "timeoutMs": 120000 },
    { "id": "s2", "tool": "ai_decision_rag", "dependsOn": ["s1"], "params": { "text": "Should we freeze...", "metadata": "{\"user_id\":\"user-demo-001\"}" }, "maxTimeMs": 30000, "timeoutMs": 120000 },
    { "id": "s3", "tool": "human_in_the_loop", "dependsOn": ["s1", "s2"], "params": { "prompt": "Is this freeze recommendation acceptable?", "proposal": "Recommend freeze..." }, "maxTimeMs": 30000, "timeoutMs": 300000 },
    { "id": "s4", "tool": "llm_answer", "dependsOn": ["s1", "s2", "s3"], "params": {}, "maxTimeMs": 30000, "timeoutMs": 120000 }
  ]
}
```

Each step becomes a row in `orchestrator_step` (`step_key` = `id`, `tool_name` = `tool`, `depends_on_json`, `input_json` = `params`).

### Analytics-style question (NL2SQL in the DAG)

**Question:** `How many ingest cases were rejected for login_anomaly last month?`

**Example LLM workflow:**

```json
{
  "steps": [
    {
      "id": "s1",
      "tool": "natural_language_to_sql",
      "dependsOn": [],
      "params": { "question": "How many ingest cases were rejected for login_anomaly last month?", "maxRows": 100 },
      "maxTimeMs": 30000,
      "timeoutMs": 120000
    },
    {
      "id": "s2",
      "tool": "llm_answer",
      "dependsOn": ["s1"],
      "params": {},
      "maxTimeMs": 30000,
      "timeoutMs": 120000
    }
  ]
}
```

### Fallback when OpenAI is unavailable

If chat is not configured or planning fails validation, a fixed default DAG is used:

```json
{
  "steps": [
    { "id": "s1", "tool": "data_acquisition", "dependsOn": [], "params": { "scenario": "qa" }, "maxTimeMs": 30000, "timeoutMs": 120000 },
    { "id": "s2", "tool": "ai_decision_rag", "dependsOn": ["s1"], "params": { "text": "<original question>" }, "maxTimeMs": 30000, "timeoutMs": 120000 },
    { "id": "s3", "tool": "llm_answer", "dependsOn": ["s1", "s2"], "params": {}, "maxTimeMs": 30000, "timeoutMs": 120000 }
  ]
}
```

## Tool registration at startup

On each application start, `ToolRegistryStartup` inserts built-in tools into `orchestrator_tool` **only when the tool name is not already present** (existing rows are left unchanged). Runtime executors are Spring beans implementing `AgentTool`; metadata lives in `BuiltinToolCatalog`.

## Registered tools

| Tool | Mode | Description |
|------|------|-------------|
| `data_acquisition` | SYNC | Load risk context / features |
| `ai_decision_rag` | SYNC | Hybrid search via **AiDecisionMakingBackend** `POST /rag/assess` |
| `similarity_retrieval` | SYNC | Legacy alias → `ai_decision_rag` |
| `natural_language_to_sql` | SYNC | NL → read-only SQL using `schema_catalog_*` |
| `human_in_the_loop` | ASYNC | User accept/reject before workflow continues |
| `llm_answer` | SYNC | Final answer from prior step outputs |

Configure RAG:

```env
APP_RAG_API_BASE_URL=https://<your-backend-app>.azurewebsites.net
APP_RAG_API_OPS_TOKEN=<optional>
```

## API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/agent/ask` | Submit question → `{ runId, status, pollPath }` |
| GET | `/agent/runs/{runId}` | Poll status, steps, `pendingApprovals` |
| POST | `/agent/runs/{runId}/resume` | Resume a **FAILED** run |
| POST | `/agent/runs/{runId}/human-response` | Answer `human_in_the_loop` step |
| GET | `/agent/evaluations?status=pending` | Human review queue (`pending` \| `accepted` \| `rejected` \| `all`) |
| POST | `/agent/evaluations/{evaluationId}/review` | `{ decision: accept\|reject, reviewerId?, comment? }` |
| GET | `/agent/tools` | Tool registry (schemas, SYNC/ASYNC) |
| POST | `/agent/tools` | Register tool metadata (executor must exist) |
| POST | `/agent/feedback` | Thumbs `up` / `down` |
| POST | `/agent/chat` | Legacy: block until complete |
| GET | `/health` | SQL connectivity |

Optional auth: set `OPS_TOKEN`; clients send `Authorization: Bearer <token>`. Blank = open (local dev).

## Database

Shared database **`ai-rag-db-1`** (same server as AiDecisionMakingBackend).

### Migrations

```bash
cd db
pip install -r requirements.txt
cp .env.example .env   # or copy AiDecisionMakingBackend/db/.env
python run_migrations.py
```

| Script | Contents |
|--------|----------|
| V1 | `qa_conversation`, `qa_message`, `qa_feedback` |
| V2 | `orchestrator_tool`, `orchestrator_run`, `orchestrator_step` |
| V3 | `qa_feedback.run_id`, nullable `conversation_id` |
| V4 | `schema_catalog_*`, `orchestrator_human_request` |
| V5 | Seed schema catalog (basic) |
| V6 | Upsert orchestrator tools |
| V7 | `qa_evaluation` (post-run human review) |
| V8 | Full catalog descriptions + demo Q&A / risk / evaluation rows |
| V9 | Fix demo UUIDs + missing tools |

### NL2SQL schema catalog

`schema_catalog_table` and `schema_catalog_column` store **table/column descriptions** for the LLM. Extend these rows for production governance; the NL2SQL tool only allows **SELECT** queries.

## Local run

**Java 17** required.

```bash
cd backend
cp .env.example .env
# Reuse SQL + OpenAI from AiDecisionMakingBackend/backend/.env when possible
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS
./mvnw spring-boot:run
```

| URL | Purpose |
|-----|---------|
| http://localhost:8788/health | DB check |
| http://localhost:8788/agent/evaluations?status=pending | Evaluation queue |

Port **8788**. CORS default includes `http://localhost:5174`.

## Build JAR

```bash
cd backend
./mvnw clean package -DskipTests -B
# → backend/target/ai-decision-making-agentic-0.1.0-SNAPSHOT.jar
```

## Azure deploy

Workflow: `.github/workflows/deploy-agentic-appservice.yml` (branch **`main`**).

| Resource | Name |
|----------|------|
| App Service | **ai-rag-agentic-ai** |
| OpenAI deployment | **ai-rag-agentic-ai** |
| SQL database | **ai-rag-db-1** |
| QA Static Web App | **ai-rag-agentic-qa** (other repo) |

**GitHub secret:** `AZURE_CREDENTIALS` (Website Contributor on the App Service).

**App Service → Configuration (typical):**

| Setting | Notes |
|---------|--------|
| `AZURE_OPENAI_ENDPOINT` | e.g. `https://ai-reg-embedding.openai.azure.com` |
| `AZURE_OPENAI_API_KEY` | Key Vault reference recommended |
| `AZURE_OPENAI_CHAT_DEPLOYMENT` | `ai-rag-agentic-ai` |
| `AZURE_SQL_*` | Server, database, user, password |
| `APP_RAG_API_BASE_URL` | AiDecisionMakingBackend URL |
| `CORS_ORIGINS` | `https://<swa-host>.azurestaticapps.net`, `http://localhost:5174` |
| `OPS_TOKEN` | Match QA `VITE_OPS_TOKEN` |
| `WEBSITES_PORT` | `8788` |

Runtime: **Java 17** (Linux).

## Repo layout

```text
backend/          # Spring Boot application (port 8788)
db/               # SQL migrations + run_migrations.py
.ai/              # Architecture notes
```

## Related repos

| Repo | Role |
|------|------|
| **AiDecisionMakingQAFront** | Chat + Evaluation UI |
| AiDecisionMakingBackend | RAG assess, risk ingest tables |
| AiDecisionMakingFrontend | Risk operations console |
| AiDecisionMakingML | Batch ML / feature bins |
