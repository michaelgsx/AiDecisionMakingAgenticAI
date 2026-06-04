package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.OrchestratorStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface OrchestratorStepRepository extends JpaRepository<OrchestratorStep, UUID> {

    List<OrchestratorStep> findByRunIdOrderByCreatedAtAsc(UUID runId);

    List<OrchestratorStep> findByRunIdAndStatusIn(UUID runId, List<String> statuses);

    /**
     * Bulk-deletes all steps for a run and flushes immediately, so a subsequent re-plan can
     * re-insert step keys without colliding with Hibernate's default INSERT-before-DELETE
     * flush ordering on the (run_id, step_key) unique constraint.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OrchestratorStep s where s.runId = :runId")
    int deleteByRunId(@Param("runId") UUID runId);
}
