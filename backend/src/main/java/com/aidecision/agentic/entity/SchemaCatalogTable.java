package com.aidecision.agentic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "schema_catalog_table")
public class SchemaCatalogTable {

    @Id
    @Column(name = "table_name", length = 128)
    private String tableName;

    @Column(name = "schema_name", length = 64, nullable = false)
    private String schemaName = "dbo";

    @Column(name = "description", length = 2000, nullable = false)
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
