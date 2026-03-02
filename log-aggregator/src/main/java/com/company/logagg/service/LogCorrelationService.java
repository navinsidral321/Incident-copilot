package com.company.logagg.service;


import com.company.common.events.LogCorrelationRequestEvent;
import com.company.common.events.LogCorrelationResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Aggregates logs from multiple services, detects error patterns and anomalies,
 * then publishes a correlation result for the copilot-service to analyse.
 *
 * In production, replace generateMockLogs() with calls to Loki/Elasticsearch
 * via the injected LogSourceClient (guarded by Resilience4j circuit breaker).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogCorrelationService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;
    private final LogSourceClient logSourceClient;   // abstracts Loki / ES

    private static final String CACHE_PREFIX = "log:correlation:";
    private static final Duration CACHE_TTL   = Duration.ofMinutes(15);

    public void correlateLogsForIncident(LogCorrelationRequestEvent request) {
        String cacheKey = CACHE_PREFIX + request.getIncidentId();

        // Guard: don't re-correlate if already done and cached
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
            log.info("Log correlation result already cached for incident [{}], skipping",
                    request.getIncidentId());
            return;
        }

        Map<String, List<LogCorrelationResultEvent.LogEntry>> logsByService = new LinkedHashMap<>();

        for (String serviceName : request.getServiceNames()) {
            List<LogCorrelationResultEvent.LogEntry> logs = logSourceClient.fetchLogs(
                    serviceName, request.getFrom(), request.getTo());
            logsByService.put(serviceName, logs);
            log.info("Fetched {} log entries from service [{}] for incident [{}]",
                    logs.size(), serviceName, request.getIncidentId());
        }

        List<String> anomalies    = detectAnomalies(logsByService);
        List<String> errorPatterns = extractErrorPatterns(logsByService);

        LogCorrelationResultEvent result = LogCorrelationResultEvent.builder()
                .incidentId(request.getIncidentId())
                .correlationId(request.getCorrelationId())
                .logsByService(logsByService)
                .anomalies(anomalies)
                .errorPatterns(errorPatterns)
                .correlatedAt(Instant.now())
                .build();

        kafkaTemplate.send(LogCorrelationResultEvent.TOPIC, request.getIncidentId(), result)
                .whenComplete((res, ex) -> {
                    if (ex == null) {
                        redisTemplate.opsForValue().set(cacheKey, "done", CACHE_TTL);
                        log.info("Published LogCorrelationResultEvent for incident [{}] — {} anomalies, {} patterns",
                                request.getIncidentId(), anomalies.size(), errorPatterns.size());
                    } else {
                        log.error("Failed to publish log correlation result for [{}]",
                                request.getIncidentId(), ex);
                    }
                });
    }

    // ----- Analysis helpers -----

    private List<String> detectAnomalies(Map<String, List<LogCorrelationResultEvent.LogEntry>> logsByService) {
        List<String> anomalies = new ArrayList<>();

        logsByService.forEach((service, logs) -> {
            long errorCount = logs.stream()
                    .filter(l -> "ERROR".equals(l.getLevel()))
                    .count();

            long warnCount = logs.stream()
                    .filter(l -> "WARN".equals(l.getLevel()))
                    .count();

            if (errorCount > 10) {
                anomalies.add(String.format(
                    "[%s] ANOMALY: %d errors in window — significantly above baseline", service, errorCount));
            }
            if (warnCount > 50) {
                anomalies.add(String.format(
                    "[%s] ANOMALY: %d warnings — potential degradation cascade", service, warnCount));
            }

            // Detect error spike in last 10% of the time window
            long totalLogs  = logs.size();
            long recentErrors = logs.stream()
                    .skip((long)(totalLogs * 0.9))
                    .filter(l -> "ERROR".equals(l.getLevel()))
                    .count();
            if (recentErrors > 5) {
                anomalies.add(String.format(
                    "[%s] ANOMALY: Error spike of %d in recent window — incident may be worsening",
                    service, recentErrors));
            }
        });

        return anomalies;
    }

    private List<String> extractErrorPatterns(Map<String, List<LogCorrelationResultEvent.LogEntry>> logsByService) {
        return logsByService.entrySet().stream()
                .flatMap(entry -> {
                    String service = entry.getKey();
                    return entry.getValue().stream()
                            .filter(l -> "ERROR".equals(l.getLevel()))
                            .collect(Collectors.groupingBy(
                                l -> normaliseMessage(l.getMessage()),
                                Collectors.counting()))
                            .entrySet().stream()
                            .filter(e -> e.getValue() > 1)
                            .map(e -> String.format("[%s] %dx: %s", service, e.getValue(), e.getKey()));
                })
                .sorted()
                .toList();
    }

    /** Strip dynamic parts (IDs, timestamps, IPs) to group similar errors */
    private String normaliseMessage(String message) {
        if (message == null) return "";
        return message
                .replaceAll("[0-9a-f]{8}-[0-9a-f-]{27}", "<uuid>")
                .replaceAll("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b", "<ip>")
                .replaceAll("\\b\\d+\\b", "<n>")
                .trim();
    }
}
