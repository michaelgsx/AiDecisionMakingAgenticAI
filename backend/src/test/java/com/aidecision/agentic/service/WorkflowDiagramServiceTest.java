package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.RunStatusResponse;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.repository.OrchestratorRunRepository;
import com.aidecision.agentic.repository.OrchestratorStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowDiagramServiceTest {

    @Spy
    private ObjectMapper mapper = new ObjectMapper();
    @Mock
    private OrchestratorRunRepository runRepo;
    @Mock
    private OrchestratorStepRepository stepRepo;

    @InjectMocks
    private WorkflowDiagramService service;

    @Test
    void renderFromJson_producesMermaid() {
        String json = """
                {"steps":[{"id":"s1","tool":"data_acquisition","dependsOn":[],"params":{}},\
                {"id":"s2","tool":"llm_answer","dependsOn":["s1"],"params":{}}]}
                """;

        var response = service.renderFromJson(json, Map.of("s1", "COMPLETED"));

        assertThat(response.format()).isEqualTo("mermaid");
        assertThat(response.mermaid()).contains("flowchart TD").contains("s1 --> s2");
    }

    @Test
    void mermaidForRunStatus_includesStepStatuses() {
        String json = """
                {"steps":[{"id":"s1","tool":"data_acquisition","dependsOn":[],"params":{}}]}
                """;
        RunStatusResponse run = new RunStatusResponse(
                UUID.randomUUID().toString(),
                "RUNNING",
                "executing/s1/data_acquisition",
                "q",
                null,
                null,
                json,
                null,
                null,
                null,
                List.of(new RunStatusResponse.StepStatusDto(
                        UUID.randomUUID().toString(), "s1", "data_acquisition", "1.1.0", "RUNNING", null, 1, null)),
                false,
                List.of(),
                false,
                List.of(),
                new RunStatusResponse.RunPaths("/agent/runs/x", "/agent/runs/x/feedback", "/agent/runs/x/poll"));

        String mermaid = service.mermaidForRunStatus(run);

        assertThat(mermaid).contains("wf_running");
    }

    @Test
    void renderForRun_loadsFromDatabase() {
        UUID runId = UUID.randomUUID();
        OrchestratorRun run = new OrchestratorRun();
        run.setRunId(runId);
        run.setWorkflowJson("""
                {"steps":[{"id":"s1","tool":"llm_answer","dependsOn":[],"params":{}}]}
                """);

        OrchestratorStep step = new OrchestratorStep();
        step.setStepKey("s1");
        step.setStatus("COMPLETED");

        when(runRepo.findById(runId)).thenReturn(Optional.of(run));
        when(stepRepo.findByRunIdOrderByCreatedAtAsc(runId)).thenReturn(List.of(step));

        var diagram = service.renderForRun(runId);

        assertThat(diagram.mermaid()).contains("llm_answer").contains("wf_completed");
    }
}
