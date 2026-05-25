package com.aidecision.agentic.service;

import com.aidecision.agentic.entity.SchemaCatalogColumn;
import com.aidecision.agentic.entity.SchemaCatalogTable;
import com.aidecision.agentic.repository.SchemaCatalogColumnRepository;
import com.aidecision.agentic.repository.SchemaCatalogTableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SchemaCatalogService {

    private final SchemaCatalogTableRepository tableRepo;
    private final SchemaCatalogColumnRepository columnRepo;

    public SchemaCatalogService(
            SchemaCatalogTableRepository tableRepo,
            SchemaCatalogColumnRepository columnRepo) {
        this.tableRepo = tableRepo;
        this.columnRepo = columnRepo;
    }

    private static final Map<String, List<String>> SCENARIO_TABLES = Map.of(
            "withdrawal_review", List.of(
                    "risk_features", "risk_ingest_records", "risk_decisions", "activity_log"),
            "withdrawal_spike", List.of(
                    "risk_features", "risk_ingest_records", "risk_decisions", "activity_log"),
            "login_anomaly", List.of(
                    "risk_features", "risk_ingest_records", "risk_decisions"),
            "qa", List.of());

    @Transactional(readOnly = true)
    public String buildLlmCatalogText() {
        return buildLlmCatalogText(null);
    }

    /** When {@code tableNames} is null or empty, includes all enabled catalog tables. */
    @Transactional(readOnly = true)
    public String buildLlmCatalogText(List<String> tableNames) {
        List<SchemaCatalogTable> tables = tableRepo.findByEnabledTrueOrderByTableNameAsc();
        if (tableNames != null && !tableNames.isEmpty()) {
            Set<String> allowed = new HashSet<>(tableNames);
            tables = tables.stream()
                    .filter(t -> allowed.contains(t.getTableName()))
                    .toList();
        }
        if (tables.isEmpty()) {
            return "(schema catalog empty — run db migrations V4/V5/V8)";
        }

        StringBuilder sb = new StringBuilder();
        for (SchemaCatalogTable t : tables) {
            sb.append("TABLE ").append(t.getSchemaName()).append(".").append(t.getTableName())
                    .append(": ").append(t.getDescription()).append("\n");
            List<SchemaCatalogColumn> cols =
                    columnRepo.findByTableNameAndEnabledTrueOrderByColumnNameAsc(t.getTableName());
            for (SchemaCatalogColumn c : cols) {
                sb.append("  - ").append(c.getColumnName());
                if (c.getDataType() != null) {
                    sb.append(" (").append(c.getDataType()).append(")");
                }
                sb.append(": ").append(c.getDescription());
                if (c.getSampleHint() != null && !c.getSampleHint().isBlank()) {
                    sb.append(" [example: ").append(c.getSampleHint()).append("]");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    @Transactional(readOnly = true)
    public List<String> enabledTableNames() {
        return tableRepo.findByEnabledTrueOrderByTableNameAsc().stream()
                .map(SchemaCatalogTable::getTableName)
                .collect(Collectors.toList());
    }

    /** Tables whose descriptions are sent to the data_acquisition LLM prompt. */
    @Transactional(readOnly = true)
    public List<String> tablesForScenario(String scenario) {
        String key = scenario == null ? "" : scenario.trim().toLowerCase();
        List<String> mapped = SCENARIO_TABLES.get(key);
        if (mapped != null && !mapped.isEmpty()) {
            return filterToEnabled(mapped);
        }
        return enabledTableNames();
    }

    private List<String> filterToEnabled(List<String> names) {
        Set<String> enabled = new HashSet<>(enabledTableNames());
        List<String> out = new ArrayList<>();
        for (String name : names) {
            if (enabled.contains(name)) {
                out.add(name);
            }
        }
        return out.isEmpty() ? enabledTableNames() : out;
    }
}
