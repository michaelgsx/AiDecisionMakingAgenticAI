package com.aidecision.agentic.tool;

import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.repository.OrchestratorToolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
        registry = new ToolRegistryService(toolRepo, List.of(executor));
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

    private static OrchestratorTool toolRow(String name, String version) {
        OrchestratorTool t = new OrchestratorTool();
        t.setToolName(name);
        t.setVersion(version);
        t.setEnabled(true);
        return t;
    }
}
