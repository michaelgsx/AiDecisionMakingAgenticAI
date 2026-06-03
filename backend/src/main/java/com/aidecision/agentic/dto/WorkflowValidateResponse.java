package com.aidecision.agentic.dto;

import java.util.List;

public record WorkflowValidateResponse(
        boolean valid,
        List<String> errors,
        List<List<String>> executionWaves,
        String workflowJson
) {}
