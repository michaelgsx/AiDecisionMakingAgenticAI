package com.aidecision.agentic.service;

import com.aidecision.agentic.entity.PlannerWorkflowCache;
import com.aidecision.agentic.orchestrator.PlannerPrompt;
import com.aidecision.agentic.repository.PlannerWorkflowCacheRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerWorkflowCacheServiceTest {

    @Mock
    private PlannerWorkflowCacheRepository cacheRepo;

    private PlannerWorkflowCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new PlannerWorkflowCacheService(cacheRepo, new ObjectMapper());
    }

    @Test
    void normalizeQuestion_trimsCollapsesAndLowercases() {
        assertThat(PlannerWorkflowCacheService.normalizeQuestion("  What  is   Risk?  "))
                .isEqualTo("what is risk?");
    }

    @Test
    void questionHash_sameAfterNormalization() {
        String a = PlannerWorkflowCacheService.questionHash("What is risk?");
        String b = PlannerWorkflowCacheService.questionHash("  WHAT   IS  risk?  ");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void questionHash_differsForDifferentQuestions() {
        String a = PlannerWorkflowCacheService.questionHash("What is risk?");
        String b = PlannerWorkflowCacheService.questionHash("What is fraud?");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void findByQuestion_usesNormalizedHash() {
        String question = "Show me recent logins";
        String hash = PlannerWorkflowCacheService.questionHash(question);
        PlannerWorkflowCache row = new PlannerWorkflowCache();
        row.setQuestionHash(hash);
        row.setWorkflowJson("{\"status\":\"ok\",\"steps\":[]}");
        when(cacheRepo.findByQuestionHash(hash)).thenReturn(Optional.of(row));

        Optional<PlannerWorkflowCache> hit = cacheService.findByQuestion("  SHOW   me recent logins  ");

        assertThat(hit).isPresent();
        assertThat(hit.get().getWorkflowJson()).contains("status");
    }

    @Test
    void save_persistsPromptAndWorkflowJson() throws Exception {
        when(cacheRepo.findByQuestionHash(any())).thenReturn(Optional.empty());
        when(cacheRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlannerPrompt prompt = new PlannerPrompt("system", "user", "{}");
        String workflowJson = "{\"status\":\"ok\",\"steps\":[{\"id\":\"s1\",\"tool\":\"llm_answer\",\"dependsOn\":[],\"params\":{}}]}";

        PlannerWorkflowCache saved = cacheService.save("What is risk?", prompt, workflowJson);

        ArgumentCaptor<PlannerWorkflowCache> captor = ArgumentCaptor.forClass(PlannerWorkflowCache.class);
        verify(cacheRepo).save(captor.capture());
        PlannerWorkflowCache row = captor.getValue();
        assertThat(row.getQuestion()).isEqualTo("What is risk?");
        assertThat(row.getQuestionHash()).isEqualTo(PlannerWorkflowCacheService.questionHash("What is risk?"));
        assertThat(row.getPlannerPrompt()).contains("system").contains("user");
        assertThat(row.getWorkflowJson()).isEqualTo(workflowJson);
        assertThat(saved.getWorkflowJson()).isEqualTo(workflowJson);
    }

    @Test
    void save_updatesExistingRowForSameQuestion() throws Exception {
        String hash = PlannerWorkflowCacheService.questionHash("What is risk?");
        PlannerWorkflowCache existing = new PlannerWorkflowCache();
        existing.setQuestionHash(hash);
        existing.setWorkflowJson("{\"status\":\"ok\",\"steps\":[]}");
        when(cacheRepo.findByQuestionHash(hash)).thenReturn(Optional.of(existing));
        when(cacheRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlannerPrompt prompt = new PlannerPrompt("system2", "user2", "{}");
        String updatedJson = "{\"status\":\"ok\",\"steps\":[{\"id\":\"s1\",\"tool\":\"data_acquisition\",\"dependsOn\":[],\"params\":{}}]}";

        cacheService.save("What is risk?", prompt, updatedJson);

        ArgumentCaptor<PlannerWorkflowCache> captor = ArgumentCaptor.forClass(PlannerWorkflowCache.class);
        verify(cacheRepo).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getWorkflowJson()).isEqualTo(updatedJson);
        assertThat(captor.getValue().getPlannerPrompt()).contains("system2");
    }
}
