package com.aidecision.agentic.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Redacts PII and secrets before values are written to logs. */
public final class LogSanitizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TEXT_LEN = 200;

    private static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern BEARER = Pattern.compile("Bearer\\s+\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_STRING_LITERAL = Pattern.compile("'(?:''|[^'])*'");
    private static final Pattern QUOTED_STRING = Pattern.compile("\"[^\"]*\"");

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "question", "answer", "text", "prompt", "proposal", "comment",
            "userid", "user_id", "username", "display_name", "displayname",
            "email", "phone", "mobile", "password", "secret", "token", "apikey", "api_key",
            "authorization", "sql", "query", "rows", "row", "transactionid", "transaction_id",
            "name", "address", "ssn", "content");

    private LogSanitizer() {
    }

    public static String text(String value) {
        if (value == null) {
            return "null";
        }
        if (value.isBlank()) {
            return "(blank)";
        }
        String sanitized = value.trim();
        sanitized = EMAIL.matcher(sanitized).replaceAll("[email]");
        sanitized = BEARER.matcher(sanitized).replaceAll("Bearer [redacted]");
        sanitized = SQL_STRING_LITERAL.matcher(sanitized).replaceAll("'[redacted]'");
        sanitized = QUOTED_STRING.matcher(sanitized).replaceAll("\"[redacted]\"");
        return truncate(sanitized);
    }

    public static String message(String message) {
        return text(message);
    }

    public static String userId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "(none)";
        }
        return maskIdentifier(userId.trim());
    }

    public static String question(String question) {
        if (question == null || question.isBlank()) {
            return "(blank)";
        }
        return "[question len=" + question.trim().length() + "]";
    }

    public static String jsonSummary(String json) {
        if (json == null || json.isBlank()) {
            return "(empty)";
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            return truncate(summarizeNode(node));
        } catch (Exception ignored) {
            return "[json len=" + json.length() + "]";
        }
    }

    private static String summarizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        if (node.isTextual()) {
            return "[value]";
        }
        if (node.isArray()) {
            return "array[len=" + node.size() + "]";
        }
        if (node.isObject()) {
            StringBuilder sb = new StringBuilder("{");
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            boolean first = true;
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                String key = entry.getKey();
                JsonNode child = entry.getValue();
                if (isSensitiveKey(key)) {
                    sb.append(key).append("=[redacted]");
                } else if (child.isNumber() || child.isBoolean()) {
                    sb.append(key).append("=").append(child.asText());
                } else if (child.isArray()) {
                    sb.append(key).append("=array[len=").append(child.size()).append("]");
                } else if (child.isObject()) {
                    sb.append(key).append("=object");
                } else {
                    sb.append(key).append("=[value]");
                }
            }
            sb.append("}");
            return sb.toString();
        }
        return "[unknown]";
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return true;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace('-', '_');
        return SENSITIVE_KEYS.contains(normalized);
    }

    private static String maskIdentifier(String value) {
        if (value.length() <= 2) {
            return "**";
        }
        return value.charAt(0) + "***" + value.charAt(value.length() - 1);
    }

    private static String truncate(String value) {
        if (value.length() <= MAX_TEXT_LEN) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LEN) + "...[truncated]";
    }
}
