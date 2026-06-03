package com.aidecision.agentic.orchestrator;

public enum AsyncChatPhase {
    PLANNING,
    EXECUTING,
    LLM_ANSWERING,
    DONE,
    FAILED
}
