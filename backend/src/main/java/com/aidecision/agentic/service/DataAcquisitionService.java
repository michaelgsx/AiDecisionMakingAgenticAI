package com.aidecision.agentic.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataAcquisitionService {

    private final DataAcquisitionPlannerService planner;
    private final SchemaCatalogService catalog;
    private final ReadOnlySqlExecutor sqlExecutor;

    public DataAcquisitionService(
            DataAcquisitionPlannerService planner,
            SchemaCatalogService catalog,
            ReadOnlySqlExecutor sqlExecutor) {
        this.planner = planner;
        this.catalog = catalog;
        this.sqlExecutor = sqlExecutor;
    }

    public AcquisitionResult acquire(String question, String scenario, int maxRows, List<String> tableOverride)
            throws Exception {
        String scenarioKey = scenario == null || scenario.isBlank() ? "qa" : scenario.trim();
        List<String> candidates = tableOverride == null || tableOverride.isEmpty()
                ? catalog.tablesForScenario(scenarioKey)
                : tableOverride;

        DataAcquisitionPlannerService.TableSelection selection =
                planner.selectTables(question, candidates);
        List<String> selected = selection.tables();

        String sql = planner.generateSql(question, selected, maxRows);
        int limit = Math.min(Math.max(maxRows, 1), 100);
        List<Map<String, Object>> rows = sqlExecutor.executeSelect(sql, limit);

        Map<String, Object> features = buildFeatures(scenarioKey, candidates, selection, rows);

        return new AcquisitionResult(
                scenarioKey,
                candidates,
                selected,
                selection.reason(),
                sql,
                rows,
                rows.size(),
                features,
                "Loaded " + rows.size() + " row(s) from "
                        + selected.size() + " table(s) (scenario " + scenarioKey + ").");
    }

    private static Map<String, Object> buildFeatures(
            String scenario,
            List<String> candidateTables,
            DataAcquisitionPlannerService.TableSelection selection,
            List<Map<String, Object>> rows) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("scenario", scenario);
        features.put("source", "schema_catalog_two_phase_sql");
        features.put("candidateTables", candidateTables);
        features.put("tables", selection.tables());
        features.put("tableSelectionReason", selection.reason());
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
            List<String> candidateTables,
            List<String> tables,
            String tableSelectionReason,
            String sql,
            List<Map<String, Object>> rows,
            int rowCount,
            Map<String, Object> features,
            String note) {}
}
