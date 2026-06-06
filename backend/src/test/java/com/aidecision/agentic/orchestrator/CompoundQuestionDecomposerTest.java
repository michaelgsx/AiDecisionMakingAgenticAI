package com.aidecision.agentic.orchestrator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompoundQuestionDecomposerTest {

    @Test
    void decompose_countAndList_splitsOnQuestionAndAnd() {
        List<String> parts = CompoundQuestionDecomposer.decompose(
                "how many distinct user ids do we have in total? and list them please.");

        assertThat(parts).hasSize(2);
        assertThat(parts.get(0)).containsIgnoringCase("how many");
        assertThat(parts.get(1)).containsIgnoringCase("list");
        assertThat(parts.get(1)).containsIgnoringCase("distinct");
        assertThat(parts.get(1)).doesNotContain("them");
    }

    @Test
    void decompose_threeCommaSeparatedIntents() {
        List<String> parts = CompoundQuestionDecomposer.decompose(
                "how many cases were frozen last week, breakdown by scenario, and compare to the prior week");

        assertThat(parts).hasSizeGreaterThanOrEqualTo(3);
        assertThat(parts.get(0)).containsIgnoringCase("frozen");
        assertThat(parts.get(1)).containsIgnoringCase("breakdown");
        assertThat(parts.get(2)).containsIgnoringCase("compare");
    }

    @Test
    void decompose_singleIntent_returnsEmpty() {
        assertThat(CompoundQuestionDecomposer.decompose("How many cases were frozen last week?"))
                .isEmpty();
        assertThat(CompoundQuestionDecomposer.isCompound("How many cases were frozen last week?"))
                .isFalse();
    }

    @Test
    void decompose_semicolonSeparated() {
        List<String> parts = CompoundQuestionDecomposer.decompose(
                "Count rejected logins; show top 5 countries by volume");

        assertThat(parts).hasSize(2);
    }
}
