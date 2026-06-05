package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.AsyncChatStatus;
import com.aidecision.agentic.service.AsyncChatStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadTaskRevivalWorkerTest {

    @Mock
    private AsyncChatStatusService statusService;
    @Mock
    private OrchestratorEngine engine;

    private final OrchestratorProperties props = new OrchestratorProperties();
    private DeadTaskRevivalWorker worker;

    @BeforeEach
    void setUp() {
        props.setStaleTaskThresholdMs(300_000);
        worker = new DeadTaskRevivalWorker(statusService, engine, props);
    }

    @Test
    void reviveStaleTasks_invokesProcessRunWhenClaimSucceeds() {
        UUID runId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AsyncChatStatus row = staleRow(requestId, runId);

        when(statusService.findStaleCandidates(any())).thenReturn(List.of(row));
        when(statusService.claimForRevival(eq(requestId), any())).thenReturn(true);

        worker.reviveStaleTasks();

        verify(engine).processRun(runId);
    }

    @Test
    void reviveStaleTasks_skipsWhenAnotherThreadClaimed() {
        UUID requestId = UUID.randomUUID();
        AsyncChatStatus row = staleRow(requestId, UUID.randomUUID());

        when(statusService.findStaleCandidates(any())).thenReturn(List.of(row));
        when(statusService.claimForRevival(eq(requestId), any())).thenReturn(false);

        worker.reviveStaleTasks();

        verify(engine, never()).processRun(any());
    }

    @Test
    void reviveStaleTasks_returnsEarlyWhenListingFails() {
        when(statusService.findStaleCandidates(any())).thenThrow(new RuntimeException("db unavailable"));

        worker.reviveStaleTasks();

        verify(engine, never()).processRun(any());
    }

    @Test
    void reviveStaleTasks_continuesAfterSingleRunFailure() {
        UUID runId1 = UUID.randomUUID();
        UUID runId2 = UUID.randomUUID();
        AsyncChatStatus row1 = staleRow(UUID.randomUUID(), runId1);
        AsyncChatStatus row2 = staleRow(UUID.randomUUID(), runId2);

        when(statusService.findStaleCandidates(any())).thenReturn(List.of(row1, row2));
        when(statusService.claimForRevival(any(), any())).thenReturn(true);
        doThrow(new RuntimeException("stuck")).when(engine).processRun(runId1);

        worker.reviveStaleTasks();

        verify(engine).processRun(runId1);
        verify(engine).processRun(runId2);
    }

    private static AsyncChatStatus staleRow(UUID requestId, UUID runId) {
        AsyncChatStatus row = new AsyncChatStatus();
        row.setRequestId(requestId);
        row.setRunId(runId);
        row.setStatus(AsyncChatPhase.EXECUTING.name());
        row.setStatusDetail("executing/s1/data_acquisition");
        return row;
    }
}
