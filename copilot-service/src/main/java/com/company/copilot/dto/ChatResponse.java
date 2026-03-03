package com.company.copilot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for POST /api/v1/copilot/incidents/{id}/chat
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String incidentId;
    private String reply;
}
