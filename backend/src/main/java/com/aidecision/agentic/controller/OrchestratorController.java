package com.aidecision.agentic.controller;

import com.aidecision.agentic.dto.AskRequest;
import com.aidecision.agentic.dto.AskResponse;
import com.aidecision.agentic.dto.HumanResponseRequest;
import com.aidecision.agentic.dto.RunStatusResponse;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.orchestrator.OrchestratorEngine;
import com.aidecision.agentic.service.OrchestratorQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/agent")
public class OrchestratorController {

    private final OrchestratorEngine engine;
    private final OrchestratorQueryService query;

    public OrchestratorController(OrchestratorEngine engine, OrchestratorQueryService query) {
        this.engine = engine;
        this.query = query;
    }

    /** Frontend submits a question; poll GET /agent/runs/{runId} until COMPLETED or FAILED. */
    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        UUID conversationId = parseUuid(request.conversationId());
        OrchestratorRun run = engine.submitQuestion(request.question(), conversationId, request.userId());
        return new AskResponse(
                run.getRunId().toString(),
                run.getStatus(),
                "/agent/runs/" + run.getRunId());
    }

    @GetMapping("/runs/{runId}")
    public RunStatusResponse getRun(@PathVariable String runId) {
        return query.getRun(UUID.fromString(runId));
    }

    @PostMapping("/runs/{runId}/resume")
    public RunStatusResponse resume(@PathVariable String runId) {
        return query.resume(UUID.fromString(runId));
    }

    /** User accepts or rejects a human_in_the_loop proposal; run continues asynchronously. */
    @PostMapping("/runs/{runId}/human-response")
    public RunStatusResponse humanResponse(
            @PathVariable String runId,
            @Valid @RequestBody HumanResponseRequest body) {
        return query.submitHumanResponse(
                UUID.fromString(runId),
                UUID.fromString(body.requestId()),
                body.decision(),
                body.comment());
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return UUID.fromString(raw.trim());
    }
}
