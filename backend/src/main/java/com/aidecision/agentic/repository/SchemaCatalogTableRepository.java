package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.SchemaCatalogTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchemaCatalogTableRepository extends JpaRepository<SchemaCatalogTable, String> {
    List<SchemaCatalogTable> findByEnabledTrueOrderByTableNameAsc();
}
