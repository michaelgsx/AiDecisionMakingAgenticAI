package com.aidecision.agentic.orchestrator;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class StepFailureClassifier {

    private static final Pattern CONTEXT_TOO_LARGE = Pattern.compile(
            "context[_\\s-]?length|context[_\\s-]?too[_\\s-]?large|maximum context|"
                    + "too many tokens|token limit|max[_\\s-]?tokens|prompt is too long|"
                    + "request too large|reduce the length|input is too long",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DB_CONNECTION = Pattern.compile(
            "connection (refused|timed out|reset|closed|is closed)|cannot open database|"
                    + "login failed|network-related|communications link failure|"
                    + "tcp/ip|unable to connect|connection string|jdbc connection|"
                    + "database .* unavailable|server not found|prelogin error",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DB_PERMISSION = Pattern.compile(
            "permission (was )?denied|select permission|insert permission|"
                    + "access denied|not permitted|insufficient privilege|"
                    + "authorization failed|no schema tables permitted|"
                    + "does not have permission|user does not have|"
                    + "the .* permission .* denied",
            Pattern.CASE_INSENSITIVE);

    public StepFailureKind classify(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return StepFailureKind.RETRYABLE;
        }
        String message = rawMessage.toLowerCase(Locale.ROOT);
        if (CONTEXT_TOO_LARGE.matcher(message).find()) {
            return StepFailureKind.CONTEXT_TOO_LARGE;
        }
        if (DB_CONNECTION.matcher(message).find()) {
            return StepFailureKind.DATABASE_CONNECTION;
        }
        if (DB_PERMISSION.matcher(message).find()) {
            return StepFailureKind.DATABASE_PERMISSION;
        }
        return StepFailureKind.RETRYABLE;
    }

    public String userFacingMessage(StepFailureKind kind, String rawMessage) {
        return switch (kind) {
            case DATABASE_CONNECTION ->
                    "Database connection problem — could not reach the database. "
                            + "Check network connectivity and database availability.";
            case DATABASE_PERMISSION ->
                    "Database permission problem — the query was rejected or the user "
                            + "has no access to the requested tables.";
            case CONTEXT_TOO_LARGE ->
                    "Context too large for the model — upstream step outputs were summarized "
                            + "and the step will be retried.";
            case FATAL -> truncate(rawMessage);
            case RETRYABLE -> truncate(rawMessage);
        };
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Step failed";
        }
        return message.substring(0, Math.min(512, message.length()));
    }
}
