package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.PendingAsyncDto;
import com.aidecision.agentic.entity.OrchestratorHumanRequest;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.orchestrator.RunStatus;
import com.aidecision.agentic.tool.ToolRegistryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrchestratorRunAssemblerTest {

    @Mock
    private HumanInLoopService humanInLoop;
    @Mock
    private ToolRegistryService toolRegistry;
    @Mock
    private WorkflowDiagramService workflowDiagrams;

    @InjectMocks
    private OrchestratorRunAssembler assembler;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void toRunStatus_includesPendingAsyncForHuman() {
        UUID runId = UUID.randomUUID();
        OrchestratorRun run = new OrchestratorRun();
        run.setRunId(runId);
        run.setStatus(RunStatus.RUNNING.name());
        run.setQuestion("q");
        run.setUserId("user-1");

        OrchestratorHumanRequest req = new OrchestratorHumanRequest();
        req.setRequestId(UUID.randomUUID());
        req.setRunId(runId);
        req.setStepKey("s3");
        req.setPrompt("Approve?");
        req.setProposal("Freeze");

        OrchestratorTool tool = new OrchestratorTool();
        tool.setToolName("human_in_the_loop");
        tool.setVersion("1.1.0");

        when(humanInLoop.pendingForRun(runId)).thenReturn(List.of(req));
        when(toolRegistry.enabledToolsByName()).thenReturn(Map.of("human_in_the_loop", tool));

        var status = assembler.toRunStatus(run, List.of());

        assertThat(status.waitingForAsync()).isTrue();
        assertThat(status.pendingAsync()).hasSize(1);
        PendingAsyncDto p = status.pendingAsync().get(0);
        assertThat(p.asyncKind()).isEqualTo("INPUT_REQUIRED");
        assertThat(p.toolName()).isEqualTo("human_in_the_loop");
        assertThat(p.toolVersion()).isEqualTo("1.1.0");
        assertThat(p.allowedDecisions()).containsExactly("accept", "reject");
        assertThat(p.feedbackPath()).contains("/feedback");
    }
}
