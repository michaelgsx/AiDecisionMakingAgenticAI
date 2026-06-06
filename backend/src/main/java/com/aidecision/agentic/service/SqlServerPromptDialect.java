package com.aidecision.agentic.service;

/** Shared LLM instructions: all generated SQL runs on Microsoft SQL Server (Azure SQL). */
final class SqlServerPromptDialect {

    static final String TARGET = """
            TARGET DIALECT: Microsoft SQL Server (T-SQL) on Azure SQL.
            Write T-SQL only — NOT PostgreSQL, MySQL, SQLite, BigQuery, or Oracle syntax.
            Use: TOP n (never LIMIT), dbo.table_name, square brackets if needed, \
            ISNULL/COALESCE, CAST/CONVERT, GETUTCDATE().
            Do NOT use: LIMIT, OFFSET/FETCH unless you know T-SQL form, ILIKE, RETURNING, \
            backtick-quoted identifiers, or dialect-specific functions from other engines.
            """;

    static final String READ_ONLY_SELECT_RULES = """
            Output ONLY one read-only SELECT (no markdown fences, no explanation).
            Rules: single statement; no semicolons; read-only.
            """;

    static final String UNSUPPORTED_WINDOW_RULES = """
            T-SQL window-function limits:
            - NEVER write COUNT(DISTINCT ...) OVER () — SQL Server rejects it (error 10759).
            - Do not put DISTINCT inside aggregate window functions unless T-SQL explicitly allows it.
            - For "count X and list them": use a CTE of distinct rows, then
              SELECT TOP n col, (SELECT COUNT(*) FROM cte) AS total_count FROM cte ORDER BY col.
            - Prefer GROUP BY, CTEs, and scalar subqueries over unsupported window DISTINCT patterns.
            """;

    private SqlServerPromptDialect() {}
}
