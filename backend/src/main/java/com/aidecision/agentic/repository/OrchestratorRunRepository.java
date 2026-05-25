package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.OrchestratorRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrchestratorRunRepository extends JpaRepository<OrchestratorRun, UUID> {

    List<OrchestratorRun> findByStatusInOrderByUpdatedAtAsc(List<String> statuses);

    List<OrchestratorRun> findByStatusOrderByCreatedAtDesc(String status);
}
