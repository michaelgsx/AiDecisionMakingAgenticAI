package com.aidecision.agentic.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowPlannerServiceParseTest {

    private WorkflowPlannerService planner;

    @BeforeEach
    void setUp() {
        planner = new WorkflowPlannerService(
                null, null, null, null,
                new WorkflowPlannerPromptBuilder(new com.aidecision.agentic.config.OrchestratorProperties(), new ObjectMapper()),
                null,
                new ObjectMapper(),
                null);
    }

    @Test
    void parseResponse_okWorkflow() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "message": "Lookup user profile",
                  "steps": [
                    {
                      "id": "s1",
                      "tool": "data_acquisition",
                      "dependsOn": [],
                      "params": { "scenario": "qa", "question": "user-001" }
                    },
                    {
                      "id": "s2",
                      "tool": "llm_answer",
                      "dependsOn": ["s1"],
                      "params": {}
                    }
                  ]
                }
                """;

        PlannerWorkflowResponse response = planner.parseResponse(json);

        assertThat(response.isOk()).isTrue();
        assertThat(response.toDag().steps()).hasSize(2);
        assertThat(response.toDag().steps().get(0).tool()).isEqualTo("data_acquisition");
    }

    @Test
    void parseResponse_insufficientTools() throws Exception {
        String json = """
                {
                  "status": "insufficient_tools",
                  "message": "Not enough tools to fetch live payment history.",
                  "missingTools": ["payment_history_api", "real_time_balance"]
                }
                """;

        PlannerWorkflowResponse response = planner.parseResponse(json);

        assertThat(response.isInsufficientTools()).isTrue();
        assertThat(response.missingTools()).containsExactly("payment_history_api", "real_time_balance");
        assertThatThrownBy(response::toDag).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void parseResponse_legacyStepsOnlyStillWorks() throws Exception {
        String json = """
                {
                  "steps": [
                    { "id": "s1", "tool": "llm_answer", "dependsOn": [], "params": {} }
                  ]
                }
                """;

        PlannerWorkflowResponse response = planner.parseResponse(json);

        assertThat(response.isOk()).isTrue();
        assertThat(response.toDag().steps()).hasSize(1);
    }
}
