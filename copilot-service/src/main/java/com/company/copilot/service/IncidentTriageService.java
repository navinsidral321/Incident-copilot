package com.company.copilot.service;

import com.company.common.events.IncidentCreatedEvent;
import com.company.common.events.LogCorrelationResultEvent;
import com.company.common.events.LogCorrelationResultEvent.LogEntry;
import com.company.copilot.config.CopilotPrompts;
import com.company.copilot.model.TriageResult;
import com.company.copilot.tools.IncidentTriageTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentTriageService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final IncidentTriageTools triageTools;
    private final ObjectMapper objectMapper;
    private final WebClient incidentServiceClient;

    /**
     * Full agentic triage pipeline:
     * 1. Retrieve similar past incidents from vector store (RAG)
     * 2. Build a rich context prompt from the incident + logs
     * 3. Let GPT-4o autonomously call tools to gather more context
     * 4. Parse the structured JSON response
     * 5. Persist the analysis back to incident-service
     * 6. Store the new incident as a vector for future RAG lookups
     */
    public TriageResult triageIncident(IncidentCreatedEvent incident,
                                        LogCorrelationResultEvent logs) {
        log.info("Starting AI triage for incident [{}]", incident.getIncidentId());

        // Step 1 — RAG: find similar past incidents
        String searchQuery = incident.getTitle() + " " + incident.getAffectedService()
                + " " + incident.getDescription();
        List<Document> similarDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(searchQuery)
                        .topK(5)
                        .similarityThreshold(0.75)
                        .build());

        String similarIncidentsContext = similarDocs.isEmpty()
                ? "No similar past incidents found."
                : formatSimilarIncidents(similarDocs);

        // Step 2 — Build user prompt
        String userPrompt = buildUserPrompt(incident, logs, similarIncidentsContext);

        // Step 3 — Call GPT-4o with tools enabled (agentic loop)
        String rawResponse = chatClient.prompt()
                .system(CopilotPrompts.TRIAGE_SYSTEM_PROMPT)
                .user(userPrompt)
                .tools(triageTools)    // Model can autonomously call these
                .call()
                .content();

        log.debug("Raw GPT-4o triage response for [{}]: {}", incident.getIncidentId(), rawResponse);

        // Step 4 — Parse structured response
        TriageResult result = parseTriageResult(rawResponse, incident.getIncidentId());

        // Step 5 — Persist analysis to incident-service
        persistAnalysis(incident.getIncidentId(), result);

        // Step 6 — Store in vector store for future RAG
        storeIncidentEmbedding(incident, result);

        log.info("Triage complete for incident [{}] — confidence={}, rootCause={}",
                incident.getIncidentId(), result.getConfidence(), result.getRootCause());

        return result;
    }

    /**
     * Interactive chat endpoint — allows engineers to ask follow-up questions
     * about the incident with full context maintained in the conversation.
     */
    public String chat(String incidentId, String userMessage, String conversationHistory) {
        String systemPrompt = CopilotPrompts.CHAT_SYSTEM_PROMPT
                .replace("{incidentId}", incidentId)
                .replace("{title}", "See incident record")
                .replace("{severity}", "See incident record")
                .replace("{rootCause}", "Refer to triage analysis")
                .replace("{status}", "IN_PROGRESS");

        return chatClient.prompt()
                .system(systemPrompt)
                .user(conversationHistory + "\n\nEngineer: " + userMessage)
                .tools(triageTools)
                .call()
                .content();
    }

    // ---- private helpers ----

    private String buildUserPrompt(IncidentCreatedEvent incident,
                                    LogCorrelationResultEvent logs,
                                    String similarIncidents) {
        String anomalies = logs != null && logs.getAnomalies() != null
                ? logs.getAnomalies().stream()
                    .map(a -> "  • " + a)
                    .collect(Collectors.joining("\n"))
                : "  • No anomaly data available yet";

        String errorPatterns = logs != null && logs.getErrorPatterns() != null
                ? logs.getErrorPatterns().stream()
                    .map(p -> "  • " + p)
                    .collect(Collectors.joining("\n"))
                : "  • No error patterns extracted yet";

        String logSample = logs != null && logs.getLogsByService() != null
                ? formatLogSample(logs.getLogsByService())
                : "  (Log correlation still in progress — use getErrorLogsForService tool)";

        return CopilotPrompts.TRIAGE_USER_PROMPT
                .replace("{incidentId}",    incident.getIncidentId())
                .replace("{title}",         incident.getTitle())
                .replace("{severity}",      incident.getSeverity().name())
                .replace("{affectedService}", incident.getAffectedService())
                .replace("{regions}",       formatList(incident.getAffectedRegions()))
                .replace("{occurredAt}",    incident.getOccurredAt().toString())
                .replace("{reportedBy}",    Optional.ofNullable(incident.getReportedBy()).orElse("automated-alert"))
                .replace("{description}",   Optional.ofNullable(incident.getDescription()).orElse("No description provided"))
                .replace("{anomalies}",     anomalies)
                .replace("{errorPatterns}", errorPatterns)
                .replace("{logSample}",     logSample)
                .replace("{similarIncidents}", similarIncidents);
    }

    private String formatLogSample(Map<String, List<LogEntry>> logsByService) {
        return logsByService.entrySet().stream()
                .map(entry -> {
                    String svcLogs = entry.getValue().stream()
                            .filter(l -> "ERROR".equals(l.getLevel()) || "WARN".equals(l.getLevel()))
                            .limit(5)
                            .map(l -> "  [%s] %s %s - %s".formatted(
                                    l.getTimestamp(), l.getLevel(), l.getService(), l.getMessage()))
                            .collect(Collectors.joining("\n"));
                    return "### " + entry.getKey() + "\n" + svcLogs;
                })
                .collect(Collectors.joining("\n\n"));
    }

    private String formatSimilarIncidents(List<Document> docs) {
        return docs.stream()
                .map(doc -> "- " + doc.getText())
                .collect(Collectors.joining("\n"));
    }

    private String formatList(List<String> items) {
        return items == null || items.isEmpty() ? "unknown" : String.join(", ", items);
    }

    private TriageResult parseTriageResult(String rawResponse, String incidentId) {
        // Strip markdown code fences if present
        String json = rawResponse
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();

        try {
            return objectMapper.readValue(json, TriageResult.class);
        } catch (JsonProcessingException ex) {
            log.warn("Could not parse structured triage JSON for [{}], creating fallback result", incidentId);
            TriageResult fallback = new TriageResult();
            fallback.setRootCause("AI analysis completed but structured output could not be parsed.");
            fallback.setSummary(rawResponse.substring(0, Math.min(rawResponse.length(), 500)));
            fallback.setConfidence("LOW");
            return fallback;
        }
    }

    private void persistAnalysis(String incidentId, TriageResult result) {
        try {
            String runbookJson = objectMapper.writeValueAsString(result.getRunbook());
            String actionsJson = objectMapper.writeValueAsString(result.getImmediateActions());

            incidentServiceClient.patch()
                    .uri("/api/v1/incidents/{id}/ai-analysis", incidentId)
                    .bodyValue(Map.of(
                            "rootCause",          result.getRootCause(),
                            "recommendedActions", actionsJson,
                            "runbook",            runbookJson,
                            "summary",            result.getSummary()
                    ))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("Persisted AI analysis to incident-service for [{}]", incidentId);
        } catch (Exception ex) {
            log.error("Failed to persist AI analysis for incident [{}]", incidentId, ex);
        }
    }

    private void storeIncidentEmbedding(IncidentCreatedEvent incident, TriageResult result) {
        String content = """
                Incident: %s
                Service: %s
                Severity: %s
                Root Cause: %s
                Resolution: %s
                """.formatted(
                incident.getTitle(),
                incident.getAffectedService(),
                incident.getSeverity(),
                result.getRootCause(),
                result.getImmediateActions() != null
                        ? String.join("; ", result.getImmediateActions())
                        : "N/A"
        );

        Document doc = new Document(content, Map.of(
                "incidentId",      incident.getIncidentId(),
                "service",         incident.getAffectedService(),
                "severity",        incident.getSeverity().name(),
                "occurredAt",      incident.getOccurredAt().toString()
        ));

        vectorStore.add(List.of(doc));
        log.debug("Stored incident [{}] embedding in vector store for future RAG", incident.getIncidentId());
    }
}
