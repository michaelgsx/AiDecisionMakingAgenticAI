-- Cached workflow planner LLM results keyed by normalized question hash.

IF OBJECT_ID(N'dbo.planner_workflow_cache', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.planner_workflow_cache (
        cache_id         UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
        question         NVARCHAR(MAX)    NOT NULL,
        question_hash    NVARCHAR(64)     NOT NULL,
        planner_prompt   NVARCHAR(MAX)    NOT NULL,
        workflow_json    NVARCHAR(MAX)    NOT NULL,
        created_at       DATETIME2        NOT NULL CONSTRAINT DF_planner_workflow_cache_created DEFAULT SYSUTCDATETIME(),
        updated_at       DATETIME2        NOT NULL CONSTRAINT DF_planner_workflow_cache_updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT UQ_planner_workflow_cache_hash UNIQUE (question_hash)
    );

    CREATE INDEX IX_planner_workflow_cache_updated ON dbo.planner_workflow_cache (updated_at DESC);
END;
