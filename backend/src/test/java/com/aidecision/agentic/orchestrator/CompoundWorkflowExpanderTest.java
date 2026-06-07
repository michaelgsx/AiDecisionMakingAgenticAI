package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.OrchestratorTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CompoundWorkflowExpanderTest {

    private CompoundWorkflowExpander expander;

    @BeforeEach
    void setUp() {
        OrchestratorProperties props = new OrchestratorProperties();
        props.setMaxStepsPerWorkflow(10);
        expander = new CompoundWorkflowExpander(props);
    }

    @Test
    void expandIfNeeded_splitsSingleNl2SqlIntoParallelSteps() {
        String question = "how many distinct user ids do we have in total? and list them please.";
        WorkflowDag underSplit = new WorkflowDag(List.of(
                nl2sql("s1", question, List.of()),
                llmAnswer("s2", List.of("s1"))));

        WorkflowDag expanded = expander.expandIfNeeded(question, underSplit, tools());

        assertThat(expanded.steps()).hasSize(3);
        assertThat(expanded.steps().stream().filter(s -> "natural_language_to_sql".equals(s.tool())).count())
                .isEqualTo(2);
        assertThat(expanded.steps().get(0).dependsOn()).isEmpty();
        assertThat(expanded.steps().get(1).dependsOn()).isEmpty();
        assertThat(expanded.steps().get(2).tool()).isEqualTo("llm_answer");
        assertThat(expanded.steps().get(2).dependsOn()).containsExactly("s1", "s2");
        assertThat(expanded.steps().get(0).params().get("question").toString())
                .isNotEqualToIgnoringCase(question);
    }

    @Test
    void expandIfNeeded_threePartQuestion_buildsThreeNl2SqlSteps() {
        String question = "how many cases were frozen last week, breakdown by scenario, and compare to the prior week";
        WorkflowDag underSplit = new WorkflowDag(List.of(
                nl2sql("s1", question, List.of()),
                llmAnswer("s2", List.of("s1"))));

        WorkflowDag expanded = expander.expandIfNeeded(question, underSplit, tools());

        assertThat(expanded.steps().stream().filter(s -> "natural_language_to_sql".equals(s.tool())).count())
                .isGreaterThanOrEqualTo(3);
        WorkflowDag.WorkflowStepDef answer = expanded.steps().get(expanded.steps().size() - 1);
        assertThat(answer.tool()).isEqualTo("llm_answer");
        assertThat(answer.dependsOn()).hasSize(expanded.steps().size() - 1);
    }

    @Test
    void expandIfNeeded_leavesSingleIntentUnchanged() {
        String question = "How many cases were frozen last week?";
        WorkflowDag dag = new WorkflowDag(List.of(
                nl2sql("s1", question, List.of()),
                llmAnswer("s2", List.of("s1"))));

        WorkflowDag result = expander.expandIfNeeded(question, dag, tools());

        assertThat(result).isSameAs(dag);
    }

    private static Map<String, OrchestratorTool> tools() {
        OrchestratorTool nl2sql = new OrchestratorTool();
        nl2sql.setToolName("natural_language_to_sql");
        nl2sql.setExecutionMode("SYNC");
        nl2sql.setEnabled(true);

        OrchestratorTool llm = new OrchestratorTool();
        llm.setToolName("llm_answer");
        llm.setExecutionMode("SYNC");
        llm.setEnabled(true);

        return Map.of("natural_language_to_sql", nl2sql, "llm_answer", llm);
    }

    private static WorkflowDag.WorkflowStepDef nl2sql(String id, String question, List<String> deps) {
        return WorkflowDag.WorkflowStepDef.tool(id, "natural_language_to_sql", deps, Map.of("question", question), null, null);
    }

    private static WorkflowDag.WorkflowStepDef llmAnswer(String id, List<String> deps) {
        return WorkflowDag.WorkflowStepDef.tool(id, "llm_answer", deps, Map.of(), null, null);
    }
}
