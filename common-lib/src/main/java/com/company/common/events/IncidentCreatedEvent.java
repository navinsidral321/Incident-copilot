package com.company.common.events;

import com.company.common.model.Severity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;



import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentCreatedEvent {

    public static final String TOPIC = "incident.created";

    private String incidentId;
    private String title;
    private String description;
    private Severity severity;
    private String affectedService;
    private List<String> affectedRegions;
    private String reportedBy;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant occurredAt;
}
