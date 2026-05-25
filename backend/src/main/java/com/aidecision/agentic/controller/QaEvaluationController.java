package com.aidecision.agentic.controller;

import com.aidecision.agentic.dto.EvaluationDto;
import com.aidecision.agentic.dto.EvaluationListResponse;
import com.aidecision.agentic.dto.EvaluationReviewRequest;
import com.aidecision.agentic.service.QaEvaluationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/agent/evaluations")
public class QaEvaluationController {

    private final QaEvaluationService evaluations;

    public QaEvaluationController(QaEvaluationService evaluations) {
        this.evaluations = evaluations;
    }

    /** List Q&A pairs for human review (default: PENDING). */
    @GetMapping
    public EvaluationListResponse list(
            @RequestParam(defaultValue = "pending") String status) {
        return evaluations.list(status);
    }

    @PostMapping("/{evaluationId}/review")
    public EvaluationDto review(
            @PathVariable String evaluationId,
            @Valid @RequestBody EvaluationReviewRequest body) {
        return evaluations.review(
                UUID.fromString(evaluationId),
                body.decision(),
                body.reviewerId(),
                body.comment());
    }
}
