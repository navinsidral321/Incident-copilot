package com.company.copilot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Structured output from GPT-4o triage analysis.
 * Mapped from the JSON response defined in the system prompt.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TriageResult {

    private String severityAssessment;
    private String rootCause;
    private String confidence;     // HIGH / MEDIUM / LOW
    private List<String> evidence;
    private List<String> immediateActions;
    private Runbook runbook;
    private String blastRadius;
    private List<String> prevention;
    private String summary;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Runbook {
        private List<String> diagnosisSteps;
        private List<String> mitigationSteps;
        private String rollbackProcedure;
        private String escalationPath;
    }
}
