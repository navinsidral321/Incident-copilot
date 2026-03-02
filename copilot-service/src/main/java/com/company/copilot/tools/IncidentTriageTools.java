package com.company.copilot.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Tool functions exposed to GPT-4o during incident triage.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IncidentTriageTools {

    private final WebClient incidentServiceClient;
    private final StringRedisTemplate redisTemplate;

    @Tool(description = """
        Retrieve recent incidents for a given service from the last N days.
        """)
    public String getRecentIncidentsForService(
            @ToolParam(description = "The microservice name to look up, e.g. 'payment-service'")
                    String serviceName,
            @ToolParam(description = "Number of days to look back (max 30)")
                    int days) {

        log.debug("Tool call: getRecentIncidentsForService(service={}, days={})", serviceName, days);
        try {
            var response = incidentServiceClient.get()
                    .uri("/api/v1/incidents?service={s}&sort=occurredAt,desc&size=10", serviceName)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return response != null ? response : "No recent incidents found for " + serviceName;
        } catch (Exception ex) {
            log.warn("Could not fetch incidents for service [{}]: {}", serviceName, ex.getMessage());
            return "Unable to retrieve incident history for " + serviceName + " at this time.";
        }
    }

    @Tool(description = """
        Get the current health status and key metrics for a microservice.
        """)
    public Map<String, Object> getServiceHealth(
            @ToolParam(description = "The service name to check health for")
                    String serviceName) {

        log.debug("Tool call: getServiceHealth(service={})", serviceName);
        String healthKey = "health:" + serviceName;
        String cached = redisTemplate.opsForValue().get(healthKey);

        if (cached != null) {
            return Map.of("serviceName", serviceName, "status", cached, "source", "cache");
        }

        Instant now = Instant.now();
        return Map.of(
                "serviceName", serviceName,
                "status", "DEGRADED",
                "errorRate", "12.4%",
                "p99LatencyMs", 3840,
                "activeConnections", 145,
                "lastHealthyAt", now.minus(Duration.ofMinutes(25)).toString(),
                "checkedAt", now.toString()
        );
    }

    @Tool(description = """
        Look up the most recent deployment for a service.
        """)
    public Map<String, Object> getLastDeployment(
            @ToolParam(description = "The service that was deployed")
                    String serviceName) {

        log.debug("Tool call: getLastDeployment(service={})", serviceName);
        Instant now = Instant.now();
        return Map.of(
                "serviceName", serviceName,
                "version", "2.14.1",
                "previousVersion", "2.13.5",
                "deployedBy", "ci-pipeline",
                "deployedAt", now.minus(Duration.ofMinutes(45)).toString(),
                "changedComponents", List.of("PaymentProcessor", "RetryConfig", "DatabaseConnectionPool"),
                "deploymentNote", "Increased max DB pool size from 10 to 50, updated retry backoff"
        );
    }

    @Tool(description = """
        Fetch correlated error logs for a specific service within a time range.
        """)
    public String getErrorLogsForService(
            @ToolParam(description = "Name of the service to get error logs for")
                    String serviceName,
            @ToolParam(description = "ISO-8601 start time, e.g. 2024-01-15T10:00:00Z")
                    String fromTime,
            @ToolParam(description = "ISO-8601 end time, e.g. 2024-01-15T10:30:00Z")
                    String toTime) {

        log.debug("Tool call: getErrorLogsForService(service={}, from={}, to={})",
                serviceName, fromTime, toTime);
        return String.format("""
            [%s] ERROR logs from %s to %s:
            [10:02:14] ERROR - Connection refused to database pool after 30000ms timeout
            [10:03:01] ERROR - Circuit breaker OPEN for downstream inventory-service
            [10:04:22] ERROR - HTTP 503 from payment-gateway: Service Unavailable
            [10:05:10] ERROR - Thread pool exhausted: no available threads
            [10:06:33] ERROR - OOM Warning: heap at 92%% capacity
            Pattern: DB pool exhaustion → cascading circuit breaker trip → upstream timeout chain
            """, serviceName, fromTime, toTime);
    }

    @Tool(description = """
        Check if there are any ongoing cloud infrastructure alerts or platform-level issues.
        """)
    public Map<String, Object> getInfrastructureAlerts(
            @ToolParam(description = "Region or availability zone to check, e.g. 'eu-west-1'")
                    String region) {

        log.debug("Tool call: getInfrastructureAlerts(region={})", region);
        Instant now = Instant.now();

        return Map.of(
                "region", region,
                "activeAlerts", List.of(
                        Map.of("type", "NodePressure",
                                "severity", "WARNING",
                                "message", "Worker node eu-west-1c experiencing memory pressure",
                                "since", now.minus(Duration.ofMinutes(30)).toString()),
                        Map.of("type", "NetworkLatency",
                                "severity", "INFO",
                                "message", "Inter-AZ latency elevated by 18ms in eu-west-1",
                                "since", now.minus(Duration.ofMinutes(50)).toString())
                ),
                "checkedAt", now.toString()
        );
    }
}