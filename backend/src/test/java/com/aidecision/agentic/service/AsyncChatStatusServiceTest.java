package com.aidecision.agentic.service;

import com.aidecision.agentic.entity.AsyncChatStatus;
import com.aidecision.agentic.orchestrator.AsyncChatPhase;
import com.aidecision.agentic.repository.AsyncChatStatusRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncChatStatusServiceTest {

    @Mock
    private AsyncChatStatusRepository repo;

    @InjectMocks
    private AsyncChatStatusService service;

    @Test
    void markStepStarted_setsExecutingDetailForNonLlmTools() {
        UUID runId = UUID.randomUUID();
        AsyncChatStatus row = new AsyncChatStatus();
        row.setRequestId(UUID.randomUUID());
        row.setRunId(runId);
        when(repo.findByRunId(runId)).thenReturn(Optional.of(row));

        service.markStepStarted(runId, "s1", "data_acquisition");

        ArgumentCaptor<AsyncChatStatus> captor = ArgumentCaptor.forClass(AsyncChatStatus.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AsyncChatPhase.EXECUTING.name());
        assertThat(captor.getValue().getStatusDetail()).isEqualTo("executing/s1/data_acquisition");
    }

    @Test
    void markStepStarted_setsLlmAnsweringForLlmAnswerTool() {
        UUID runId = UUID.randomUUID();
        AsyncChatStatus row = new AsyncChatStatus();
        row.setRequestId(UUID.randomUUID());
        row.setRunId(runId);
        when(repo.findByRunId(runId)).thenReturn(Optional.of(row));

        service.markStepStarted(runId, "s3", "llm_answer");

        ArgumentCaptor<AsyncChatStatus> captor = ArgumentCaptor.forClass(AsyncChatStatus.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AsyncChatPhase.LLM_ANSWERING.name());
        assertThat(captor.getValue().getStatusDetail()).isEqualTo("llm-answering");
    }

    @Test
    void claimForRevival_returnsTrueWhenRowUpdated() {
        UUID requestId = UUID.randomUUID();
        Instant cutoff = Instant.now().minusSeconds(600);
        when(repo.claimStale(eq(requestId), any(), eq(cutoff), any())).thenReturn(1);

        assertThat(service.claimForRevival(requestId, cutoff)).isTrue();
    }

    @Test
    void claimForRevival_returnsFalseWhenAnotherThreadClaimed() {
        UUID requestId = UUID.randomUUID();
        Instant cutoff = Instant.now().minusSeconds(600);
        when(repo.claimStale(eq(requestId), any(), eq(cutoff), any())).thenReturn(0);

        assertThat(service.claimForRevival(requestId, cutoff)).isFalse();
    }
}
