package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.EvaluationDto;
import com.aidecision.agentic.dto.EvaluationListResponse;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.entity.QaEvaluation;
import com.aidecision.agentic.evaluation.EvaluationConfidenceExtractor;
import com.aidecision.agentic.orchestrator.RunStatus;
import com.aidecision.agentic.orchestrator.StepStatus;
import com.aidecision.agentic.repository.OrchestratorRunRepository;
import com.aidecision.agentic.repository.OrchestratorStepRepository;
import com.aidecision.agentic.repository.QaEvaluationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class QaEvaluationService {

    private final QaEvaluationRepository evaluationRepo;
    private final OrchestratorRunRepository runRepo;
    private final OrchestratorStepRepository stepRepo;
    private final ObjectMapper mapper;

    public QaEvaluationService(
            QaEvaluationRepository evaluationRepo,
            OrchestratorRunRepository runRepo,
            OrchestratorStepRepository stepRepo,
            ObjectMapper mapper) {
        this.evaluationRepo = evaluationRepo;
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.mapper = mapper;
    }

    @Transactional
    public void enqueueCompletedRun(OrchestratorRun run) {
        if (run == null || !RunStatus.COMPLETED.name().equals(run.getStatus())) {
            return;
        }
        enqueueRunLevel(run);
        List<OrchestratorStep> steps = stepRepo.findByRunIdOrderByCreatedAtAsc(run.getRunId());
        for (OrchestratorStep step : steps) {
            if (StepStatus.COMPLETED.name().equals(step.getStatus())) {
                enqueueStepLevel(run, step);
            }
        }
    }

    private void enqueueRunLevel(OrchestratorRun run) {
        if (evaluationRepo.existsByRunIdAndStepKey(run.getRunId(), QaEvaluation.RUN_STEP_KEY)) {
            return;
        }
        String answer = run.getAnswerText() == null ? "" : run.getAnswerText();
        double confidence = confidenceFromRunAnswer(run, answer);
        QaEvaluation row = baseRow(run);
        row.setEvaluationScope(QaEvaluation.SCOPE_RUN);
        row.setStepKey(QaEvaluation.RUN_STEP_KEY);
        row.setAnswerText(answer);
        row.setConfidence(confidence);
        evaluationRepo.save(row);
    }

    private void enqueueStepLevel(OrchestratorRun run, OrchestratorStep step) {
        if (evaluationRepo.existsByRunIdAndStepKey(run.getRunId(), step.getStepKey())) {
            return;
        }
        String output = step.getOutputJson() == null ? "" : step.getOutputJson();
        QaEvaluation row = baseRow(run);
        row.setEvaluationScope(QaEvaluation.SCOPE_STEP);
        row.setStepKey(step.getStepKey());
        row.setStepId(step.getStepId());
        row.setToolName(step.getToolName());
        row.setAnswerText(formatStepReviewText(step, output));
        row.setConfidence(EvaluationConfidenceExtractor.extract(output, step.getToolName(), mapper));
        evaluationRepo.save(row);
    }

    private QaEvaluation baseRow(OrchestratorRun run) {
        QaEvaluation row = new QaEvaluation();
        row.setRunId(run.getRunId());
        row.setQuestion(run.getQuestion());
        row.setReviewStatus(QaEvaluation.STATUS_PENDING);
        return row;
    }

    private double confidenceFromRunAnswer(OrchestratorRun run, String answerText) {
        List<OrchestratorStep> steps = stepRepo.findByRunIdOrderByCreatedAtAsc(run.getRunId());
        for (int i = steps.size() - 1; i >= 0; i--) {
            OrchestratorStep step = steps.get(i);
            if ("llm_answer".equals(step.getToolName()) && step.getOutputJson() != null) {
                return EvaluationConfidenceExtractor.extract(
                        step.getOutputJson(), step.getToolName(), mapper);
            }
        }
        return EvaluationConfidenceExtractor.extract(answerText, "llm_answer", mapper);
    }

    static String formatStepReviewText(OrchestratorStep step, String outputJson) {
        String header = "[" + step.getStepKey() + " / " + step.getToolName() + "]";
        if (outputJson.isBlank()) {
            return header + "\n(no output)";
        }
        int max = 4000;
        if (outputJson.length() <= max) {
            return header + "\n" + outputJson;
        }
        return header + "\n" + outputJson.substring(0, max) + "\n… [truncated]";
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
                row.getEvaluationScope(),
                QaEvaluation.RUN_STEP_KEY.equals(row.getStepKey()) ? null : row.getStepKey(),
                row.getStepId() == null ? null : row.getStepId().toString(),
                row.getToolName(),
                row.getConfidence(),
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
