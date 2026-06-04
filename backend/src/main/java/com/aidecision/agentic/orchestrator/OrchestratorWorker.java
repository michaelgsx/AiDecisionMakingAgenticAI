package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.repository.OrchestratorRunRepository;
import com.aidecision.agentic.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrchestratorWorker {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorWorker.class);

    private final OrchestratorRunRepository runRepo;
    private final OrchestratorEngine engine;

    public OrchestratorWorker(OrchestratorRunRepository runRepo, OrchestratorEngine engine) {
        this.runRepo = runRepo;
        this.engine = engine;
    }

    @Scheduled(fixedDelayString = "${app.orchestrator.worker-poll-interval-ms:2000}")
    public void pollRuns() {
        List<String> active = List.of(
                RunStatus.PENDING.name(),
                RunStatus.RUNNING.name()
        );

        List<OrchestratorRun> runs;
        try {
            runs = runRepo.findByStatusInOrderByUpdatedAtAsc(active);
        } catch (Throwable t) {
            // Never let a failure escape the scheduled method: a thrown Throwable would cancel
            // this fixedDelay task permanently and silently freeze the whole orchestrator.
            log.warn("Worker could not list active runs: {}", LogSanitizer.message(String.valueOf(t.getMessage())));
            return;
        }
        for (OrchestratorRun run : runs) {
            try {
                engine.processRun(run.getRunId());
            } catch (Throwable t) {
                log.warn("Worker could not process run {}: {}",
                        run.getRunId(),
                        LogSanitizer.message(String.valueOf(t.getMessage())));
            }
        }
    }
}
