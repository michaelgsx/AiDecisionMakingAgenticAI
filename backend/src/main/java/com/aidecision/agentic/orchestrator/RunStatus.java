package com.aidecision.agentic.orchestrator;

public enum RunStatus {
    PENDING,
    PLANNING,
    RUNNING,
    /** Blocked on INPUT_REQUIRED async tool (e.g. human_in_the_loop) until feedback. */
    WAITING_ASYNC,
    COMPLETED,
    FAILED,
    CANCELLED
}
