package com.aidecision.agentic.orchestrator;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowMermaidRendererTest {

    @Test
    void emptyDag_returnsPlaceholder() {
        String m = WorkflowMermaidRenderer.toMermaid(new WorkflowDag(List.of()));
        assertThat(m).contains("empty");
        assertThat(m).startsWith("flowchart TD");
    }

    @Test
    void linearDag_rendersNodesAndEdges() {
        WorkflowDag dag = new WorkflowDag(List.of(
                new WorkflowDag.WorkflowStepDef("s1", "data_acquisition", List.of(), Map.of(), null, null),
                new WorkflowDag.WorkflowStepDef("s2", "llm_answer", List.of("s1"), Map.of(), null, null)));

        String m = WorkflowMermaidRenderer.toMermaid(dag, Map.of("s1", "COMPLETED", "s2", "RUNNING"));

        assertThat(m).contains("s1");
        assertThat(m).contains("data_acquisition");
        assertThat(m).contains("s1 --> s2");
        assertThat(m).contains("class s1 wf_completed");
        assertThat(m).contains("class s2 wf_running");
    }

    @Test
    void statusMapFromSteps_buildsMap() {
        var map = WorkflowMermaidRenderer.statusMapFromSteps(List.of(
                new WorkflowMermaidRenderer.StepStatusSource("s1", "COMPLETED"),
                new WorkflowMermaidRenderer.StepStatusSource("s2", "PENDING")));
        assertThat(map).containsEntry("s1", "COMPLETED").containsEntry("s2", "PENDING");
    }
}
