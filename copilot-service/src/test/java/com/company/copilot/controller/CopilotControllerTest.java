package com.company.copilot.controller;

import com.company.copilot.dto.ChatRequest;
import com.company.copilot.service.IncidentTriageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CopilotController.class)
class CopilotControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean IncidentTriageService triageService;
    @MockBean org.springframework.ai.chat.client.ChatClient chatClient;

    @Test
    void chat_returnsAiReply() throws Exception {
        when(triageService.chat(eq("INC-001"), anyString(), anyString()))
                .thenReturn("Rollback the deployment — restart won't help.");

        mockMvc.perform(post("/api/v1/copilot/incidents/INC-001/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChatRequest("Should I rollback?", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value("INC-001"))
                .andExpect(jsonPath("$.reply").value("Rollback the deployment — restart won't help."));
    }

    @Test
    void chat_emptyMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/copilot/incidents/INC-001/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
