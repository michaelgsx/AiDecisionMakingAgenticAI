-- Schema catalog for natural_language_to_sql tool + human-in-the-loop async requests

IF OBJECT_ID(N'dbo.schema_catalog_table', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.schema_catalog_table (
        table_name    NVARCHAR(128) NOT NULL PRIMARY KEY,
        schema_name   NVARCHAR(64) NOT NULL DEFAULT N'dbo',
        description   NVARCHAR(2000) NOT NULL,
        enabled       BIT NOT NULL DEFAULT 1,
        created_at    DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at    DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
    );

    CREATE TABLE dbo.schema_catalog_column (
        column_id     UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
        table_name    NVARCHAR(128) NOT NULL,
        column_name   NVARCHAR(128) NOT NULL,
        data_type     NVARCHAR(64) NULL,
        description   NVARCHAR(2000) NOT NULL,
        is_nullable   BIT NULL,
        sample_hint   NVARCHAR(500) NULL,
        enabled       BIT NOT NULL DEFAULT 1,
        CONSTRAINT FK_schema_column_table FOREIGN KEY (table_name)
            REFERENCES dbo.schema_catalog_table(table_name) ON DELETE CASCADE,
        CONSTRAINT UQ_schema_column UNIQUE (table_name, column_name)
    );

    CREATE TABLE dbo.orchestrator_human_request (
        request_id    UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
        run_id        UNIQUEIDENTIFIER NOT NULL,
        step_id       UNIQUEIDENTIFIER NOT NULL,
        step_key      NVARCHAR(64) NOT NULL,
        prompt        NVARCHAR(1000) NOT NULL,
        proposal      NVARCHAR(MAX) NOT NULL,
        status        NVARCHAR(16) NOT NULL,
        decision      NVARCHAR(16) NULL,
        comment       NVARCHAR(2000) NULL,
        created_at    DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        answered_at   DATETIME2 NULL,
        CONSTRAINT FK_human_request_run FOREIGN KEY (run_id)
            REFERENCES dbo.orchestrator_run(run_id) ON DELETE CASCADE,
        CONSTRAINT CK_human_request_status CHECK (status IN (N'WAITING', N'ANSWERED', N'EXPIRED')),
        CONSTRAINT CK_human_request_decision CHECK (decision IS NULL OR decision IN (N'accept', N'reject'))
    );
    CREATE INDEX IX_human_request_run ON dbo.orchestrator_human_request(run_id, status);
END;

-- Extend tool_type check for new tools (drop/recreate if needed on existing DB)
-- New tool types registered in Java seed: NL2SQL, HUMAN_IN_LOOP
