package com.aidecision.agentic.orchestrator;

import java.util.List;

/** Planner determined the registered tools cannot answer the user question. */
public class InsufficientToolsException extends RuntimeException {

    private final List<String> missingTools;

    public InsufficientToolsException(String message, List<String> missingTools) {
        super(message);
        this.missingTools = missingTools == null ? List.of() : List.copyOf(missingTools);
    }

    public List<String> missingTools() {
        return missingTools;
    }
}
