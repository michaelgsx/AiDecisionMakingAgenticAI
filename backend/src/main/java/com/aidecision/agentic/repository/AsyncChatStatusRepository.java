package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.AsyncChatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AsyncChatStatusRepository extends JpaRepository<AsyncChatStatus, UUID> {

    Optional<AsyncChatStatus> findByRunId(UUID runId);

    List<AsyncChatStatus> findByStatusInAndUpdatedAtBeforeAndRunIdIsNotNullOrderByUpdatedAtAsc(
            Collection<String> statuses, Instant cutoff);

    /** Atomically bump updated_at so only one revival thread claims a stale row. */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE AsyncChatStatus s SET s.updatedAt = :now
            WHERE s.requestId = :requestId
              AND s.status IN :statuses
              AND s.updatedAt < :cutoff
            """)
    int claimStale(
            @Param("requestId") UUID requestId,
            @Param("now") Instant now,
            @Param("cutoff") Instant cutoff,
            @Param("statuses") Collection<String> statuses);
}
