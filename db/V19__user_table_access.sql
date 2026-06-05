-- Per-user table ACL: SQL tools must resolve allowed tables here before reading schema_catalog_*.
-- Default user "admin" is seeded with every enabled catalog table so the lookup step always runs.

IF OBJECT_ID(N'dbo.user_table_access', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.user_table_access (
        access_id   UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
        user_id     NVARCHAR(128)    NOT NULL,
        table_name  NVARCHAR(128)    NOT NULL,
        created_at  DATETIME2        NOT NULL CONSTRAINT DF_user_table_access_created DEFAULT SYSUTCDATETIME(),
        CONSTRAINT UQ_user_table_access_user_table UNIQUE (user_id, table_name)
    );

    CREATE INDEX IX_user_table_access_user ON dbo.user_table_access (user_id);
END;

-- Seed admin with all currently enabled schema_catalog_table rows (idempotent).
IF OBJECT_ID(N'dbo.schema_catalog_table', N'U') IS NOT NULL
BEGIN
    INSERT INTO dbo.user_table_access (user_id, table_name)
    SELECT N'admin', t.table_name
    FROM dbo.schema_catalog_table t
    WHERE t.enabled = 1
      AND NOT EXISTS (
          SELECT 1 FROM dbo.user_table_access a
          WHERE a.user_id = N'admin' AND a.table_name = t.table_name
      );
END;
