package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.OrchestratorRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface OrchestratorRunRepository extends JpaRepository<OrchestratorRun, UUID> {

    List<OrchestratorRun> findByStatusInOrderByUpdatedAtAsc(List<String> statuses);

    List<OrchestratorRun> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * Atomically transitions a run's status only if it currently equals {@code expected}.
     * Used to claim a run for processing so concurrent workers (e.g. an old container draining
     * during deploy) cannot plan/execute the same run twice. Returns rows updated (1 = claimed).
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrchestratorRun r set r.status = :next, r.updatedAt = CURRENT_TIMESTAMP "
            + "where r.runId = :runId and r.status = :expected")
    int compareAndSetStatus(@Param("runId") UUID runId,
                            @Param("expected") String expected,
                            @Param("next") String next);
}
