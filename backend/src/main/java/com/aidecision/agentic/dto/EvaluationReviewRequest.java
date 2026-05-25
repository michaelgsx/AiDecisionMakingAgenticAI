package com.aidecision.agentic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EvaluationReviewRequest(
        @NotBlank @Pattern(regexp = "accept|reject", flags = Pattern.Flag.CASE_INSENSITIVE) String decision,
        String reviewerId,
        String comment
) {}
