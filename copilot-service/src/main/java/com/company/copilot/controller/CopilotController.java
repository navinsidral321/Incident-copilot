package com.company.copilot.controller;

import com.company.copilot.dto.ChatRequest;
import com.company.copilot.dto.ChatResponse;
import com.company.copilot.service.IncidentTriageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/copilot")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Copilot", description = "AI-powered incident analysis and real-time chat")
public class CopilotController {

    private final IncidentTriageService triageService;
    private final ChatClient chatClient;

    /**
     * Real-time streaming chat — tokens streamed back as SSE.
     * Engineers can ask anything about the ongoing incident.
     */
    @PostMapping(value = "/incidents/{incidentId}/chat/stream",
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream a chat response about an incident (SSE)")
    public Flux<String> chatStream(
            @PathVariable String incidentId,
            @RequestBody ChatRequest request) {

        log.info("Streaming chat for incident [{}]: {}", incidentId, request.getMessage());

        return chatClient.prompt()
                .system("You are an SRE AI Copilot assisting with incident: " + incidentId
                        + ". Be concise, technical, and actionable.")
                .user(request.getMessage())
                .stream()
                .content();
    }

    /**
     * Non-streaming chat endpoint for simpler clients.
     */
    @PostMapping("/incidents/{incidentId}/chat")
    @Operation(summary = "Ask the AI Copilot a question about an incident")
    public ChatResponse chat(
            @PathVariable String incidentId,
            @RequestBody ChatRequest request) {

        String response = triageService.chat(incidentId, request.getMessage(),
                request.getConversationHistory() != null ? request.getConversationHistory() : "");
        return new ChatResponse(incidentId, response);
    }

    /**
     * Manually trigger a re-triage for an incident.
     */
    @PostMapping("/incidents/{incidentId}/retriage")
    @Operation(summary = "Manually trigger AI re-triage for an incident")
    public Map<String, String> retriage(@PathVariable String incidentId) {
        log.info("Manual re-triage requested for incident [{}]", incidentId);
        return Map.of(
                "incidentId", incidentId,
                "status", "re-triage requested",
                "message", "AI analysis will be updated shortly"
        );
    }

    /**
     * Generate a post-mortem draft as streaming SSE.
     */
    @PostMapping(value = "/incidents/{incidentId}/postmortem/stream",
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Generate a post-mortem document (streamed)")
    public Flux<String> generatePostmortem(@PathVariable String incidentId) {
        String prompt = """
                Generate a detailed post-mortem document for incident %s.
                Use the standard format: Summary, Impact, Timeline,
                Root Cause Analysis, Contributing Factors,
                Resolution Steps, Lessons Learned, Action Items.
                Be specific and include estimated timings.
                """.formatted(incidentId);

        return chatClient.prompt()
                .system("You are an expert SRE writing a post-mortem. Be thorough, blameless, and action-oriented.")
                .user(prompt)
                .stream()
                .content();
    }
}
