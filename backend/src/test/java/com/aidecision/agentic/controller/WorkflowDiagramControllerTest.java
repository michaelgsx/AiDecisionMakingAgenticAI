package com.aidecision.agentic.controller;

import com.aidecision.agentic.dto.WorkflowDiagramResponse;
import com.aidecision.agentic.service.WorkflowDiagramService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkflowDiagramController.class)
class WorkflowDiagramControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @MockBean
    private WorkflowDiagramService diagrams;

    @Test
    void postDiagram_returnsMermaid() throws Exception {
        when(diagrams.renderFromJson(any(), any()))
                .thenReturn(new WorkflowDiagramResponse("mermaid", "flowchart TD\n  s1[\"s1\"]"));

        mockMvc.perform(post("/agent/workflow/diagram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workflowJson":"{\\"steps\\":[]}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("mermaid"))
                .andExpect(jsonPath("$.mermaid").value("flowchart TD\n  s1[\"s1\"]"));
    }

    @Test
    void getRunDiagram_returnsMermaid() throws Exception {
        UUID runId = UUID.randomUUID();
        when(diagrams.renderForRun(runId))
                .thenReturn(new WorkflowDiagramResponse("mermaid", "flowchart TD"));

        mockMvc.perform(get("/agent/runs/{runId}/workflow-diagram", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mermaid").value("flowchart TD"));
    }
}
