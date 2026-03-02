package com.company.logagg.controller;

import com.company.common.events.LogCorrelationRequestEvent;
import com.company.logagg.service.LogCorrelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST interface for log-aggregator.
 *
 * Primary usage is Kafka-driven (automatic), but these
 * endpoints allow manual triggering for debugging and testing.
 */
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Log Aggregator", description = "Manual log correlation endpoints")
public class LogAggregatorController {

    private final LogCorrelationService logCorrelationService;

    /**
     * Manually trigger log correlation for an incident.
     * Useful when: logs were unavailable at incident creation time,
     * or you want to refresh correlation with a wider time window.
     */
    @PostMapping("/correlate")
    @Operation(summary = "Manually trigger log correlation for an incident")
    public ResponseEntity<Map<String, String>> triggerCorrelation(
            @RequestParam String incidentId,
            @RequestParam String affectedService,
            @RequestParam(defaultValue = "30") int windowMinutes) {

        log.info("Manual correlation trigger: incident={} service={} window={}m",
                incidentId, affectedService, windowMinutes);

        var request = LogCorrelationRequestEvent.builder()
                .incidentId(incidentId)
                .correlationId(UUID.randomUUID().toString())
                .serviceNames(List.of(affectedService))
                .from(Instant.now().minus(windowMinutes, ChronoUnit.MINUTES))
                .to(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        logCorrelationService.correlateLogsForIncident(request);

        return ResponseEntity.accepted().body(Map.of(
                "incidentId", incidentId,
                "status", "correlation triggered",
                "windowMinutes", String.valueOf(windowMinutes)
        ));
    }

    /** Health endpoint exposing more detail than /actuator/health */
    @GetMapping("/health")
    @Operation(summary = "Detailed health check for log aggregator")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "logSources", Map.of(
                        "loki",          "circuit-breaker-aware",
                        "elasticsearch", "circuit-breaker-aware",
                        "mock",          "always-available"
                )
        );
    }
}
