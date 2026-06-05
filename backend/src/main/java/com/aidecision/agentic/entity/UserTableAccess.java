package com.aidecision.agentic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_table_access")
public class UserTableAccess {

    @Id
    @Column(name = "access_id", columnDefinition = "uniqueidentifier")
    private UUID accessId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "table_name", nullable = false, length = 128)
    private String tableName;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME2")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (accessId == null) {
            accessId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getAccessId() { return accessId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public Instant getCreatedAt() { return createdAt; }
}
