package com.aidecision.agentic.service;

import com.aidecision.agentic.config.AzureOpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** LLM → read-only SQL using schema_catalog table/column descriptions. */
@Service
public class LlmSqlGenerationService {

    private static final Pattern SQL_FENCE =
            Pattern.compile("```(?:sql)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private static final String ANALYTICS_SYSTEM = """
            You write Microsoft SQL Server SELECT-only queries for risk analytics.
            Use ONLY tables and columns from the schema catalog below.
            Output ONLY the SQL (no markdown fences, no explanation).
            Rules: single SELECT; no semicolons; use TOP 100 or less; read-only.
            """;

    private static final String DATA_ACQUISITION_SYSTEM = """
            You write Microsoft SQL Server SELECT-only queries to fetch risk context rows \
            needed to answer the user's question.
            Use ONLY tables and columns from the schema catalog below.
            Prefer risk_features, risk_ingest_records, and risk_decisions when the question \
            involves a case, user, withdrawal, device, or review outcome.
            Return the rows that provide context (TOP N), not chart-style aggregates unless \
            the question explicitly asks for a count or sum.
            Output ONLY the SQL (no markdown fences, no explanation).
            Rules: single SELECT; no semicolons; use TOP 50 or less; read-only.
            """;

    public enum Mode {
        ANALYTICS,
        DATA_ACQUISITION
    }

    private final AzureOpenAiProperties openAi;
    private final SchemaCatalogService catalog;
    private final ObjectMapper mapper;
    private final RestClient http;

    public LlmSqlGenerationService(
            AzureOpenAiProperties openAi,
            SchemaCatalogService catalog,
            ObjectMapper mapper,
            RestClient http) {
        this.openAi = openAi;
        this.catalog = catalog;
        this.mapper = mapper;
        this.http = http;
    }

    public String generateSql(String question, Mode mode, List<String> tableNames) throws Exception {
        if (!openAi.chatConfigured()) {
            return fallbackSql(mode, tableNames);
        }

        String catalogText = catalog.buildLlmCatalogText(tableNames);
        String system = mode == Mode.DATA_ACQUISITION ? DATA_ACQUISITION_SYSTEM : ANALYTICS_SYSTEM;
        String user = "Schema catalog (related tables and columns):\n"
                + catalogText
                + "\n\nUser question:\n"
                + question;

        String raw = callChat(system, user);
        return extractSql(raw);
    }

    private String callChat(String system, String user) throws Exception {
        String base = openAi.getEndpoint().replaceAll("/+$", "");
        URI uri = URI.create(base + "/openai/deployments/" + openAi.getChatDeployment()
                + "/chat/completions?api-version=" + openAi.getEffectiveChatApiVersion());

        ObjectNode root = mapper.createObjectNode();
        root.put("temperature", 0.1);
        root.put("max_tokens", 1024);
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);

        String body = http.post()
                .uri(uri)
                .header("api-key", openAi.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(root.toString())
                .retrieve()
                .body(String.class);

        JsonNode json = mapper.readTree(body == null ? "{}" : body);
        return json.path("choices").path(0).path("message").path("content").asText("").trim();
    }

    private String fallbackSql(Mode mode, List<String> tableNames) {
        if (mode == Mode.DATA_ACQUISITION) {
            boolean hasRisk = tableNames == null
                    || tableNames.isEmpty()
                    || tableNames.contains("risk_features");
            if (hasRisk) {
                return """
                        SELECT TOP 20 request_id, scenario, user_id, transaction_id, \
                        withdraw_amount, country_code, created_at
                        FROM dbo.risk_features
                        ORDER BY created_at DESC
                        """.replaceAll("\\s+", " ").trim();
            }
        }
        return "SELECT TOP 10 table_name, description FROM dbo.schema_catalog_table";
    }

    static String extractSql(String raw) {
        Matcher m = SQL_FENCE.matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        return raw.replaceAll("^```(?:sql)?\\s*", "").replaceAll("```\\s*$", "").trim();
    }
}
