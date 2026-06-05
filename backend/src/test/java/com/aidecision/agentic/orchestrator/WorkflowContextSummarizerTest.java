package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.service.LlmSqlGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowContextSummarizerTest {

    @Mock
    private LlmSqlGenerationService llm;

    @InjectMocks
    private WorkflowContextSummarizer summarizer;

    @Test
    void summarize_withoutLlm_truncatesLongOutput() {
        when(llm.isChatConfigured()).thenReturn(false);
        String longText = "x".repeat(10_000);

        Map<String, String> out = summarizer.summarize(Map.of("s1", longText));

        assertThat(out.get("s1")).hasSizeLessThan(longText.length());
        assertThat(out.get("s1")).contains("truncated");
    }

    @Test
    void summarize_withLlm_usesChatComplete() throws Exception {
        when(llm.isChatConfigured()).thenReturn(true);
        when(llm.chatComplete(anyString(), anyString(), anyInt(), anyDouble()))
                .thenReturn("condensed facts");

        Map<String, String> out = summarizer.summarize(Map.of("s1", "{\"rows\":[]}"));

        assertThat(out.get("s1")).isEqualTo("condensed facts");
    }
}
