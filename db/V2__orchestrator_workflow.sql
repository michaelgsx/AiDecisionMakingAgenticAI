-- Orchestrator: tool registry, workflow runs, steps (resumable DAG execution)

IF OBJECT_ID(N'dbo.orchestrator_tool', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.orchestrator_tool (
        tool_name            NVARCHAR(64) NOT NULL PRIMARY KEY,
        version              NVARCHAR(32) NOT NULL DEFAULT '1.0.0',
        description          NVARCHAR(2000) NOT NULL,
        tool_type            NVARCHAR(64) NOT NULL,
        execution_mode       NVARCHAR(16) NOT NULL,
        request_schema_json  NVARCHAR(MAX) NOT NULL,
        response_schema_json NVARCHAR(MAX) NOT NULL,
        enabled              BIT NOT NULL DEFAULT 1,
        created_at           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_orchestrator_tool_mode CHECK (execution_mode IN (N'SYNC', N'ASYNC')),
        CONSTRAINT CK_orchestrator_tool_type CHECK (tool_type IN (
            N'DATA_ACQUISITION', N'SIMILARITY_RETRIEVAL', N'LLM_REASONING',
            N'AGGREGATE', N'FEEDBACK', N'VALIDATION'
        ))
    );

    CREATE TABLE dbo.orchestrator_run (
        run_id           UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
        conversation_id  UNIQUEIDENTIFIER NULL,
        user_id          NVARCHAR(128) NULL,
        question         NVARCHAR(MAX) NOT NULL,
        status           NVARCHAR(32) NOT NULL,
        workflow_json    NVARCHAR(MAX) NULL,
        answer_text      NVARCHAR(MAX) NULL,
        error_message    NVARCHAR(MAX) NULL,
        checkpoint_json  NVARCHAR(MAX) NULL,
        created_at       DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at       DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_orchestrator_run_status CHECK (status IN (
            N'PENDING', N'PLANNING', N'RUNNING', N'COMPLETED', N'FAILED', N'CANCELLED'
        ))
    );
    CREATE INDEX IX_orchestrator_run_status ON dbo.orchestrator_run(status, updated_at);

    CREATE TABLE dbo.orchestrator_step (
        step_id          UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
        run_id           UNIQUEIDENTIFIER NOT NULL,
        step_key         NVARCHAR(64) NOT NULL,
        tool_name        NVARCHAR(64) NOT NULL,
        status           NVARCHAR(32) NOT NULL,
        depends_on_json  NVARCHAR(MAX) NULL,
        input_json       NVARCHAR(MAX) NULL,
        output_json      NVARCHAR(MAX) NULL,
        max_time_ms      INT NOT NULL DEFAULT 30000,
        timeout_ms       INT NOT NULL DEFAULT 120000,
        attempt_count    INT NOT NULL DEFAULT 0,
        started_at       DATETIME2 NULL,
        finished_at      DATETIME2 NULL,
        error_message    NVARCHAR(512) NULL,
        created_at       DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at       DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_orchestrator_step_run FOREIGN KEY (run_id)
            REFERENCES dbo.orchestrator_run(run_id) ON DELETE CASCADE,
        CONSTRAINT FK_orchestrator_step_tool FOREIGN KEY (tool_name)
            REFERENCES dbo.orchestrator_tool(tool_name),
        CONSTRAINT UQ_orchestrator_step_run_key UNIQUE (run_id, step_key),
        CONSTRAINT CK_orchestrator_step_status CHECK (status IN (
            N'PENDING', N'READY', N'RUNNING', N'COMPLETED', N'FAILED', N'SKIPPED', N'TIMED_OUT'
        ))
    );
    CREATE INDEX IX_orchestrator_step_run ON dbo.orchestrator_step(run_id, status);
END;

IF COL_LENGTH('dbo.qa_feedback', 'run_id') IS NULL
BEGIN
    ALTER TABLE dbo.qa_feedback ADD run_id UNIQUEIDENTIFIER NULL;
END;
