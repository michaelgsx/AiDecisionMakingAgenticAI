package com.aidecision.agentic.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataAcquisitionService {

    private final LlmSqlGenerationService sqlGeneration;
    private final SchemaCatalogService catalog;
    private final ReadOnlySqlExecutor sqlExecutor;

    public DataAcquisitionService(
            LlmSqlGenerationService sqlGeneration,
            SchemaCatalogService catalog,
            ReadOnlySqlExecutor sqlExecutor) {
        this.sqlGeneration = sqlGeneration;
        this.catalog = catalog;
        this.sqlExecutor = sqlExecutor;
    }

    public AcquisitionResult acquire(String question, String scenario, int maxRows, List<String> tableOverride)
            throws Exception {
        String scenarioKey = scenario == null || scenario.isBlank() ? "qa" : scenario.trim();
        List<String> tables = tableOverride == null || tableOverride.isEmpty()
                ? catalog.tablesForScenario(scenarioKey)
                : tableOverride;

        String sql = sqlGeneration.generateSql(
                question,
                LlmSqlGenerationService.Mode.DATA_ACQUISITION,
                tables);
        int limit = Math.min(Math.max(maxRows, 1), 100);
        List<Map<String, Object>> rows = sqlExecutor.executeSelect(sql, limit);

        Map<String, Object> features = buildFeatures(scenarioKey, tables, rows);

        return new AcquisitionResult(
                scenarioKey,
                tables,
                sql,
                rows,
                rows.size(),
                features,
                "Loaded " + rows.size() + " row(s) from "
                        + tables.size() + " catalog table(s) for scenario " + scenarioKey + ".");
    }

    private static Map<String, Object> buildFeatures(
            String scenario, List<String> tables, List<Map<String, Object>> rows) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("scenario", scenario);
        features.put("source", "schema_catalog_sql");
        features.put("tables", tables);
        features.put("rowCount", rows.size());
        if (!rows.isEmpty()) {
            features.put("sample", rows.get(0));
            if (rows.size() > 1) {
                features.put("additionalRowCount", rows.size() - 1);
            }
        }
        return features;
    }

    public record AcquisitionResult(
            String scenario,
            List<String> tables,
            String sql,
            List<Map<String, Object>> rows,
            int rowCount,
            Map<String, Object> features,
            String note) {}
}
