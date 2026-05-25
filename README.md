# AI Decision Making — Agentic AI (Backend)

Spring Boot **orchestrator + tools** API for risk-control Q&A. Deployed to App Service **`ai-rag-agentic-ai`**. OpenAI deployment **`ai-rag-agentic-ai`** on `ai-reg-embedding`.

> **Synthetic data:** Demo schemas are AI-generated for illustration only.

## Architecture

Orchestrator receives questions, asks the LLM for an execution **DAG**, validates it (no dead loops), executes **registered tools**, persists workflow + step status in SQL, and supports **resume** on failure. A background worker polls `PENDING` / `RUNNING` runs every 2s.

Details: [`.ai/12-orchestrator-architecture.md`](./.ai/12-orchestrator-architecture.md)

## API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/agent/ask` | Submit question → `{ runId, status, pollPath }` |
| GET | `/agent/runs/{runId}` | Poll workflow status, steps, answer |
| POST | `/agent/runs/{runId}/resume` | Resume failed run from DB checkpoint |
| GET | `/agent/tools` | Tool registry (schemas, SYNC/ASYNC, type) |
| POST | `/agent/feedback` | Thumbs `up` / `down` (`runId` + `messageId`) |
| POST | `/agent/chat` | Legacy blocking wait until complete |
| GET | `/health` | SQL connectivity |

## Azure resources

| Resource | Name |
|----------|------|
| App Service (this API) | **ai-rag-agentic-ai** |
| OpenAI chat deployment | **ai-rag-agentic-ai** |
| SQL database | `ai-rag-db-1` (shared) |
| QA UI (separate repo) | SWA `ai-rag-agentic-qa` |

## Database

```bash
cd db
pip install -r requirements.txt
cp .env.example .env   # or symlink Backend db/.env
python run_migrations.py   # V1, V2 (orchestrator), V3 (feedback link)
```

## Local run

```bash
cd backend
cp .env.example .env
# Copy SQL + OpenAI vars from AiDecisionMakingBackend/backend/.env if needed
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS
./mvnw spring-boot:run
```

Port **8788**. Health: http://localhost:8788/health

## Build JAR

```bash
cd backend
./mvnw clean package -DskipTests -B
# JAR: backend/target/ai-decision-making-agentic-0.1.0-SNAPSHOT.jar
```

## Deploy (GitHub Actions)

Workflow: `.github/workflows/deploy-agentic-appservice.yml`

**Secrets:** `AZURE_CREDENTIALS` (Website Contributor on `ai-rag-agentic-ai`)

**App Service → Configuration** (required after first deploy):

| Setting | Value |
|---------|--------|
| `AZURE_OPENAI_ENDPOINT` | `https://ai-reg-embedding.openai.azure.com` |
| `AZURE_OPENAI_API_KEY` | Key Vault ref or secret |
| `AZURE_OPENAI_CHAT_DEPLOYMENT` | `ai-rag-agentic-ai` |
| `AZURE_SQL_SERVER` | `ai-rag-sql-server.database.windows.net` |
| `AZURE_SQL_DATABASE` | `ai-rag-db-1` |
| `AZURE_SQL_USER` / `AZURE_SQL_PASSWORD` | SQL login |
| `CORS_ORIGINS` | `https://<ai-rag-agentic-qa-host>.azurestaticapps.net` |
| `OPS_TOKEN` | Same token as QA frontend `VITE_OPS_TOKEN` |
| `WEBSITES_PORT` | `8788` (if needed) |

Runtime stack: **Java 17** (Linux).

## Frontend

[AiDecisionMakingQAFront](../AiDecisionMakingQAFront) — set `VITE_AGENT_API_BASE_URL=https://ai-rag-agentic-ai.azurewebsites.net` (or your App Service URL).
