package com.aidecision.agentic.service;

import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.entity.QaEvaluation;
import com.aidecision.agentic.orchestrator.RunStatus;
import com.aidecision.agentic.orchestrator.StepStatus;
import com.aidecision.agentic.repository.OrchestratorRunRepository;
import com.aidecision.agentic.repository.OrchestratorStepRepository;
import com.aidecision.agentic.repository.QaEvaluationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QaEvaluationServiceTest {

    @Mock
    private QaEvaluationRepository evaluationRepo;
    @Mock
    private OrchestratorRunRepository runRepo;
    @Mock
    private OrchestratorStepRepository stepRepo;

    private QaEvaluationService service;

    private final ObjectMapper mapper = new ObjectMapper();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new QaEvaluationService(evaluationRepo, runRepo, stepRepo, mapper);
    }

    @Test
    void enqueueCompletedRun_createsRunAndStepEvaluationsWithConfidence() {
        UUID runId = UUID.randomUUID();
        OrchestratorRun run = new OrchestratorRun();
        run.setRunId(runId);
        run.setStatus(RunStatus.COMPLETED.name());
        run.setQuestion("Q?");
        run.setAnswerText("Final");

        OrchestratorStep s1 = step(runId, "s1", "data_acquisition",
                "{\"rowCount\":2,\"confidence\":0.8}");
        OrchestratorStep s2 = step(runId, "s2", "llm_answer",
                "{\"answer\":\"Final\",\"confidence\":0.92}");

        when(evaluationRepo.existsByRunIdAndStepKey(eq(runId), eq(QaEvaluation.RUN_STEP_KEY)))
                .thenReturn(false);
        when(evaluationRepo.existsByRunIdAndStepKey(eq(runId), eq("s1"))).thenReturn(false);
        when(evaluationRepo.existsByRunIdAndStepKey(eq(runId), eq("s2"))).thenReturn(false);
        when(stepRepo.findByRunIdOrderByCreatedAtAsc(runId)).thenReturn(List.of(s1, s2));

        service.enqueueCompletedRun(run);

        ArgumentCaptor<QaEvaluation> captor = ArgumentCaptor.forClass(QaEvaluation.class);
        verify(evaluationRepo, atLeast(3)).save(captor.capture());

        List<QaEvaluation> saved = captor.getAllValues();
        assertThat(saved).anyMatch(e ->
                QaEvaluation.SCOPE_RUN.equals(e.getEvaluationScope())
                        && e.getConfidence() == 0.92);
        assertThat(saved).anyMatch(e ->
                QaEvaluation.SCOPE_STEP.equals(e.getEvaluationScope())
                        && "s1".equals(e.getStepKey())
                        && e.getConfidence() == 0.8);
    }

    private static OrchestratorStep step(UUID runId, String key, String tool, String output) {
        OrchestratorStep step = new OrchestratorStep();
        step.setRunId(runId);
        step.setStepKey(key);
        step.setToolName(tool);
        step.setStatus(StepStatus.COMPLETED.name());
        step.setOutputJson(output);
        return step;
    }
}
