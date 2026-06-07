package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.FeedbackRequest;
import com.aidecision.agentic.dto.FeedbackResponse;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.QaFeedback;
import com.aidecision.agentic.orchestrator.RunStatus;
import com.aidecision.agentic.orchestrator.WorkflowPlannerService;
import com.aidecision.agentic.repository.OrchestratorRunRepository;
import com.aidecision.agentic.repository.QaConversationRepository;
import com.aidecision.agentic.repository.QaFeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QaFeedbackServiceTest {

    @Mock
    private QaFeedbackRepository feedbackRepo;
    @Mock
    private OrchestratorRunRepository runRepo;
    @Mock
    private QaConversationRepository conversationRepo;
    @Mock
    private WorkflowPlannerService planner;

    private QaFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new QaFeedbackService(feedbackRepo, runRepo, conversationRepo, planner);
    }

    @Test
    void submit_thumbsUp_cachesWorkflowWhenRunCompleted() {
        UUID runId = UUID.randomUUID();
        OrchestratorRun run = completedRun(runId, "{\"steps\":[{\"id\":\"s1\",\"tool\":\"llm_answer\",\"dependsOn\":[],\"params\":{}}]}");
        when(runRepo.findById(runId)).thenReturn(Optional.of(run));
        when(feedbackRepo.findByMessageId(runId)).thenReturn(Optional.empty());
        when(feedbackRepo.save(any())).thenAnswer(inv -> assignFeedbackId(inv.getArgument(0)));

        FeedbackResponse response = service.submit(new FeedbackRequest(
                runId.toString(), runId.toString(), null, "up", null));

        verify(planner).cacheWorkflowFromThumbsUp(same(run));
        assertThat(response.message()).contains("Workflow saved");
    }

    @Test
    void submit_thumbsDown_doesNotCacheWorkflow() {
        UUID runId = UUID.randomUUID();
        OrchestratorRun run = completedRun(runId, "{\"steps\":[]}");
        when(runRepo.findById(runId)).thenReturn(Optional.of(run));
        when(feedbackRepo.findByMessageId(runId)).thenReturn(Optional.empty());
        when(feedbackRepo.save(any())).thenAnswer(inv -> assignFeedbackId(inv.getArgument(0)));

        FeedbackResponse response = service.submit(new FeedbackRequest(
                runId.toString(), runId.toString(), null, "down", null));

        verify(planner, never()).cacheWorkflowFromThumbsUp(any());
        assertThat(response.message()).isEqualTo("Feedback recorded.");
    }

    @Test
    void submit_ignoresRunIdSentAsConversationId() {
        UUID runId = UUID.randomUUID();
        OrchestratorRun run = completedRun(runId, "{\"steps\":[]}");
        when(runRepo.findById(runId)).thenReturn(Optional.of(run));
        when(feedbackRepo.findByMessageId(runId)).thenReturn(Optional.empty());
        when(feedbackRepo.save(any())).thenAnswer(inv -> {
            QaFeedback row = inv.getArgument(0);
            assertThat(row.getConversationId()).isNull();
            return assignFeedbackId(row);
        });

        service.submit(new FeedbackRequest(
                runId.toString(), runId.toString(), runId.toString(), "down", null));

        verify(conversationRepo, never()).existsById(any());
    }

    private static QaFeedback assignFeedbackId(QaFeedback row) {
        if (row.getFeedbackId() == null) {
            try {
                var field = QaFeedback.class.getDeclaredField("feedbackId");
                field.setAccessible(true);
                field.set(row, UUID.randomUUID());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
        return row;
    }

    private static OrchestratorRun completedRun(UUID runId, String workflowJson) {
        OrchestratorRun run = new OrchestratorRun();
        run.setRunId(runId);
        run.setQuestion("How many users?");
        run.setStatus(RunStatus.COMPLETED.name());
        run.setWorkflowJson(workflowJson);
        return run;
    }
}
