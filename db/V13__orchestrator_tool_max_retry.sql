-- Per-tool retry budget surfaced to the LLM workflow planner (orchestrator_tool registry).

IF OBJECT_ID(N'dbo.orchestrator_tool', N'U') IS NOT NULL
   AND COL_LENGTH('dbo.orchestrator_tool', 'max_retry') IS NULL
BEGIN
    ALTER TABLE dbo.orchestrator_tool
        ADD max_retry INT NOT NULL CONSTRAINT DF_orchestrator_tool_max_retry DEFAULT 3;
END;

GO

IF OBJECT_ID(N'dbo.orchestrator_tool', N'U') IS NOT NULL
   AND COL_LENGTH('dbo.orchestrator_tool', 'max_retry') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM sys.check_constraints
       WHERE name = N'CK_orchestrator_tool_max_retry'
         AND parent_object_id = OBJECT_ID(N'dbo.orchestrator_tool')
   )
BEGIN
    ALTER TABLE dbo.orchestrator_tool
        ADD CONSTRAINT CK_orchestrator_tool_max_retry CHECK (max_retry BETWEEN 0 AND 10);
END;

GO
