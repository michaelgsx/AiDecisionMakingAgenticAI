package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.SchemaCatalogColumn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchemaCatalogColumnRepository extends JpaRepository<SchemaCatalogColumn, java.util.UUID> {
    List<SchemaCatalogColumn> findByTableNameAndEnabledTrueOrderByColumnNameAsc(String tableName);
    List<SchemaCatalogColumn> findByEnabledTrueOrderByTableNameAscColumnNameAsc();
}
