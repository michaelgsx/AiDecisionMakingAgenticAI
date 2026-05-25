package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.ToolExecuteRequest;
import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.tool.AgentTool;
import com.aidecision.agentic.tool.ToolExecutionContext;
import com.aidecision.agentic.tool.ToolRegistryService;
import com.aidecision.agentic.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolOperationsServiceTest {

    @Mock
    private ToolRegistryService toolRegistry;
    @Mock
    private HumanInLoopService humanInLoop;

    @InjectMocks
    private ToolOperationsService operations;

    @Test
    void execute_validatesVersionAndRunsTool() {
        OrchestratorTool row = new OrchestratorTool();
        row.setToolName("data_acquisition");
        row.setVersion("1.1.0");
        AgentTool tool = mock(AgentTool.class);
        when(toolRegistry.requireRegistered("data_acquisition", "1.1.0")).thenReturn(row);
        when(toolRegistry.requireExecutor("data_acquisition")).thenReturn(tool);
        when(tool.execute(any(ToolExecutionContext.class), any()))
                .thenReturn(ToolResult.ok(Map.of("rowCount", 1)));

        var response = operations.execute(
                "data_acquisition",
                "1.1.0",
                new ToolExecuteRequest(Map.of("scenario", "qa"), "q?", null, null, null));

        assertThat(response.success()).isTrue();
        assertThat(response.output()).containsEntry("rowCount", 1);
        verify(toolRegistry).requireRegistered("data_acquisition", "1.1.0");
    }

    @Test
    void poll_syncTool_throws() {
        OrchestratorTool row = new OrchestratorTool();
        when(toolRegistry.requireRegistered(eq("data_acquisition"), eq("1.1.0"))).thenReturn(row);
        AgentTool sync = mock(AgentTool.class);
        when(toolRegistry.requireExecutor("data_acquisition")).thenReturn(sync);

        assertThatThrownBy(() -> operations.poll(
                        "data_acquisition",
                        "1.1.0",
                        new com.aidecision.agentic.dto.ToolPollRequest(null, null, null, null, Map.of())))
                .hasMessageContaining("not ASYNC");
    }
}
