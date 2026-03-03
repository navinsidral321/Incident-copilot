package com.company.common.events;

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
public class LogCorrelationRequestEvent {

    public static final String TOPIC = "log.correlation.request";

    private String incidentId;
    private List<String> serviceNames;   // services to pull logs from
    private Instant from;
    private Instant to;
    private String correlationId;        // trace back to copilot session
}
