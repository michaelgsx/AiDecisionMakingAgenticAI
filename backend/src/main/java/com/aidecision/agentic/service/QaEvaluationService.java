package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.EvaluationDto;
import com.aidecision.agentic.dto.EvaluationListResponse;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.QaEvaluation;
import com.aidecision.agentic.orchestrator.RunStatus;
import com.aidecision.agentic.repository.OrchestratorRunRepository;
import com.aidecision.agentic.repository.QaEvaluationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class QaEvaluationService {

    private final QaEvaluationRepository evaluationRepo;
    private final OrchestratorRunRepository runRepo;

    public QaEvaluationService(QaEvaluationRepository evaluationRepo, OrchestratorRunRepository runRepo) {
        this.evaluationRepo = evaluationRepo;
        this.runRepo = runRepo;
    }

    @Transactional
    public void enqueueCompletedRun(OrchestratorRun run) {
        if (run == null || !RunStatus.COMPLETED.name().equals(run.getStatus())) {
            return;
        }
        if (evaluationRepo.existsByRunId(run.getRunId())) {
            return;
        }
        String answer = run.getAnswerText() == null ? "" : run.getAnswerText();
        QaEvaluation row = new QaEvaluation();
        row.setRunId(run.getRunId());
        row.setQuestion(run.getQuestion());
        row.setAnswerText(answer);
        row.setReviewStatus(QaEvaluation.STATUS_PENDING);
        evaluationRepo.save(row);
    }

    @Transactional
    public EvaluationListResponse list(String statusFilter) {
        List<OrchestratorRun> completed = runRepo.findByStatusOrderByCreatedAtDesc(RunStatus.COMPLETED.name());
        for (OrchestratorRun run : completed) {
            enqueueCompletedRun(run);
        }

        List<QaEvaluation> rows;
        if (statusFilter == null || statusFilter.isBlank() || "all".equalsIgnoreCase(statusFilter)) {
            rows = evaluationRepo.findAllByOrderByCreatedAtDesc();
        } else {
            String normalized = normalizeStatus(statusFilter);
            rows = evaluationRepo.findByReviewStatusOrderByCreatedAtDesc(normalized);
        }

        List<EvaluationDto> items = rows.stream().map(this::toDto).toList();
        return new EvaluationListResponse(items, items.size());
    }

    @Transactional
    public EvaluationDto review(UUID evaluationId, String decision, String reviewerId, String comment) {
        QaEvaluation row = evaluationRepo.findById(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown evaluationId: " + evaluationId));

        if (!QaEvaluation.STATUS_PENDING.equals(row.getReviewStatus())) {
            throw new IllegalArgumentException("Evaluation already reviewed");
        }

        String d = decision.trim().toLowerCase();
        row.setReviewStatus("accept".equals(d) ? QaEvaluation.STATUS_ACCEPTED : QaEvaluation.STATUS_REJECTED);
        row.setReviewerId(blankToNull(reviewerId));
        row.setComment(blankToNull(comment));
        row.setReviewedAt(Instant.now());
        evaluationRepo.save(row);
        return toDto(row);
    }

    private EvaluationDto toDto(QaEvaluation row) {
        return new EvaluationDto(
                row.getEvaluationId().toString(),
                row.getRunId().toString(),
                row.getQuestion(),
                row.getAnswerText(),
                row.getReviewStatus(),
                row.getReviewerId(),
                row.getComment(),
                row.getCreatedAt().toString(),
                row.getReviewedAt() == null ? null : row.getReviewedAt().toString()
        );
    }

    private static String normalizeStatus(String raw) {
        return switch (raw.trim().toUpperCase()) {
            case "PENDING", "ACCEPTED", "REJECTED" -> raw.trim().toUpperCase();
            default -> throw new IllegalArgumentException(
                    "status must be pending, accepted, rejected, or all");
        };
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }
}
