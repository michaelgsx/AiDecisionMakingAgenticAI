package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.AsyncChatPollResponse;
import com.aidecision.agentic.dto.AsyncChatSubmitResponse;
import com.aidecision.agentic.dto.ExecuteRequest;
import com.aidecision.agentic.entity.AsyncChatStatus;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.orchestrator.AsyncChatPhase;
import com.aidecision.agentic.orchestrator.OrchestratorEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncChatServiceTest {

    @Mock
    private AsyncChatStatusService statusService;
    @Mock
    private AsyncChatProcessor processor;
    @Mock
    private OrchestratorEngine engine;

    @InjectMocks
    private AsyncChatService asyncChatService;

    @Test
    void submit_createsStatusLinksRunAndStartsAsyncProcessing() {
        UUID requestId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        AsyncChatStatus status = new AsyncChatStatus();
        status.setRequestId(requestId);
        status.setQuestion("Should we freeze?");
        status.setStatus(AsyncChatPhase.PLANNING.name());
        status.setStatusDetail("planning");

        OrchestratorRun run = new OrchestratorRun();
        run.setRunId(runId);

        when(statusService.create(eq("Should we freeze?"), any(), eq("user-1"))).thenReturn(status);
        when(engine.submitQuestion("Should we freeze?", null, "user-1")).thenReturn(run);

        AsyncChatSubmitResponse response = asyncChatService.submit(
                new ExecuteRequest("Should we freeze?", null, "user-1", null));

        assertThat(response.requestId()).isEqualTo(requestId.toString());
        assertThat(response.status()).isEqualTo("PLANNING");
        assertThat(response.pollPath()).isEqualTo("/agent/async-chat/" + requestId);

        verify(statusService).linkRun(requestId, runId);
        verify(processor).processRun(requestId, runId);
    }

    @Test
    void poll_returnsMappedStatus() {
        UUID requestId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        AsyncChatStatus row = new AsyncChatStatus();
        row.setRequestId(requestId);
        row.setStatus(AsyncChatPhase.DONE.name());
        row.setStatusDetail("done");
        row.setQuestion("q");
        row.setAnswer("final answer");
        row.setRunId(runId);

        when(statusService.findByRequestId(requestId)).thenReturn(Optional.of(row));

        AsyncChatPollResponse response = asyncChatService.poll(requestId);

        assertThat(response.requestId()).isEqualTo(requestId.toString());
        assertThat(response.status()).isEqualTo("DONE");
        assertThat(response.statusDetail()).isEqualTo("done");
        assertThat(response.answer()).isEqualTo("final answer");
        assertThat(response.runId()).isEqualTo(runId.toString());
    }
}
