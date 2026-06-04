-- Tool invocation URL surfaced to the orchestrator executor and the LLM workflow planner.
-- Each built-in tool is callable at POST /agent/tools/{tool_name}/{version}/execute; storing the
-- endpoint in the registry lets the executor (and external/remote tools) know how to call a tool.

IF OBJECT_ID(N'dbo.orchestrator_tool', N'U') IS NOT NULL
   AND COL_LENGTH('dbo.orchestrator_tool', 'endpoint_url') IS NULL
BEGIN
    ALTER TABLE dbo.orchestrator_tool
        ADD endpoint_url NVARCHAR(256) NULL;
END;

GO

-- Backfill built-in tools with their relative execute endpoint (tool_name/version/execute).
IF OBJECT_ID(N'dbo.orchestrator_tool', N'U') IS NOT NULL
   AND COL_LENGTH('dbo.orchestrator_tool', 'endpoint_url') IS NOT NULL
BEGIN
    UPDATE dbo.orchestrator_tool
        SET endpoint_url = N'/agent/tools/' + tool_name + N'/' + version + N'/execute'
        WHERE endpoint_url IS NULL;
END;

GO
