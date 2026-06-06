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

    private static final String ANALYTICS_SYSTEM_PREFIX = SqlServerPromptDialect.TARGET
            + "\n"
            + """
            You write T-SQL SELECT-only queries for risk analytics on Azure SQL.
            Use ONLY tables and columns from the schema catalog below.
            """
            + SqlServerPromptDialect.READ_ONLY_SELECT_RULES;

    private static final String DATA_ACQUISITION_SYSTEM_PREFIX = SqlServerPromptDialect.TARGET
            + "\n"
            + """
            You write T-SQL SELECT-only queries to fetch risk context rows for the user's question.
            Use ONLY tables and columns from the schema catalog below.
            Prefer risk_features, risk_ingest_records, and risk_decisions when the question \
            involves a case, user, withdrawal, device, or review outcome.
            Return context rows, not chart-style aggregates unless the question \
            explicitly asks for a count or sum.
            """
            + SqlServerPromptDialect.READ_ONLY_SELECT_RULES;

    public enum Mode {
        ANALYTICS,
        DATA_ACQUISITION
    }

    private final AzureOpenAiProperties openAi;
    private final SchemaCatalogService catalog;
    private final UserTableAccessService userTableAccess;
    private final ReadOnlySqlValidator sqlValidator;
    private final ObjectMapper mapper;
    private final RestClient http;

    public LlmSqlGenerationService(
            AzureOpenAiProperties openAi,
            SchemaCatalogService catalog,
            UserTableAccessService userTableAccess,
            ReadOnlySqlValidator sqlValidator,
            ObjectMapper mapper,
            RestClient http) {
        this.openAi = openAi;
        this.catalog = catalog;
        this.userTableAccess = userTableAccess;
        this.sqlValidator = sqlValidator;
        this.mapper = mapper;
        this.http = http;
    }

    public boolean isChatConfigured() {
        return openAi.chatConfigured();
    }

    public String generateSql(String question, Mode mode, List<String> tableNames, String userId)
            throws Exception {
        return generateSql(question, mode, tableNames, userId, 200);
    }

    public String generateSql(String question, Mode mode, List<String> tableNames, String userId, int maxRows)
            throws Exception {
        List<String> effectiveTables = resolveTablesForUser(tableNames, userId);
        if (!openAi.chatConfigured()) {
            return fallbackSql(mode, effectiveTables);
        }

        int rowCap = Math.min(Math.max(maxRows, 1), 200);
        String catalogText = catalog.buildLlmCatalogText(effectiveTables);
        String system = buildSystemPrompt(mode, rowCap);
        String user = "Schema catalog (related tables and columns):\n"
                + catalogText
                + "\n\nRow cap (application enforces this — omit TOP in SQL unless user asks for top/first N): "
                + rowCap
                + "\n\nUser question:\n"
                + question;

        return generateValidatedSql(system, user);
    }

    private static String buildSystemPrompt(Mode mode, int rowCap) {
        String topRules = SqlServerPromptDialect.topAndDistinctRules(rowCap);
        if (mode == Mode.DATA_ACQUISITION) {
            return DATA_ACQUISITION_SYSTEM_PREFIX + topRules + SqlServerPromptDialect.UNSUPPORTED_WINDOW_RULES;
        }
        return ANALYTICS_SYSTEM_PREFIX + topRules + SqlServerPromptDialect.UNSUPPORTED_WINDOW_RULES;
    }

    private String generateValidatedSql(String system, String user) throws Exception {
        String sql = extractSql(chatComplete(system, user, 1024, 0.1));
        try {
            return sqlValidator.validateAndNormalize(sql);
        } catch (IllegalArgumentException first) {
            String retryUser = user
                    + "\n\nPrevious SQL was rejected — must be valid Microsoft SQL Server T-SQL: "
                    + first.getMessage()
                    + "\nRegenerate: use SELECT DISTINCT TOP n (never TOP n DISTINCT); "
                    + "omit TOP for list-all questions; never COUNT(DISTINCT ...) OVER ().";
            sql = extractSql(chatComplete(system, retryUser, 1024, 0.0));
            return sqlValidator.validateAndNormalize(sql);
        }
    }

    /** Used by {@link DataAcquisitionPlannerService} when Azure OpenAI is not configured. */
    public String fallbackAcquisitionSql(List<String> tableNames) {
        return fallbackSql(Mode.DATA_ACQUISITION, tableNames);
    }

    public String chatComplete(String system, String user, int maxTokens, double temperature)
            throws Exception {
        return callChat(system, user, maxTokens, temperature);
    }

    private String callChat(String system, String user) throws Exception {
        return callChat(system, user, 1024, 0.1);
    }

    private String callChat(String system, String user, int maxTokens, double temperature)
            throws Exception {
        String base = openAi.getEndpoint().replaceAll("/+$", "");
        URI uri = URI.create(base + "/openai/deployments/" + openAi.getChatDeployment()
                + "/chat/completions?api-version=" + openAi.getEffectiveChatApiVersion());

        ObjectNode root = mapper.createObjectNode();
        root.put("max_completion_tokens", maxTokens);
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

    private List<String> resolveTablesForUser(List<String> tableNames, String userId) {
        if (tableNames == null || tableNames.isEmpty()) {
            return userTableAccess.allowedTableNames(userId);
        }
        return userTableAccess.intersectCandidates(userId, tableNames);
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
