package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.OrchestratorTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowDagValidatorTest {

    private WorkflowDagValidator validator;
    private Map<String, OrchestratorTool> tools;

    @BeforeEach
    void setUp() {
        OrchestratorProperties props = new OrchestratorProperties();
        props.setMaxStepsPerWorkflow(5);
        validator = new WorkflowDagValidator(props);
        OrchestratorTool t = new OrchestratorTool();
        t.setToolName("data_acquisition");
        t.setEnabled(true);
        tools = Map.of("data_acquisition", t);
    }

    @Test
    void validDag_passes() {
        WorkflowDag dag = new WorkflowDag(List.of(
                step("s1", "data_acquisition", List.of()),
                step("s2", "data_acquisition", List.of("s1"))));
        assertThatCode(() -> validator.validate(dag, tools)).doesNotThrowAnyException();
    }

    @Test
    void unknownTool_fails() {
        WorkflowDag dag = new WorkflowDag(List.of(step("s1", "missing_tool", List.of())));
        assertThatThrownBy(() -> validator.validate(dag, tools))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown or disabled tool");
    }

    @Test
    void cycle_fails() {
        WorkflowDag dag = new WorkflowDag(List.of(
                step("s1", "data_acquisition", List.of("s2")),
                step("s2", "data_acquisition", List.of("s1"))));
        assertThatThrownBy(() -> validator.validate(dag, tools))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void tooManySteps_fails() {
        WorkflowDag dag = new WorkflowDag(List.of(
                step("s1", "data_acquisition", List.of()),
                step("s2", "data_acquisition", List.of()),
                step("s3", "data_acquisition", List.of()),
                step("s4", "data_acquisition", List.of()),
                step("s5", "data_acquisition", List.of()),
                step("s6", "data_acquisition", List.of())));
        assertThatThrownBy(() -> validator.validate(dag, tools))
                .hasMessageContaining("max steps");
    }

    private static WorkflowDag.WorkflowStepDef step(String id, String tool, List<String> deps) {
        return new WorkflowDag.WorkflowStepDef(id, tool, deps, Map.of(), null, null);
    }
}
