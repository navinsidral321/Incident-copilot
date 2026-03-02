package com.company.copilot.event;

import com.company.common.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class IncidentEventConsumer {

    private final IncidentTriageService triageService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${copilot.log-window-minutes:30}")
    private int logWindowMinutes;

    // Holds correlation results temporarily until the incident event is ready to triage
    private final java.util.concurrent.ConcurrentHashMap<String, LogCorrelationResultEvent>
            pendingCorrelations = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * When an incident is created:
     * 1. Request log correlation from log-aggregator (async)
     * 2. Start a background triage (may use partial or no log data if logs are slow)
     */
    @KafkaListener(topics = IncidentCreatedEvent.TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onIncidentCreated(
            @Payload IncidentCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment ack) {

        log.info("Copilot received IncidentCreatedEvent: id=[{}] service=[{}] severity=[{}]",
                event.getIncidentId(), event.getAffectedService(), event.getSeverity());

        // Request log correlation
        String correlationId = UUID.randomUUID().toString();
        publishLogCorrelationRequest(event, correlationId);

        ack.acknowledge();

        // If high priority, start immediate triage with or without logs
        if (event.getSeverity().isHighPriority()) {
            CompletableFuture.runAsync(() -> {
                // Wait briefly for logs to arrive before triaging
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}

                LogCorrelationResultEvent logs = pendingCorrelations.remove(correlationId);
                log.info("Starting PRIORITY triage for [{}] (logs available: {})",
                        event.getIncidentId(), logs != null);
                triageService.triageIncident(event, logs);
            });
        }
    }

    /**
     * When log correlation results arrive, trigger triage if not already started.
     */
    @KafkaListener(topics = LogCorrelationResultEvent.TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onLogCorrelationResult(
            @Payload LogCorrelationResultEvent event,
            Acknowledgment ack) {

        log.info("Received log correlation for incident [{}] — {} services, {} anomalies",
                event.getIncidentId(),
                event.getLogsByService() != null ? event.getLogsByService().size() : 0,
                event.getAnomalies() != null ? event.getAnomalies().size() : 0);

        // Cache for 10 minutes in case the incident event triggers triage after this arrives
        redisTemplate.opsForValue().set(
                "log:result:" + event.getIncidentId(),
                event.getIncidentId(),  // Just a marker; real data held in memory map
                Duration.ofMinutes(10));

        pendingCorrelations.put(event.getCorrelationId(), event);
        ack.acknowledge();
    }

    private void publishLogCorrelationRequest(IncidentCreatedEvent incident, String correlationId) {
        Instant to   = incident.getOccurredAt().plus(Duration.ofMinutes(5));
        Instant from = incident.getOccurredAt().minus(Duration.ofMinutes(logWindowMinutes));

        var request = LogCorrelationRequestEvent.builder()
                .incidentId(incident.getIncidentId())
                .serviceNames(incident.getAffectedRegions() != null
                        ? List.of(incident.getAffectedService())
                        : List.of(incident.getAffectedService()))
                .from(from)
                .to(to)
                .correlationId(correlationId)
                .build();

        kafkaTemplate.send(LogCorrelationRequestEvent.TOPIC, incident.getIncidentId(), request)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish LogCorrelationRequestEvent for [{}]",
                                incident.getIncidentId(), ex);
                    }
                });
    }
}
