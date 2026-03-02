package com.company.copilot.dto;

import lombok.Data;

/**
 * Optional body for POST /api/v1/copilot/incidents/{id}/retriage
 * Allows passing extra context to influence the re-triage.
 */
@Data
public class RetriageRequest {
    private String additionalContext;
    private boolean forceRefresh = false;
}
