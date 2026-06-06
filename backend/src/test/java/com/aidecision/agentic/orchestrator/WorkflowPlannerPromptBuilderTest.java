package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.OrchestratorTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowPlannerPromptBuilderTest {

    private WorkflowPlannerPromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new WorkflowPlannerPromptBuilder(new OrchestratorProperties(), null, new ObjectMapper());
    }

    @Test
    void build_containsSystemUserAndOutputSchemaSections() throws Exception {
        OrchestratorTool tool = sampleTool("data_acquisition");

        PlannerPrompt prompt = builder.build(
                "show user user-001",
                Map.of("data_acquisition", tool));

        assertThat(prompt.systemPrompt()).contains("insufficient_tools");
        assertThat(prompt.systemPrompt()).contains("ONLY the tools provided");
        assertThat(prompt.userPrompt()).contains("## 1. User question");
        assertThat(prompt.userPrompt()).contains("show user user-001");
        assertThat(prompt.userPrompt()).contains("## 2. Available tools");
        assertThat(prompt.userPrompt()).contains("data_acquisition");
        assertThat(prompt.userPrompt()).contains("requestSchema");
        assertThat(prompt.userPrompt()).contains("## 3. SQL data catalog");
        assertThat(prompt.userPrompt()).contains("## 4. Required output JSON schema");
        assertThat(prompt.outputJsonSchema()).contains("\"status\"");
        assertThat(prompt.outputJsonSchema()).contains("insufficient_tools");
        assertThat(prompt.outputJsonSchema()).contains("steps");
    }

    @Test
    void systemPrompt_instructsParallelNl2SqlForCompoundQuestions() throws Exception {
        OrchestratorTool nl2sql = sampleTool("natural_language_to_sql");
        nl2sql.setDescription("NL to read-only SQL.");

        PlannerPrompt prompt = builder.build(
                "how many distinct user ids do we have in total? and list them please.",
                Map.of("natural_language_to_sql", nl2sql, "llm_answer", sampleTool("llm_answer")));

        assertThat(prompt.systemPrompt()).contains("Compound / multi-part questions");
        assertThat(prompt.systemPrompt()).contains("dependsOn: []");
        assertThat(prompt.systemPrompt()).contains("run in parallel");
        assertThat(prompt.systemPrompt()).contains("Decomposition pattern");
        assertThat(prompt.systemPrompt()).contains("COUNT/aggregate sub-question only");
        assertThat(prompt.systemPrompt()).contains("LIST/detail sub-question only");
        assertThat(prompt.systemPrompt()).doesNotContain("distinct user ids");
        assertThat(prompt.outputJsonSchema()).contains("run in parallel");
    }

    private static OrchestratorTool sampleTool(String name) {
        OrchestratorTool t = new OrchestratorTool();
        t.setToolName(name);
        t.setVersion("1.1.0");
        t.setMaxRetry(3);
        t.setDescription("Fetch SQL context rows.");
        t.setToolType("DATA_ACQUISITION");
        t.setExecutionMode("SYNC");
        t.setRequestSchemaJson("""
                {"type":"object","properties":{"question":{"type":"string","description":"NL question"}}}
                """);
        t.setResponseSchemaJson("""
                {"type":"object","properties":{"rowCount":{"type":"integer","description":"Rows returned"}}}
                """);
        t.setEnabled(true);
        return t;
    }
}
