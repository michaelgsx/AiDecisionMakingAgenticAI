package com.aidecision.agentic.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ReadOnlySqlValidator {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|EXEC|EXECUTE|MERGE|GRANT|REVOKE|xp_)\\b",
            Pattern.CASE_INSENSITIVE);

    public void validate(String sql) {
        validateAndNormalize(sql);
    }

    /** Validates read-only T-SQL and returns syntax-normalized SQL safe to execute. */
    public String validateAndNormalize(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL is empty");
        }
        String trimmed = SqlServerSyntaxRules.normalize(sql.trim());
        if (trimmed.contains(";")) {
            throw new IllegalArgumentException("Only a single SELECT statement is allowed (no semicolons)");
        }
        if (!trimmed.regionMatches(true, 0, "SELECT", 0, 6)) {
            throw new IllegalArgumentException("Only SELECT queries are allowed");
        }
        if (FORBIDDEN.matcher(trimmed).find()) {
            throw new IllegalArgumentException("Query contains forbidden keywords");
        }
        SqlServerSyntaxRules.validate(trimmed);
        return trimmed;
    }
}
