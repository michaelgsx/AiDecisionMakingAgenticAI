package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.AsyncChatStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AsyncChatStatusRepository extends JpaRepository<AsyncChatStatus, UUID> {

    Optional<AsyncChatStatus> findByRunId(UUID runId);
}
