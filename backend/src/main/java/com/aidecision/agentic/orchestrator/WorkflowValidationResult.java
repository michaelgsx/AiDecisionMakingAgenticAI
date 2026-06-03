package com.aidecision.agentic.orchestrator;

import java.util.List;

/** Result of structural + executable workflow validation (includes parallel execution waves). */
public record WorkflowValidationResult(
        boolean valid,
        List<String> errors,
        List<List<String>> executionWaves
) {
    public static WorkflowValidationResult ok(List<List<String>> waves) {
        return new WorkflowValidationResult(true, List.of(), waves);
    }

    public static WorkflowValidationResult fail(List<String> errors) {
        return new WorkflowValidationResult(false, errors, List.of());
    }
}
