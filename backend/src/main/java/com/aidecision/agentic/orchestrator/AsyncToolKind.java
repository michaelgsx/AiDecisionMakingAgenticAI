package com.aidecision.agentic.orchestrator;

/** How an ASYNC tool waits for the frontend. */
public enum AsyncToolKind {
    /** User must submit feedback (e.g. human_in_the_loop accept/reject). */
    INPUT_REQUIRED,
    /** Long-running; client polls until the step completes (no user input). */
    POLL_ONLY
}
