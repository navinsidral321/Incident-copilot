package com.company.copilot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/v1/copilot/incidents/{id}/chat
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "message is required")
    private String message;

    /** Optional: pass prior conversation turns to maintain context */
    private String conversationHistory;
}
