package com.company.incident.dto;

import com.company.common.model.IncidentStatus;
import com.company.common.model.Severity;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

public class IncidentDto {

    @Data
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "Title is required")
        private String title;

        private String description;

        @NotNull(message = "Severity is required")
        private Severity severity;

        @NotBlank(message = "Affected service is required")
        private String affectedService;

        private List<String> affectedRegions;
        private String reportedBy;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant occurredAt;
    }

    @Data
    @Builder
    public static class UpdateRequest {
        private String title;
        private String description;
        private Severity severity;
        private IncidentStatus status;
    }

    @Data
    @Builder
    public static class Response {
        private String id;
        private String title;
        private String description;
        private Severity severity;
        private IncidentStatus status;
        private String affectedService;
        private List<String> affectedRegions;
        private String reportedBy;

        // AI analysis results
        private String aiRootCause;
        private String aiRecommendedActions;
        private String aiRunbook;
        private String aiSummary;
        private boolean aiAnalysisAvailable;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant occurredAt;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant resolvedAt;

        private Long timeToResolveMinutes;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant createdAt;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant updatedAt;
    }

    @Data
    @Builder
    public static class SummaryResponse {
        private String id;
        private String title;
        private Severity severity;
        private IncidentStatus status;
        private String affectedService;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant occurredAt;
    }
}
