# `similarity_retrieval` (v1.1.0)

## 能做什么

**Legacy alias**：参数与返回值与 `ai_decision_rag` 相同，内部直接委托到 `ai_decision_rag` 的实现。

> 新 workflow 推荐直接用 `ai_decision_rag`，保留该 tool 主要为了兼容旧的 planner 输出与历史数据。

## 请求/返回

- Endpoint：`POST /agent/tools/similarity_retrieval/1.1.0/execute`
- Schema：与 `ai_decision_rag` 完全一致（见 [`ai_decision_rag` README](../ai_decision_rag/README.md)）

