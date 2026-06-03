package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.AsyncChatPollResponse;
import com.aidecision.agentic.dto.AsyncChatSubmitResponse;
import com.aidecision.agentic.dto.ExecuteRequest;
import com.aidecision.agentic.entity.AsyncChatStatus;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.orchestrator.AsyncChatPhase;
import com.aidecision.agentic.orchestrator.OrchestratorEngine;
import com.aidecision.agentic.util.AfterCommitTasks;
import com.aidecision.agentic.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AsyncChatService {

    private static final Logger log = LoggerFactory.getLogger(AsyncChatService.class);

    private final AsyncChatStatusService statusService;
    private final AsyncChatProcessor processor;
    private final OrchestratorEngine engine;
    private final AfterCommitTasks afterCommit;

    public AsyncChatService(
            AsyncChatStatusService statusService,
            AsyncChatProcessor processor,
            OrchestratorEngine engine,
            AfterCommitTasks afterCommit) {
        this.statusService = statusService;
        this.processor = processor;
        this.engine = engine;
        this.afterCommit = afterCommit;
    }

    @Transactional
    public AsyncChatSubmitResponse submit(ExecuteRequest request) {
        log.info("Async chat submit question={} userId={} conversationId={}",
                LogSanitizer.question(request.question()),
                LogSanitizer.userId(request.userId()),
                request.conversationId());

        UUID conversationId = parseUuid(request.conversationId());
        AsyncChatStatus status = statusService.create(
                request.question(),
                conversationId,
                request.userId());

        OrchestratorRun run = engine.submitQuestion(request.question(), conversationId, request.userId());
        statusService.linkRun(status.getRequestId(), run.getRunId());

        UUID requestId = status.getRequestId();
        UUID runId = run.getRunId();
        afterCommit.run(() -> processor.processRun(requestId, runId));

        String pollPath = "/agent/async-chat/" + requestId;
        return new AsyncChatSubmitResponse(
                requestId.toString(),
                AsyncChatPhase.PLANNING.name(),
                pollPath);
    }

    @Transactional(readOnly = true)
    public AsyncChatPollResponse poll(UUID requestId) {
        AsyncChatStatus row = statusService.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown requestId: " + requestId));
        return toPollResponse(row);
    }

    private static AsyncChatPollResponse toPollResponse(AsyncChatStatus row) {
        return new AsyncChatPollResponse(
                row.getRequestId().toString(),
                row.getStatus(),
                row.getStatusDetail(),
                row.getQuestion(),
                row.getAnswer(),
                row.getRunId() == null ? null : row.getRunId().toString(),
                row.getErrorMessage(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return UUID.fromString(raw.trim());
    }
}
