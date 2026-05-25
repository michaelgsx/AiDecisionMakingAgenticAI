package com.aidecision.agentic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two-phase data acquisition: (1) LLM picks tables from catalog index,
 * (2) LLM writes SQL using column detail + foreign keys.
 */
@Service
public class DataAcquisitionPlannerService {

    private static final Pattern JSON_FENCE =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private static final String TABLE_SELECT_SYSTEM = """
            You pick database tables needed to answer a risk/QA question.
            You receive ONLY a table index (name + description). Pick the minimal set \
            that can answer the question; include extra tables only when JOINs are required.
            Output ONLY JSON: {"tables":["table_a","table_b"],"reason":"one sentence"}.
            Rules: table names must come from the index; at least one table; at most 6 tables.
            """;

    private static final String SQL_SYSTEM_TEMPLATE = """
            You write Microsoft SQL Server SELECT-only queries to fetch context rows \
            for the user's question.
            Use ONLY tables, columns, and FOREIGN KEYS from the schema detail below.
            Join tables using the documented foreign keys when multiple tables are needed.
            Return context rows (TOP N), not chart aggregates unless the question asks for counts.
            Output ONLY the SQL (no markdown fences, no explanation).
            Rules: single SELECT; no semicolons; use TOP %d or less; read-only.
            """;

    private final LlmSqlGenerationService llm;
    private final SchemaCatalogService catalog;
    private final ObjectMapper mapper;

    public DataAcquisitionPlannerService(
            LlmSqlGenerationService llm,
            SchemaCatalogService catalog,
            ObjectMapper mapper) {
        this.llm = llm;
        this.catalog = catalog;
        this.mapper = mapper;
    }

    public record TableSelection(List<String> tables, String reason) {}

    public TableSelection selectTables(String question, List<String> candidates) throws Exception {
        if (candidates == null || candidates.isEmpty()) {
            candidates = catalog.enabledTableNames();
        }
        Set<String> allowed = new HashSet<>(candidates);

        if (!llm.isChatConfigured()) {
            return new TableSelection(new ArrayList<>(candidates), "LLM unavailable; using scenario catalog tables.");
        }

        String index = catalog.buildTableIndexText(candidates);
        String user = "Table index:\n" + index + "\n\nUser question:\n" + question;
        String raw = llm.chatComplete(TABLE_SELECT_SYSTEM, user, 512, 0.0);
        return parseTableSelection(raw, allowed);
    }

    public String generateSql(String question, List<String> selectedTables, int maxRows) throws Exception {
        int top = Math.min(Math.max(maxRows, 1), 100);
        String system = SQL_SYSTEM_TEMPLATE.formatted(top);

        if (!llm.isChatConfigured()) {
            return llm.fallbackAcquisitionSql(selectedTables);
        }

        String detail = catalog.buildColumnAndForeignKeyText(selectedTables);
        String user = "Schema detail (columns + foreign keys):\n"
                + detail
                + "\n\nUser question:\n"
                + question;
        String raw = llm.chatComplete(system, user, 1024, 0.1);
        return LlmSqlGenerationService.extractSql(raw);
    }

    TableSelection parseTableSelection(String raw, Set<String> allowed) throws Exception {
        String json = extractJsonPayload(raw);
        JsonNode node = mapper.readTree(json);
        List<String> tables = new ArrayList<>();
        JsonNode arr = node.path("tables");
        if (arr.isArray()) {
            for (JsonNode t : arr) {
                String name = t.asText("").trim();
                if (!name.isBlank() && allowed.contains(name)) {
                    tables.add(name);
                }
            }
        }
        if (tables.isEmpty()) {
            tables.addAll(allowed.stream().limit(4).toList());
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>(tables);
        String reason = node.path("reason").asText("").trim();
        if (reason.isBlank()) {
            reason = "Selected " + deduped.size() + " table(s) from catalog index.";
        }
        return new TableSelection(List.copyOf(deduped), reason);
    }

    static String extractJsonPayload(String raw) {
        Matcher m = JSON_FENCE.matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
