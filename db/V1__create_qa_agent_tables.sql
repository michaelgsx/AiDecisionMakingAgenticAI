-- Agentic risk-control Q&A: conversations, messages, thumbs feedback
IF OBJECT_ID(N'dbo.qa_conversation', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.qa_conversation (
        conversation_id   UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
        user_id           NVARCHAR(128) NULL,
        title             NVARCHAR(256) NULL,
        created_at        DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at        DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
    );

    CREATE TABLE dbo.qa_message (
        message_id        UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
        conversation_id   UNIQUEIDENTIFIER NOT NULL,
        role              NVARCHAR(16) NOT NULL,
        content           NVARCHAR(MAX) NOT NULL,
        model_name        NVARCHAR(128) NULL,
        created_at        DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_qa_message_conversation FOREIGN KEY (conversation_id)
            REFERENCES dbo.qa_conversation(conversation_id) ON DELETE CASCADE,
        CONSTRAINT CK_qa_message_role CHECK (role IN (N'user', N'assistant', N'system'))
    );
    CREATE INDEX IX_qa_message_conversation ON dbo.qa_message(conversation_id, created_at);

    CREATE TABLE dbo.qa_feedback (
        feedback_id       UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
        conversation_id   UNIQUEIDENTIFIER NOT NULL,
        message_id        UNIQUEIDENTIFIER NOT NULL,
        rating            NVARCHAR(8) NOT NULL,
        comment           NVARCHAR(2000) NULL,
        created_at        DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_qa_feedback_message FOREIGN KEY (message_id)
            REFERENCES dbo.qa_message(message_id) ON DELETE CASCADE,
        CONSTRAINT FK_qa_feedback_conversation FOREIGN KEY (conversation_id)
            REFERENCES dbo.qa_conversation(conversation_id),
        CONSTRAINT CK_qa_feedback_rating CHECK (rating IN (N'up', N'down'))
    );
    CREATE INDEX IX_qa_feedback_message ON dbo.qa_feedback(message_id);
END;
