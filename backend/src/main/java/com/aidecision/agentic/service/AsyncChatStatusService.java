package com.aidecision.agentic.service;

import com.aidecision.agentic.entity.AsyncChatStatus;
import com.aidecision.agentic.orchestrator.AsyncChatPhase;
import com.aidecision.agentic.repository.AsyncChatStatusRepository;
import com.aidecision.agentic.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AsyncChatStatusService {

    private static final Logger log = LoggerFactory.getLogger(AsyncChatStatusService.class);

    private final AsyncChatStatusRepository repo;

    public AsyncChatStatusService(AsyncChatStatusRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public AsyncChatStatus create(String question, UUID conversationId, String userId) {
        AsyncChatStatus row = new AsyncChatStatus();
        row.setQuestion(question.trim());
        row.setConversationId(conversationId);
        row.setUserId(blankToNull(userId));
        row.setStatus(AsyncChatPhase.PLANNING.name());
        row.setStatusDetail("planning");
        AsyncChatStatus saved = repo.save(row);
        log.info("Async chat {} created question={} userId={}",
                saved.getRequestId(),
                LogSanitizer.question(saved.getQuestion()),
                LogSanitizer.userId(saved.getUserId()));
        return saved;
    }

    /**
     * Ensure a status row exists for a run created outside the async-chat path (e.g. sync
     * {@code /agent/ask} or {@code /agent/execute}). Idempotent: if a row is already linked to the
     * run it is returned unchanged, so the markPlanning/markStepStarted/markDone updates (keyed by
     * runId) drive the same detailed status table for sync calls as for async calls.
     */
    @Transactional
    public AsyncChatStatus ensureForRun(UUID runId, String question, UUID conversationId, String userId) {
        Optional<AsyncChatStatus> existing = repo.findByRunId(runId);
        if (existing.isPresent()) {
            return existing.get();
        }
        AsyncChatStatus row = new AsyncChatStatus();
        row.setQuestion(question == null ? "" : question.trim());
        row.setConversationId(conversationId);
        row.setUserId(blankToNull(userId));
        row.setRunId(runId);
        row.setStatus(AsyncChatPhase.PLANNING.name());
        row.setStatusDetail("planning");
        AsyncChatStatus saved = repo.save(row);
        log.info("Status row {} created for run {} (sync path)", saved.getRequestId(), runId);
        return saved;
    }

    @Transactional
    public void linkRun(UUID requestId, UUID runId) {
        AsyncChatStatus row = repo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown requestId: " + requestId));
        row.setRunId(runId);
        repo.save(row);
        log.info("Async chat {} linked runId={}", requestId, runId);
    }

    public Optional<AsyncChatStatus> findByRequestId(UUID requestId) {
        return repo.findById(requestId);
    }

    public Optional<AsyncChatStatus> findByRunId(UUID runId) {
        return repo.findByRunId(runId);
    }

    private static final List<String> REVIVABLE_STATUSES = List.of(
            AsyncChatPhase.PLANNING.name(),
            AsyncChatPhase.EXECUTING.name(),
            AsyncChatPhase.LLM_ANSWERING.name());

    /** Status rows that have not progressed within the stale threshold (linked to a run). */
    @Transactional(readOnly = true)
    public List<AsyncChatStatus> findStaleCandidates(Instant cutoff) {
        return repo.findByStatusInAndUpdatedAtBeforeAndRunIdIsNotNullOrderByUpdatedAtAsc(
                REVIVABLE_STATUSES, cutoff);
    }

    /**
     * Claim a stale row for revival by bumping {@code updated_at} to now. Returns true when this
     * thread won the claim so another revival worker will skip it until the threshold elapses again.
     */
    @Transactional
    public boolean claimForRevival(UUID requestId, Instant cutoff) {
        int updated = repo.claimStale(requestId, Instant.now(), cutoff, REVIVABLE_STATUSES);
        if (updated > 0) {
            log.info("Revival claimed stale status row {}", requestId);
        }
        return updated > 0;
    }

    @Transactional
    public void markPlanning(UUID runId) {
        updateByRunId(runId, row -> {
            row.setStatus(AsyncChatPhase.PLANNING.name());
            row.setStatusDetail("planning");
        });
    }

    @Transactional
    public void markStepStarted(UUID runId, String stepKey, String toolName) {
        updateByRunId(runId, row -> {
            if ("llm_answer".equals(toolName)) {
                row.setStatus(AsyncChatPhase.LLM_ANSWERING.name());
                row.setStatusDetail("llm-answering");
            } else {
                row.setStatus(AsyncChatPhase.EXECUTING.name());
                row.setStatusDetail("executing/" + stepKey + "/" + toolName);
            }
        });
    }

    @Transactional
    public void markDone(UUID runId, String answer) {
        updateByRunId(runId, row -> {
            row.setStatus(AsyncChatPhase.DONE.name());
            row.setStatusDetail("done");
            row.setAnswer(answer);
            row.setErrorMessage(null);
        });
    }

    @Transactional
    public void markFailed(UUID runId, String errorMessage) {
        updateByRunId(runId, row -> {
            row.setStatus(AsyncChatPhase.FAILED.name());
            row.setStatusDetail("failed");
            row.setErrorMessage(errorMessage);
        });
    }

    private void updateByRunId(UUID runId, java.util.function.Consumer<AsyncChatStatus> updater) {
        repo.findByRunId(runId).ifPresent(row -> {
            updater.accept(row);
            repo.save(row);
            log.debug("Async chat {} status={} detail={}",
                    row.getRequestId(), row.getStatus(), row.getStatusDetail());
        });
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
