package com.aidecision.agentic.tool.impl;

import com.aidecision.agentic.service.DataAcquisitionService;
import com.aidecision.agentic.tool.ToolExecutionContext;
import com.aidecision.agentic.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataAcquisitionToolTest {

    @Mock
    private DataAcquisitionService acquisition;

    @InjectMocks
    private DataAcquisitionTool tool;

    @Test
    void execute_returnsSqlAndRows() throws Exception {
        when(acquisition.acquire(eq("freeze?"), eq("qa"), eq(50), anyList(), eq("admin")))
                .thenReturn(new DataAcquisitionService.AcquisitionResult(
                        "qa",
                        List.of("risk_features", "risk_decisions"),
                        List.of("risk_features"),
                        "need case rows",
                        "SELECT TOP 1 * FROM dbo.risk_features",
                        List.of(Map.of("user_id", "u1")),
                        1,
                        Map.of("scenario", "qa"),
                        "ok"));

        ToolResult result = tool.execute(
                new ToolExecutionContext(UUID.randomUUID(), "s1", "freeze?", Map.of(), "admin"),
                Map.of("scenario", "qa"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).containsKeys("sql", "rows", "features");
    }

    @Test
    void execute_missingQuestion_fails() {
        ToolResult result = tool.execute(
                new ToolExecutionContext(UUID.randomUUID(), "s1", "", Map.of(), null),
                Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("question");
    }
}
