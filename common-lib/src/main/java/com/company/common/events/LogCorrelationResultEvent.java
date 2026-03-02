package com.company.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogCorrelationResultEvent {

    public static final String TOPIC = "log.correlation.result";

    private String incidentId;
    private String correlationId;
    private Map<String, List<LogEntry>> logsByService;
    private List<String> anomalies;          // pre-detected anomaly signals
    private List<String> errorPatterns;      // grouped error patterns
    private Instant correlatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogEntry {
        private Instant timestamp;
        private String level;       // ERROR, WARN, INFO
        private String service;
        private String traceId;
        private String spanId;
        private String message;
        private Map<String, String> labels;
    }
}
