package com.aidecision.agentic.service;

import com.aidecision.agentic.orchestrator.OrchestratorEngine;
import com.aidecision.agentic.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AsyncChatProcessor {

    private static final Logger log = LoggerFactory.getLogger(AsyncChatProcessor.class);

    private final OrchestratorEngine engine;
    private final AsyncChatStatusService statusService;

    public AsyncChatProcessor(OrchestratorEngine engine, AsyncChatStatusService statusService) {
        this.engine = engine;
        this.statusService = statusService;
    }

    @Async("asyncChatExecutor")
    public void processRun(UUID requestId, UUID runId) {
        log.info("Async chat {} processing runId={}", requestId, runId);
        try {
            engine.processRun(runId);
        } catch (Exception e) {
            log.error("Async chat {} run {} failed: {}",
                    requestId,
                    runId,
                    LogSanitizer.message(e.getMessage()),
                    e);
            statusService.markFailed(runId, e.getMessage());
        }
    }
}
