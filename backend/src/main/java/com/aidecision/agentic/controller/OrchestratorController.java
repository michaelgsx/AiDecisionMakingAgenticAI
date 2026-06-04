package com.aidecision.agentic.controller;

import com.aidecision.agentic.dto.AskRequest;
import com.aidecision.agentic.dto.AskResponse;
import com.aidecision.agentic.dto.AsyncChatPollResponse;
import com.aidecision.agentic.dto.AsyncChatSubmitResponse;
import com.aidecision.agentic.dto.AsyncToolFeedbackRequest;
import com.aidecision.agentic.dto.AsyncToolPollRequest;
import com.aidecision.agentic.dto.ExecuteRequest;
import com.aidecision.agentic.dto.ExecuteResponse;
import com.aidecision.agentic.dto.HumanResponseRequest;
import com.aidecision.agentic.dto.RunStatusResponse;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.orchestrator.OrchestratorEngine;
import com.aidecision.agentic.service.AsyncChatService;
import com.aidecision.agentic.service.AsyncChatStatusService;
import com.aidecision.agentic.service.OrchestratorExecuteService;
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
    private final OrchestratorExecuteService executeService;
    private final AsyncChatService asyncChatService;
    private final AsyncChatStatusService asyncChatStatus;

    public OrchestratorController(
            OrchestratorEngine engine,
            OrchestratorQueryService query,
            OrchestratorExecuteService executeService,
            AsyncChatService asyncChatService,
            AsyncChatStatusService asyncChatStatus) {
        this.engine = engine;
        this.query = query;
        this.executeService = executeService;
        this.asyncChatService = asyncChatService;
        this.asyncChatStatus = asyncChatStatus;
    }

    /** Submit question; poll {@code GET /agent/runs/{runId}} until complete or async pending. */
    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        UUID conversationId = parseUuid(request.conversationId());
        OrchestratorRun run = engine.submitQuestion(request.question(), conversationId, request.userId());
        asyncChatStatus.ensureForRun(run.getRunId(), request.question(), conversationId, request.userId());
        return new AskResponse(
                run.getRunId().toString(),
                run.getStatus(),
                "/agent/runs/" + run.getRunId());
    }

    /**
     * Run workflow inline: returns final answer when all tools are SYNC; otherwise returns
     * {@code pendingAsync} with requestId, stepKey, toolName/version, and feedback/poll paths.
     */
    @PostMapping("/execute")
    public ExecuteResponse execute(@Valid @RequestBody ExecuteRequest request) throws InterruptedException {
        return executeService.execute(request);
    }

    /** Submit question for async processing; poll {@code GET /agent/async-chat/{requestId}} until DONE or FAILED. */
    @PostMapping("/async-chat")
    public AsyncChatSubmitResponse asyncChat(@Valid @RequestBody ExecuteRequest request) {
        return asyncChatService.submit(request);
    }

    @GetMapping("/async-chat/{requestId}")
    public AsyncChatPollResponse asyncChatPoll(@PathVariable String requestId) {
        return asyncChatService.poll(UUID.fromString(requestId));
    }

    @GetMapping("/runs/{runId}")
    public RunStatusResponse getRun(@PathVariable String runId) {
        return query.getRun(UUID.fromString(runId));
    }

    @PostMapping("/runs/{runId}/resume")
    public RunStatusResponse resume(@PathVariable String runId) {
        return query.resume(UUID.fromString(runId));
    }

    /** Feedback for INPUT_REQUIRED async tools (e.g. human_in_the_loop accept/reject). */
    @PostMapping("/runs/{runId}/feedback")
    public RunStatusResponse feedback(
            @PathVariable String runId,
            @Valid @RequestBody AsyncToolFeedbackRequest body) {
        return query.submitFeedback(UUID.fromString(runId), body);
    }

    /** Poll-only async tools (no user input). */
    @PostMapping("/runs/{runId}/poll")
    public RunStatusResponse poll(
            @PathVariable String runId,
            @Valid @RequestBody AsyncToolPollRequest body) {
        return executeService.pollAsyncStep(UUID.fromString(runId), body);
    }

    /** @deprecated Prefer {@link #feedback}; kept for backward compatibility. */
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
