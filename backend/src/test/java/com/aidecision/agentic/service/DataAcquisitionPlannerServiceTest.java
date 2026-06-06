package com.aidecision.agentic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataAcquisitionPlannerServiceTest {

    @Mock
    private LlmSqlGenerationService llm;
    @Mock
    private SchemaCatalogService catalog;
    @Spy
    private ObjectMapper mapper = new ObjectMapper();

    @InjectMocks
    private DataAcquisitionPlannerService planner;

    @Test
    void parseTableSelection_filtersToAllowed() throws Exception {
        Set<String> allowed = new LinkedHashSet<>(List.of("risk_features", "risk_decisions"));
        String raw = """
                {"tables":["risk_features","unknown","risk_decisions"],"reason":"need case + outcome"}
                """;

        DataAcquisitionPlannerService.TableSelection sel =
                planner.parseTableSelection(raw, allowed);

        assertThat(sel.tables()).containsExactly("risk_features", "risk_decisions");
        assertThat(sel.reason()).contains("case");
        assertThat(sel.confidence()).isEqualTo(0.5);
    }

    @Test
    void parseTableSelection_readsConfidenceFromJson() throws Exception {
        Set<String> allowed = new LinkedHashSet<>(List.of("risk_features"));
        String raw = """
                {"tables":["risk_features"],"reason":"need case","confidence":0.85}
                """;

        DataAcquisitionPlannerService.TableSelection sel =
                planner.parseTableSelection(raw, allowed);

        assertThat(sel.confidence()).isEqualTo(0.85);
    }
    void selectTables_withoutLlm_returnsCandidates() throws Exception {
        when(llm.isChatConfigured()).thenReturn(false);
        List<String> candidates = List.of("risk_features", "activity_log");

        DataAcquisitionPlannerService.TableSelection sel =
                planner.selectTables("recent logins?", candidates);

        assertThat(sel.tables()).isEqualTo(candidates);
    }

    @Test
    void selectTables_emptyCandidates_doesNotFallBackToFullCatalog() throws Exception {
        DataAcquisitionPlannerService.TableSelection sel =
                planner.selectTables("recent logins?", List.of());

        assertThat(sel.tables()).isEmpty();
        assertThat(sel.reason()).containsIgnoringCase("ACL");
    }
}
