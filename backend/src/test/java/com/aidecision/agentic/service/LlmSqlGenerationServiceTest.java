package com.aidecision.agentic.service;

import com.aidecision.agentic.config.AzureOpenAiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmSqlGenerationServiceTest {

    @Mock
    private AzureOpenAiProperties openAi;
    @Mock
    private SchemaCatalogService catalog;
    @Mock
    private UserTableAccessService userTableAccess;
    @Mock
    private RestClient http;

    @InjectMocks
    private LlmSqlGenerationService service;

    @Test
    void generateSql_whenChatNotConfigured_usesAcquisitionFallback() throws Exception {
        when(openAi.chatConfigured()).thenReturn(false);
        when(userTableAccess.intersectCandidates(any(), any()))
                .thenReturn(List.of("risk_features"));

        String sql = service.generateSql(
                "user withdrawals?",
                LlmSqlGenerationService.Mode.DATA_ACQUISITION,
                List.of("risk_features"),
                "admin");

        assertThat(sql).containsIgnoringCase("risk_features");
        assertThat(sql).startsWith("SELECT");
    }

    @Test
    void extractSql_stripsMarkdownFence() {
        String sql = LlmSqlGenerationService.extractSql("```sql\nSELECT TOP 1 id FROM t\n```");
        assertThat(sql).isEqualTo("SELECT TOP 1 id FROM t");
    }
}
