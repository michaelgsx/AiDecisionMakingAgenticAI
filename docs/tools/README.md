# Tools documentation

这个目录为 **每一个 tool 提供一个 README**，用于回答以下问题：

- **这个 tool 能干什么**（适用场景 / 不适用场景）
- **主要流程**（输入 → 关键步骤 → 输出）
- **Prompt 示例**（系统指令/用户输入形态；必要时说明两阶段/异步）
- **请求/返回值示例**（对齐 `/agent/tools/{tool}/{version}` 的 JSON schema）

## Tool 列表（当前内置）

- [`data_acquisition`](./data_acquisition/README.md)（`1.1.0`）— 两阶段选表 + 生成只读 SQL，取上下文行
- [`ai_decision_rag`](./ai_decision_rag/README.md)（`1.1.0`）— 调用 AiDecision RAG assess 找相似案例
- [`similarity_retrieval`](./similarity_retrieval/README.md)（`1.1.0`）— legacy alias，委托到 `ai_decision_rag`
- [`natural_language_to_sql`](./natural_language_to_sql/README.md)（`1.1.0`）— 自然语言 → 只读 SQL（分析/聚合）
- [`human_in_the_loop`](./human_in_the_loop/README.md)（`1.1.0`）— **ASYNC** 人工确认（accept/reject + comment）
- [`llm_answer`](./llm_answer/README.md)（`1.1.0`）— 汇总前序步骤输出，生成最终回答

## 统一调用入口（HTTP）

每个 tool 都有同构的 controller：

- `POST /agent/tools/{toolName}/{version}/execute`
- （ASYNC 工具）`POST /agent/tools/{toolName}/{version}/poll`
- （如支持）`POST /agent/tools/{toolName}/{version}/cancel`

注意：生产上更常用 orchestrator 的工作流入口（`/agent/execute`、`/agent/runs/{id}/feedback`、`/agent/runs/{id}/poll`），tool 的独立 execute 主要用于调试与集成测试。

