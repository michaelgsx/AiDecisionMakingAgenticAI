package com.aidecision.agentic.service;

import java.util.regex.Pattern;

/** T-SQL syntax checks beyond read-only keyword guards. */
final class SqlServerSyntaxRules {

    private static final Pattern COUNT_DISTINCT_OVER = Pattern.compile(
            "COUNT\\s*\\(\\s*DISTINCT\\s+[^)]+\\)\\s*OVER\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    private SqlServerSyntaxRules() {}

    static void validate(String sql) {
        String normalized = sql == null ? "" : sql.replaceAll("\\s+", " ");
        if (COUNT_DISTINCT_OVER.matcher(normalized).find()) {
            throw new IllegalArgumentException(
                    "SQL Server does not allow COUNT(DISTINCT ...) OVER (); "
                            + "use a CTE or scalar subquery for count-and-list questions");
        }
    }
}
