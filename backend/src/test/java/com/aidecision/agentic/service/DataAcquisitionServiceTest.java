package com.aidecision.agentic.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataAcquisitionServiceTest {

    @Mock
    private DataAcquisitionPlannerService planner;
    @Mock
    private SchemaCatalogService catalog;
    @Mock
    private UserTableAccessService userTableAccess;
    @Mock
    private ReadOnlySqlExecutor sqlExecutor;

    @InjectMocks
    private DataAcquisitionService service;

    @Test
    void acquire_twoPhaseThenExecutesSql() throws Exception {
        when(catalog.tablesForScenario("withdrawal_review"))
                .thenReturn(List.of("risk_features", "risk_decisions", "risk_ingest_records"));
        when(userTableAccess.intersectCandidates(eq("analyst-1"), any()))
                .thenAnswer(inv -> inv.getArgument(1));
        when(planner.selectTables(eq("freeze withdrawal?"), any()))
                .thenReturn(new DataAcquisitionPlannerService.TableSelection(
                        List.of("risk_features", "risk_decisions"), "case and decision"));
        when(planner.generateSql(eq("freeze withdrawal?"), eq(List.of("risk_features", "risk_decisions")), eq(20)))
                .thenReturn("SELECT TOP 5 * FROM dbo.risk_features");
        when(sqlExecutor.executeSelect(any(), eq(20)))
                .thenReturn(List.of(Map.of("user_id", "user-demo-001")));

        DataAcquisitionService.AcquisitionResult result =
                service.acquire("freeze withdrawal?", "withdrawal_review", 20, List.of(), "analyst-1");

        assertThat(result.tables()).containsExactly("risk_features", "risk_decisions");
        assertThat(result.tableSelectionReason()).contains("decision");
        assertThat(result.sql()).contains("risk_features");
        assertThat(result.features()).containsEntry("source", "schema_catalog_two_phase_sql");
        verify(sqlExecutor).executeSelect("SELECT TOP 5 * FROM dbo.risk_features", 20);
    }
}
