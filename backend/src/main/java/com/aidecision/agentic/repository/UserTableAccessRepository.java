package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.UserTableAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserTableAccessRepository extends JpaRepository<UserTableAccess, UUID> {

    @Query("SELECT DISTINCT a.tableName FROM UserTableAccess a WHERE a.userId = :userId ORDER BY a.tableName")
    List<String> findDistinctTableNamesByUserId(@Param("userId") String userId);
}
