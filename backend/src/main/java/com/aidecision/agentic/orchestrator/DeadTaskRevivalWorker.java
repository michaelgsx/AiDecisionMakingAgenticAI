package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.AsyncChatStatus;
import com.aidecision.agentic.service.AsyncChatStatusService;
import com.aidecision.agentic.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Sweeps {@code async_chat_status} for tasks that stopped updating (crashed worker, deploy, etc.)
 * and re-drives the linked orchestrator run. Claiming bumps {@code updated_at} so concurrent
 * revival threads do not process the same row.
 */
@Component
public class DeadTaskRevivalWorker {

    private static final Logger log = LoggerFactory.getLogger(DeadTaskRevivalWorker.class);

    private final AsyncChatStatusService statusService;
    private final OrchestratorEngine engine;
    private final OrchestratorProperties props;

    public DeadTaskRevivalWorker(
            AsyncChatStatusService statusService,
            OrchestratorEngine engine,
            OrchestratorProperties props) {
        this.statusService = statusService;
        this.engine = engine;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${app.orchestrator.revival-poll-interval-ms:10000}")
    public void reviveStaleTasks() {
        Instant cutoff = Instant.now().minusMillis(props.getStaleTaskThresholdMs());
        List<AsyncChatStatus> stale;
        try {
            stale = statusService.findStaleCandidates(cutoff);
        } catch (Throwable t) {
            log.warn("Revival sweeper could not list stale status rows: {}",
                    LogSanitizer.message(String.valueOf(t.getMessage())));
            return;
        }

        for (AsyncChatStatus row : stale) {
            try {
                if (!statusService.claimForRevival(row.getRequestId(), cutoff)) {
                    continue;
                }
                log.info("Reviving stale run {} (status {} detail {}, last update {})",
                        row.getRunId(),
                        row.getStatus(),
                        row.getStatusDetail(),
                        row.getUpdatedAt());
                engine.processRun(row.getRunId());
            } catch (Throwable t) {
                log.warn("Revival could not process run {} for status {}: {}",
                        row.getRunId(),
                        row.getRequestId(),
                        LogSanitizer.message(String.valueOf(t.getMessage())));
            }
        }
    }
}
