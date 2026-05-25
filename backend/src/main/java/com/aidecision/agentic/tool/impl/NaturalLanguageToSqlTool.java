package com.aidecision.agentic.tool.impl;

import com.aidecision.agentic.service.Nl2SqlService;
import com.aidecision.agentic.service.ReadOnlySqlExecutor;
import com.aidecision.agentic.tool.AgentTool;
import com.aidecision.agentic.tool.ToolExecutionContext;
import com.aidecision.agentic.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NaturalLanguageToSqlTool implements AgentTool {

    private final Nl2SqlService nl2Sql;
    private final ReadOnlySqlExecutor sqlExecutor;

    public NaturalLanguageToSqlTool(Nl2SqlService nl2Sql, ReadOnlySqlExecutor sqlExecutor) {
        this.nl2Sql = nl2Sql;
        this.sqlExecutor = sqlExecutor;
    }

    @Override
    public String name() {
        return "natural_language_to_sql";
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx, Map<String, Object> params) {
        String question = params.containsKey("question")
                ? params.get("question").toString()
                : ctx.question();
        int maxRows = 50;
        if (params.get("maxRows") instanceof Number n) {
            maxRows = n.intValue();
        }

        try {
            String sql = nl2Sql.generateSql(question);
            var rows = sqlExecutor.executeSelect(sql, maxRows);
            return ToolResult.ok(Map.of(
                    "sql", sql,
                    "rows", rows,
                    "rowCount", rows.size(),
                    "summary", "Executed read-only query; returned " + rows.size() + " row(s)."
            ));
        } catch (Exception e) {
            return ToolResult.fail("NL2SQL failed: " + e.getMessage());
        }
    }
}
