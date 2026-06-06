package com.aidecision.agentic.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationConfidenceExtractorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extract_readsTopLevelConfidence() throws Exception {
        double c = EvaluationConfidenceExtractor.extract(
                "{\"answer\":\"ok\",\"confidence\":0.91}", "llm_answer", mapper);
        assertThat(c).isEqualTo(0.91);
    }

    @Test
    void extract_readsNestedFeaturesConfidence() throws Exception {
        double c = EvaluationConfidenceExtractor.extract(
                "{\"features\":{\"confidence\":0.66}}", "data_acquisition", mapper);
        assertThat(c).isEqualTo(0.66);
    }

    @Test
    void extract_ragUsesTopHitScoreWhenNoExplicitField() throws Exception {
        double c = EvaluationConfidenceExtractor.extract("""
                {"hits":[{"score":0.73},{"score":0.41}]}
                """, "ai_decision_rag", mapper);
        assertThat(c).isEqualTo(0.73);
    }

    @Test
    void extract_clampsOutOfRange() {
        assertThat(EvaluationConfidenceExtractor.clamp(1.5)).isEqualTo(1.0);
        assertThat(EvaluationConfidenceExtractor.clamp(-0.1)).isEqualTo(0.0);
    }
}
