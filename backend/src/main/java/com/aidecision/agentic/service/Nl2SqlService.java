package com.aidecision.agentic.service;

import org.springframework.stereotype.Service;

@Service
public class Nl2SqlService {

    private final LlmSqlGenerationService sqlGeneration;

    public Nl2SqlService(LlmSqlGenerationService sqlGeneration) {
        this.sqlGeneration = sqlGeneration;
    }

    public String generateSql(String question, String userId, int maxRows) throws Exception {
        return sqlGeneration.generateSql(
                question, LlmSqlGenerationService.Mode.ANALYTICS, null, userId, maxRows);
    }
}
