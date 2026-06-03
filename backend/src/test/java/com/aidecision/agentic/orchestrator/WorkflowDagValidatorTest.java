package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.OrchestratorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowDagValidatorTest {

    private WorkflowDagValidator validator;
    private Map<String, com.aidecision.agentic.entity.OrchestratorTool> tools;

    @BeforeEach
    void setUp() {
        OrchestratorProperties props = new OrchestratorProperties();
        props.setMaxStepsPerWorkflow(5);
        validator = new WorkflowDagValidator(props);
        com.aidecision.agentic.entity.OrchestratorTool t = syncTool("data_acquisition");
        com.aidecision.agentic.entity.OrchestratorTool t2 = syncTool("llm_answer");
        tools = Map.of("data_acquisition", t, "llm_answer", t2);
    }

    @Test
    void validDag_passes() {
        WorkflowDag dag = new WorkflowDag(List.of(
                step("s1", "data_acquisition", List.of()),
                step("s2", "llm_answer", List.of("s1"))));
        assertThatCode(() -> validator.validate(dag, tools)).doesNotThrowAnyException();
    }

    @Test
    void computeExecutionWaves_parallelIndependentSteps() {
        WorkflowDag dag = new WorkflowDag(List.of(
                step("s1", "data_acquisition", List.of()),
                step("s2", "data_acquisition", List.of()),
                step("s3", "llm_answer", List.of("s1", "s2"))));

        List<List<String>> waves = validator.computeExecutionWaves(dag);

        assertThat(waves).hasSize(2);
        assertThat(waves.get(0)).containsExactlyInAnyOrder("s1", "s2");
        assertThat(waves.get(1)).containsExactly("s3");
    }

    @Test
    void validateExecutable_syncOnly_rejectsAsyncTool() {
        com.aidecision.agentic.entity.OrchestratorTool async = syncTool("human_in_the_loop");
        async.setExecutionMode("ASYNC");
        WorkflowDag dag = new WorkflowDag(List.of(step("s1", "human_in_the_loop", List.of())));

        WorkflowValidationResult result = validator.validateExecutable(
                dag, Map.of("human_in_the_loop", async), true, t -> true);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("ASYNC"));
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

    private static com.aidecision.agentic.entity.OrchestratorTool syncTool(String name) {
        com.aidecision.agentic.entity.OrchestratorTool t = new com.aidecision.agentic.entity.OrchestratorTool();
        t.setToolName(name);
        t.setExecutionMode("SYNC");
        t.setEnabled(true);
        return t;
    }

    private static WorkflowDag.WorkflowStepDef step(String id, String tool, List<String> deps) {
        return new WorkflowDag.WorkflowStepDef(id, tool, deps, Map.of(), null, null);
    }
}
