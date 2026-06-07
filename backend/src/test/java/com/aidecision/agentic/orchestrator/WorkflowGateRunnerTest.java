package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.repository.OrchestratorStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowGateRunnerTest {

    @Mock
    private OrchestratorStepRepository stepRepo;

    private WorkflowGateRunner gateRunner;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        gateRunner = new WorkflowGateRunner(stepRepo, new GateConditionEvaluator(mapper), mapper);
    }

    @Test
    void runGate_skipsElseBranch() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID gateId = UUID.randomUUID();

        OrchestratorStep s1 = step("s1", "natural_language_to_sql", StepStatus.COMPLETED.name(),
                "{\"rowCount\": 10}");
        OrchestratorStep gate = step("g1", WorkflowDag.GATE_TOOL_NAME, StepStatus.READY.name(), null);
        assignStepId(gate, gateId);
        gate.setInputJson("""
                {"expression":"steps.s1.output.rowCount > 5","then":["s2"],"else":["s3"]}
                """);
        OrchestratorStep s2 = step("s2", "llm_answer", StepStatus.PENDING.name(), null);
        OrchestratorStep s3 = step("s3", "llm_answer", StepStatus.PENDING.name(), null);

        when(stepRepo.findById(gateId)).thenReturn(Optional.of(gate));
        when(stepRepo.findByRunIdOrderByCreatedAtAsc(runId)).thenReturn(List.of(s1, gate, s2, s3));
        when(stepRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkflowStepRunner.StepRunResult result = gateRunner.runGate(runId, gateId);

        assertThat(result.outcome()).isEqualTo(WorkflowStepRunner.StepOutcome.COMPLETED);
        ArgumentCaptor<OrchestratorStep> saved = ArgumentCaptor.forClass(OrchestratorStep.class);
        verify(stepRepo, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().stream()
                .filter(s -> "s3".equals(s.getStepKey()))
                .map(OrchestratorStep::getStatus)
                .findFirst()).contains(StepStatus.SKIPPED.name());
    }

    private static OrchestratorStep step(String key, String tool, String status, String outputJson) {
        OrchestratorStep step = new OrchestratorStep();
        assignStepId(step, UUID.randomUUID());
        step.setRunId(UUID.randomUUID());
        step.setStepKey(key);
        step.setToolName(tool);
        step.setStatus(status);
        step.setOutputJson(outputJson);
        step.setDependsOnJson("[]");
        return step;
    }

    private static void assignStepId(OrchestratorStep step, UUID stepId) {
        try {
            var field = OrchestratorStep.class.getDeclaredField("stepId");
            field.setAccessible(true);
            field.set(step, stepId);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
