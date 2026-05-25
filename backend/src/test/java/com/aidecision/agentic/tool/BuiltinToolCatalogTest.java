package com.aidecision.agentic.tool;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinToolCatalogTest {

    @Test
    void all_includesSixBuiltInTools() {
        Set<String> names = BuiltinToolCatalog.all().stream()
                .map(BuiltinToolCatalog.Definition::name)
                .collect(Collectors.toSet());

        assertThat(names).containsExactlyInAnyOrder(
                "data_acquisition",
                "similarity_retrieval",
                "ai_decision_rag",
                "natural_language_to_sql",
                "human_in_the_loop",
                "llm_answer");
    }

    @Test
    void all_toolsUseVersion110() {
        assertThat(BuiltinToolCatalog.all())
                .allMatch(d -> "1.1.0".equals(d.version()));
    }
}
