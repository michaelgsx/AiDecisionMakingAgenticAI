package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.QaEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QaEvaluationRepository extends JpaRepository<QaEvaluation, UUID> {

    boolean existsByRunId(UUID runId);

    boolean existsByRunIdAndStepKey(UUID runId, String stepKey);

    Optional<QaEvaluation> findByRunId(UUID runId);

    List<QaEvaluation> findByReviewStatusOrderByCreatedAtDesc(String reviewStatus);

    List<QaEvaluation> findAllByOrderByCreatedAtDesc();
}
