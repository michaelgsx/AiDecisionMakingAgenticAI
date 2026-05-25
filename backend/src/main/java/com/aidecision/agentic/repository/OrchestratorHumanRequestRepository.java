package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.OrchestratorHumanRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrchestratorHumanRequestRepository extends JpaRepository<OrchestratorHumanRequest, UUID> {
    List<OrchestratorHumanRequest> findByRunIdAndStatusOrderByCreatedAtAsc(UUID runId, String status);

    Optional<OrchestratorHumanRequest> findByStepId(UUID stepId);

    Optional<OrchestratorHumanRequest> findByRequestId(UUID requestId);
}
