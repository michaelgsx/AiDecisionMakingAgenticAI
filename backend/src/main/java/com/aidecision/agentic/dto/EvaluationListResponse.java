package com.aidecision.agentic.dto;

import java.util.List;

public record EvaluationListResponse(
        List<EvaluationDto> items,
        int total
) {}
