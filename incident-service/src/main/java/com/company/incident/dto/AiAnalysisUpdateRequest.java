package com.company.incident.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO received by incident-service when copilot-service
 * PATCHes the AI triage results back.
 *
 * Endpoint: PATCH /api/v1/incidents/{id}/ai-analysis
 *
 * All fields are nullable — copilot-service sends whatever
 * GPT-4o was able to produce even if parsing was partial.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisUpdateRequest {

    /** Plain-English root cause from GPT-4o */
    private String rootCause;

    /** JSON array string of ordered immediate action steps */
    private String recommendedActions;

    /** JSON-serialised TriageResult.Runbook object */
    private String runbook;

    /** 2-3 sentence exec summary for status page */
    private String summary;
}
