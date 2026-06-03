-- Async chat request status for non-blocking POST /agent/async-chat + polling.

IF OBJECT_ID(N'dbo.async_chat_status', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.async_chat_status (
        request_id       UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
        conversation_id  UNIQUEIDENTIFIER NULL,
        user_id          NVARCHAR(128) NULL,
        question         NVARCHAR(MAX)    NOT NULL,
        answer           NVARCHAR(MAX)    NULL,
        status           NVARCHAR(32)     NOT NULL,
        status_detail    NVARCHAR(256)    NOT NULL,
        run_id           UNIQUEIDENTIFIER NULL,
        error_message    NVARCHAR(MAX)    NULL,
        created_at       DATETIME2        NOT NULL CONSTRAINT DF_async_chat_status_created DEFAULT SYSUTCDATETIME(),
        updated_at       DATETIME2        NOT NULL CONSTRAINT DF_async_chat_status_updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_async_chat_status_run FOREIGN KEY (run_id)
            REFERENCES dbo.orchestrator_run(run_id),
        CONSTRAINT CK_async_chat_status_status CHECK (status IN (
            N'PLANNING', N'EXECUTING', N'LLM_ANSWERING', N'DONE', N'FAILED'
        ))
    );

    CREATE INDEX IX_async_chat_status_run ON dbo.async_chat_status (run_id);
    CREATE INDEX IX_async_chat_status_updated ON dbo.async_chat_status (updated_at DESC);
END;
