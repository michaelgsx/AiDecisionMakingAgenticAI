package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.OrchestratorTool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrchestratorToolRepository extends JpaRepository<OrchestratorTool, String> {

    List<OrchestratorTool> findByEnabledTrueOrderByToolNameAsc();
}
