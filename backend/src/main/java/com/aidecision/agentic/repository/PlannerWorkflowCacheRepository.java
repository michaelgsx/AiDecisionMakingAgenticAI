package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.PlannerWorkflowCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlannerWorkflowCacheRepository extends JpaRepository<PlannerWorkflowCache, UUID> {

    Optional<PlannerWorkflowCache> findByQuestionHash(String questionHash);
}
