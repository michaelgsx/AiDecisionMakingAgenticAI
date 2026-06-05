package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.repository.OrchestratorRunRepository;
import com.aidecision.agentic.repository.OrchestratorStepRepository;
import com.aidecision.agentic.service.AsyncChatStatusService;
import com.aidecision.agentic.tool.HttpToolInvoker;
import com.aidecision.agentic.tool.ToolRegistryService;
import com.aidecision.agentic.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowStepRunnerRetryTest {

    @Mock
    private OrchestratorRunRepository runRepo;
    @Mock
    private OrchestratorStepRepository stepRepo;
    @Mock
    private ToolRegistryService toolRegistry;
    @Mock
    private AsyncChatStatusService asyncChatStatus;
    @Mock
    private HttpToolInvoker httpToolInvoker;
    @Mock
    private StepFailureClassifier failureClassifier;
    @Mock
    private WorkflowContextSummarizer contextSummarizer;

    private WorkflowStepRunner runner;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        runner = new WorkflowStepRunner(
                runRepo,
                stepRepo,
                toolRegistry,
                asyncChatStatus,
                httpToolInvoker,
                new OrchestratorProperties(),
                mapper,
                failureClassifier,
                contextSummarizer,
                null);
        runner = org.mockito.Mockito.spy(runner);
        org.mockito.Mockito.doCallRealMethod().when(runner).finishStep(any(), any(), any());
    }

    @Test
    void finishStep_successResetsRetryToZero() {
        UUID runId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();
        OrchestratorStep step = baseStep(runId, stepId, "s2", "llm_answer");
        step.setAttemptCount(2);
        when(stepRepo.findById(stepId)).thenReturn(Optional.of(step));

        WorkflowStepRunner.StepRunResult result =
                runner.finishStep(runId, stepId, ToolResult.ok(Map.of("answer", "ok")));

        assertThat(result.outcome()).isEqualTo(WorkflowStepRunner.StepOutcome.COMPLETED);
        assertThat(step.getAttemptCount()).isZero();
        assertThat(step.getStatus()).isEqualTo(StepStatus.COMPLETED.name());
    }

    @Test
    void finishStep_databaseErrorFailsImmediatelyWithoutRetryIncrementBeyondPermanentFail() {
        UUID runId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();
        OrchestratorStep step = baseStep(runId, stepId, "s1", "data_acquisition");
        when(stepRepo.findById(stepId)).thenReturn(Optional.of(step));
        when(failureClassifier.classify(any())).thenReturn(StepFailureKind.DATABASE_CONNECTION);
        when(failureClassifier.userFacingMessage(any(), any()))
                .thenReturn("Database connection problem");

        WorkflowStepRunner.StepRunResult result =
                runner.finishStep(runId, stepId, ToolResult.fail("TCP/IP connection timed out"));

        assertThat(result.outcome()).isEqualTo(WorkflowStepRunner.StepOutcome.FAILED);
        assertThat(step.getAttemptCount()).isZero();
        assertThat(step.getErrorMessage()).contains("connection");
        verify(contextSummarizer, never()).summarize(any());
    }

    @Test
    void finishStep_contextTooLargeSummarizesAndSchedulesRetry() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();
        OrchestratorStep dep = baseStep(runId, UUID.randomUUID(), "s1", "data_acquisition");
        dep.setOutputJson("{\"rows\":[1,2,3]}");
        OrchestratorStep step = baseStep(runId, stepId, "s2", "llm_answer");
        step.setDependsOnJson("[\"s1\"]");
        step.setInputJson("{}");

        when(stepRepo.findById(stepId)).thenReturn(Optional.of(step));
        when(stepRepo.findByRunIdOrderByCreatedAtAsc(runId)).thenReturn(List.of(dep, step));
        when(failureClassifier.classify(any())).thenReturn(StepFailureKind.CONTEXT_TOO_LARGE);
        when(failureClassifier.userFacingMessage(any(), any())).thenReturn("Context too large");
        when(contextSummarizer.summarize(any())).thenReturn(Map.of("s1", "short summary"));
        stubTool("llm_answer", 3);

        WorkflowStepRunner.StepRunResult result =
                runner.finishStep(runId, stepId, ToolResult.fail("maximum context length exceeded"));

        assertThat(result.outcome()).isEqualTo(WorkflowStepRunner.StepOutcome.RETRY_READY);
        assertThat(step.getAttemptCount()).isEqualTo(1);
        assertThat(step.getStatus()).isEqualTo(StepStatus.READY.name());
        assertThat(step.getInputJson()).contains("short summary");
        verify(contextSummarizer).summarize(any());
    }

    @Test
    void finishStep_exceedsMaxRetryFailsPermanently() {
        UUID runId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();
        OrchestratorStep step = baseStep(runId, stepId, "s1", "data_acquisition");
        step.setAttemptCount(3);
        when(stepRepo.findById(stepId)).thenReturn(Optional.of(step));
        when(failureClassifier.classify(any())).thenReturn(StepFailureKind.RETRYABLE);
        when(failureClassifier.userFacingMessage(any(), any())).thenReturn("still failing");
        stubTool("data_acquisition", 3);

        WorkflowStepRunner.StepRunResult result =
                runner.finishStep(runId, stepId, ToolResult.fail("timeout"));

        assertThat(result.outcome()).isEqualTo(WorkflowStepRunner.StepOutcome.FAILED);
        assertThat(step.getAttemptCount()).isEqualTo(4);
        assertThat(step.getStatus()).isEqualTo(StepStatus.FAILED.name());
    }

    private void stubTool(String name, int maxRetry) {
        OrchestratorTool tool = new OrchestratorTool();
        tool.setToolName(name);
        tool.setMaxRetry(maxRetry);
        when(toolRegistry.enabledToolsByName()).thenReturn(Map.of(name, tool));
    }

    private static OrchestratorStep baseStep(UUID runId, UUID stepId, String key, String tool) {
        OrchestratorStep step = new OrchestratorStep();
        step.setRunId(runId);
        step.setStepKey(key);
        step.setToolName(tool);
        step.setStatus(StepStatus.RUNNING.name());
        return step;
    }
}
