package com.aidecision.agentic.controller;

import com.aidecision.agentic.dto.AskRequest;
import com.aidecision.agentic.dto.ChatRequest;
import com.aidecision.agentic.dto.ChatResponse;
import com.aidecision.agentic.dto.RunStatusResponse;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.orchestrator.OrchestratorEngine;
import com.aidecision.agentic.orchestrator.RunStatus;
import com.aidecision.agentic.service.OrchestratorQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Legacy sync chat — blocks until orchestrator completes. Prefer /agent/ask + polling.
 */
@RestController
@RequestMapping("/agent")
public class AgentChatController {

    private final OrchestratorEngine engine;
    private final OrchestratorQueryService query;

    public AgentChatController(OrchestratorEngine engine, OrchestratorQueryService query) {
        this.engine = engine;
        this.query = query;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) throws InterruptedException {
        UUID conversationId = request.conversationId() != null && !request.conversationId().isBlank()
                ? UUID.fromString(request.conversationId().trim()) : null;
        OrchestratorRun run = engine.submitQuestion(request.message(), conversationId, request.userId());

        RunStatusResponse status;
        int attempts = 0;
        do {
            if (attempts > 0) {
                Thread.sleep(500);
            }
            status = query.getRun(run.getRunId());
            attempts++;
        } while ((isActive(status.status()) || status.waitingForHuman()) && attempts < 120);

        return new ChatResponse(
                run.getRunId().toString(),
                run.getRunId().toString(),
                status.answer() != null ? status.answer() : "(no answer — poll /agent/runs/" + run.getRunId() + ")",
                "orchestrator",
                !RunStatus.COMPLETED.name().equals(status.status()));
    }

    private static boolean isActive(String status) {
        return RunStatus.PENDING.name().equals(status) || RunStatus.RUNNING.name().equals(status);
    }
}
