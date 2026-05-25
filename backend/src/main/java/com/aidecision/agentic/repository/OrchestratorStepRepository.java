package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.OrchestratorStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrchestratorStepRepository extends JpaRepository<OrchestratorStep, UUID> {

    List<OrchestratorStep> findByRunIdOrderByCreatedAtAsc(UUID runId);

    List<OrchestratorStep> findByRunIdAndStatusIn(UUID runId, List<String> statuses);
}
