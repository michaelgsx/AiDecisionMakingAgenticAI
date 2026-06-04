package com.aidecision.agentic.tool;

import com.aidecision.agentic.dto.RegisterToolRequest;
import com.aidecision.agentic.dto.ToolSchemaDto;
import com.aidecision.agentic.dto.ToolSchemaFieldDto;
import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.repository.OrchestratorToolRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolRegistryServiceTest {

    @Mock
    private OrchestratorToolRepository toolRepo;

    private ToolRegistryService registry;

    @BeforeEach
    void setUp() {
        AgentTool executor = mock(AgentTool.class);
        when(executor.name()).thenReturn("data_acquisition");
        registry = new ToolRegistryService(
                toolRepo, List.of(executor), new ToolSchemaConverter(new ObjectMapper()));
        registry.initExecutors();
    }

    @Test
    void requireRegistered_matchingVersion_returnsRow() {
        OrchestratorTool row = toolRow("data_acquisition", "1.1.0");
        when(toolRepo.findByEnabledTrueOrderByToolNameAsc()).thenReturn(List.of(row));

        OrchestratorTool found = registry.requireRegistered("data_acquisition", "1.1.0");

        assertThat(found.getToolName()).isEqualTo("data_acquisition");
    }

    @Test
    void requireRegistered_versionMismatch_throws() {
        when(toolRepo.findByEnabledTrueOrderByToolNameAsc())
                .thenReturn(List.of(toolRow("data_acquisition", "1.1.0")));

        assertThatThrownBy(() -> registry.requireRegistered("data_acquisition", "2.0.0"))
                .hasMessageContaining("version mismatch");
    }

    @Test
    void register_persistsStructuredSchemasAndMaxRetry() {
        when(toolRepo.findById("data_acquisition")).thenReturn(java.util.Optional.empty());
        when(toolRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterToolRequest request = sampleRequest();
        registry.register(request);

        ArgumentCaptor<OrchestratorTool> captor = ArgumentCaptor.forClass(OrchestratorTool.class);
        verify(toolRepo).save(captor.capture());
        OrchestratorTool saved = captor.getValue();

        assertThat(saved.getMaxRetry()).isEqualTo(3);
        assertThat(saved.getRequestSchemaJson()).contains("\"question\"");
        assertThat(saved.getRequestSchemaJson()).contains("Natural-language question");
        assertThat(saved.getResponseSchemaJson()).contains("\"rowCount\"");
    }

    @Test
    void register_duplicateName_throws() {
        when(toolRepo.findById("data_acquisition")).thenReturn(java.util.Optional.of(toolRow("data_acquisition", "1.1.0")));

        assertThatThrownBy(() -> registry.register(sampleRequest()))
                .hasMessageContaining("already exists");
    }

    private static RegisterToolRequest sampleRequest() {
        return new RegisterToolRequest(
                "data_acquisition",
                "1.1.0",
                3,
                "Fetch user profile rows via NL→SQL.",
                "DATA_ACQUISITION",
                "SYNC",
                new ToolSchemaDto(
                        "Inputs for SQL generation",
                        List.of(new ToolSchemaFieldDto(
                                "question",
                                "string",
                                "Natural-language question driving SQL generation."))),
                new ToolSchemaDto(
                        "SQL result bundle",
                        List.of(new ToolSchemaFieldDto(
                                "rowCount",
                                "integer",
                                "Number of rows returned."))),
                "/agent/tools/data_acquisition/1.1.0/execute",
                true);
    }

    private static OrchestratorTool toolRow(String name, String version) {
        OrchestratorTool t = new OrchestratorTool();
        t.setToolName(name);
        t.setVersion(version);
        t.setEnabled(true);
        t.setMaxRetry(3);
        t.setDescription("test");
        t.setToolType("DATA_ACQUISITION");
        t.setExecutionMode("SYNC");
        t.setRequestSchemaJson("{}");
        t.setResponseSchemaJson("{}");
        return t;
    }
}
