package com.aidecision.agentic.service;

import java.util.regex.Pattern;

/** T-SQL syntax checks beyond read-only keyword guards. */
final class SqlServerSyntaxRules {

    private static final Pattern COUNT_DISTINCT_OVER = Pattern.compile(
            "COUNT\\s*\\(\\s*DISTINCT\\s+[^)]+\\)\\s*OVER\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    /** Invalid T-SQL: {@code SELECT TOP n DISTINCT ...} — must be {@code SELECT DISTINCT TOP n}. */
    private static final Pattern TOP_BEFORE_DISTINCT = Pattern.compile(
            "\\bSELECT\\s+TOP\\s+(\\d+)\\s+DISTINCT\\b",
            Pattern.CASE_INSENSITIVE);

    private SqlServerSyntaxRules() {}

    /** Fixes common LLM T-SQL mistakes before validation/execution. */
    static String normalize(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        return TOP_BEFORE_DISTINCT.matcher(sql.trim()).replaceAll("SELECT DISTINCT TOP $1");
    }

    static void validate(String sql) {
        String normalized = sql == null ? "" : sql.replaceAll("\\s+", " ");
        if (TOP_BEFORE_DISTINCT.matcher(normalized).find()) {
            throw new IllegalArgumentException(
                    "T-SQL requires SELECT DISTINCT TOP n — never SELECT TOP n DISTINCT");
        }
        if (COUNT_DISTINCT_OVER.matcher(normalized).find()) {
            throw new IllegalArgumentException(
                    "SQL Server does not allow COUNT(DISTINCT ...) OVER (); "
                            + "use a CTE or scalar subquery for count-and-list questions");
        }
    }
}
