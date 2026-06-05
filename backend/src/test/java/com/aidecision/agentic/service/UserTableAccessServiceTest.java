package com.aidecision.agentic.service;

import com.aidecision.agentic.repository.UserTableAccessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTableAccessServiceTest {

    @Mock
    private UserTableAccessRepository accessRepo;
    @Mock
    private SchemaCatalogService catalog;

    @InjectMocks
    private UserTableAccessService service;

    @Test
    void resolveUserId_defaultsBlankToAdmin() {
        assertThat(service.resolveUserId(null)).isEqualTo("admin");
        assertThat(service.resolveUserId("  ")).isEqualTo("admin");
    }

    @Test
    void allowedTableNames_intersectsAclWithEnabledCatalog() {
        when(accessRepo.findDistinctTableNamesByUserId("admin"))
                .thenReturn(List.of("risk_features", "ghost_table"));
        when(catalog.enabledTableNames()).thenReturn(List.of("risk_features", "qa_message"));

        assertThat(service.allowedTableNames("admin")).containsExactly("risk_features");
    }

    @Test
    void intersectCandidates_filtersPlannerOverride() {
        when(accessRepo.findDistinctTableNamesByUserId("analyst"))
                .thenReturn(List.of("risk_features"));
        when(catalog.enabledTableNames()).thenReturn(List.of("risk_features", "qa_message"));

        assertThat(service.intersectCandidates("analyst", List.of("risk_features", "qa_message")))
                .containsExactly("risk_features");
    }
}
