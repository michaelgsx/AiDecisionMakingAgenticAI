package com.aidecision.agentic.orchestrator;

/** Classified failure modes that drive retry vs immediate run termination. */
public enum StepFailureKind {
    /** Transient or unknown — increment retry and re-queue when budget remains. */
    RETRYABLE,
    /** LLM / tool input exceeded context limits — compress upstream outputs then retry. */
    CONTEXT_TOO_LARGE,
    /** Database unreachable or auth to server failed — do not retry. */
    DATABASE_CONNECTION,
    /** SQL / ACL / object permission denied — do not retry. */
    DATABASE_PERMISSION,
    /** Non-recoverable without changing inputs or code. */
    FATAL
}
