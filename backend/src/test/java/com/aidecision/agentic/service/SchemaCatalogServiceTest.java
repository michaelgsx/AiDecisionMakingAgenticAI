package com.aidecision.agentic.service;

import com.aidecision.agentic.entity.SchemaCatalogColumn;
import com.aidecision.agentic.entity.SchemaCatalogForeignKey;
import com.aidecision.agentic.entity.SchemaCatalogTable;
import com.aidecision.agentic.repository.SchemaCatalogColumnRepository;
import com.aidecision.agentic.repository.SchemaCatalogForeignKeyRepository;
import com.aidecision.agentic.repository.SchemaCatalogTableRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaCatalogServiceTest {

    @Mock
    private SchemaCatalogTableRepository tableRepo;
    @Mock
    private SchemaCatalogColumnRepository columnRepo;
    @Mock
    private SchemaCatalogForeignKeyRepository foreignKeyRepo;

    @InjectMocks
    private SchemaCatalogService service;

    @Test
    void buildLlmCatalogText_filtersTables() {
        SchemaCatalogTable risk = table("risk_features", "Risk cases");
        SchemaCatalogTable qa = table("qa_evaluation", "Eval queue");
        when(tableRepo.findByEnabledTrueOrderByTableNameAsc()).thenReturn(List.of(risk, qa));
        when(columnRepo.findByTableNameAndEnabledTrueOrderByColumnNameAsc("risk_features"))
                .thenReturn(List.of(column("risk_features", "user_id", "User id")));

        String text = service.buildLlmCatalogText(List.of("risk_features"));

        assertThat(text).contains("risk_features").contains("user_id");
        assertThat(text).doesNotContain("qa_evaluation");
    }

    @Test
    void tablesForScenario_withdrawalReview_returnsRiskTables() {
        when(tableRepo.findByEnabledTrueOrderByTableNameAsc()).thenReturn(List.of(
                table("risk_features", "a"),
                table("risk_ingest_records", "b"),
                table("risk_decisions", "c"),
                table("activity_log", "d"),
                table("qa_evaluation", "e")));

        List<String> tables = service.tablesForScenario("withdrawal_review");

        assertThat(tables).containsExactly(
                "risk_features", "risk_ingest_records", "risk_decisions", "activity_log");
    }

    @Test
    void buildColumnAndForeignKeyText_includesFkLines() {
        SchemaCatalogTable risk = table("risk_features", "Risk cases");
        SchemaCatalogTable ingest = table("risk_ingest_records", "Ingest");
        when(tableRepo.findByEnabledTrueOrderByTableNameAsc()).thenReturn(List.of(risk, ingest));
        when(columnRepo.findByTableNameAndEnabledTrueOrderByColumnNameAsc("risk_features"))
                .thenReturn(List.of(column("risk_features", "request_id", "Case id")));
        when(columnRepo.findByTableNameAndEnabledTrueOrderByColumnNameAsc("risk_ingest_records"))
                .thenReturn(List.of(column("risk_ingest_records", "record_uuid", "UUID")));
        SchemaCatalogForeignKey fk = new SchemaCatalogForeignKey();
        fk.setFromTable("risk_ingest_records");
        fk.setFromColumn("record_uuid");
        fk.setToTable("risk_features");
        fk.setToColumn("request_id");
        fk.setDescription("Join ingest to case");
        fk.setEnabled(true);
        when(foreignKeyRepo.findEnabledInvolvingTables(List.of("risk_features", "risk_ingest_records")))
                .thenReturn(List.of(fk));

        String text = service.buildColumnAndForeignKeyText(
                List.of("risk_features", "risk_ingest_records"));

        assertThat(text).contains("FOREIGN KEYS");
        assertThat(text).contains("risk_ingest_records.record_uuid -> risk_features.request_id");
    }

    @Test
    void buildTableIndexText_omitsColumns() {
        when(tableRepo.findByEnabledTrueOrderByTableNameAsc())
                .thenReturn(List.of(table("risk_features", "Risk cases")));

        String text = service.buildTableIndexText(List.of("risk_features"));

        assertThat(text).contains("risk_features");
        assertThat(text).doesNotContain("  - ");
    }

    @Test
    void tablesForScenario_qa_returnsAllEnabled() {
        when(tableRepo.findByEnabledTrueOrderByTableNameAsc()).thenReturn(List.of(
                table("risk_features", "a"),
                table("qa_evaluation", "b")));

        assertThat(service.tablesForScenario("qa"))
                .containsExactly("risk_features", "qa_evaluation");
    }

    private static SchemaCatalogTable table(String name, String desc) {
        SchemaCatalogTable t = new SchemaCatalogTable();
        t.setTableName(name);
        t.setSchemaName("dbo");
        t.setDescription(desc);
        t.setEnabled(true);
        return t;
    }

    private static SchemaCatalogColumn column(String table, String col, String desc) {
        SchemaCatalogColumn c = new SchemaCatalogColumn();
        c.setTableName(table);
        c.setColumnName(col);
        c.setDescription(desc);
        c.setEnabled(true);
        return c;
    }
}
