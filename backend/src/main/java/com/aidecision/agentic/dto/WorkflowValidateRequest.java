package com.aidecision.agentic.dto;

import java.util.List;

public record WorkflowValidateRequest(
        String question,
        String workflowJson
) {}
