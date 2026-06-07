package com.aidecision.agentic.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GateConditionEvaluatorTest {

    private GateConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new GateConditionEvaluator(new ObjectMapper());
    }

    @Test
    void evaluate_numericComparison() {
        Map<String, String> outputs = Map.of(
                "s1", "{\"rowCount\": 12}");
        assertThat(evaluator.evaluate("steps.s1.output.rowCount > 5", outputs)).isTrue();
        assertThat(evaluator.evaluate("steps.s1.output.rowCount > 20", outputs)).isFalse();
    }

    @Test
    void evaluate_stringEquality() {
        Map<String, String> outputs = Map.of(
                "s2", "{\"aiLabel\": \"frozen\"}");
        assertThat(evaluator.evaluate("steps.s2.output.aiLabel == 'frozen'", outputs)).isTrue();
        assertThat(evaluator.evaluate("steps.s2.output.aiLabel == 'passed'", outputs)).isFalse();
    }

    @Test
    void evaluate_truthyField() {
        Map<String, String> outputs = Map.of(
                "s3", "{\"accepted\": true}");
        assertThat(evaluator.evaluate("steps.s3.output.accepted", outputs)).isTrue();
    }
}
