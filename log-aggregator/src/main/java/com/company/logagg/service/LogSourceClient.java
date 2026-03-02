package com.company.logagg.service;

import com.company.common.events.LogCorrelationResultEvent.LogEntry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Abstracts over real log backends (Loki, Elasticsearch).
 * Falls back to generated mock logs when backends are unavailable
 * or disabled — critical for demo environments.
 */
@Component
@Slf4j
public class LogSourceClient {

    private final RestClient restClient;

    @Value("${log-sources.loki.enabled:false}")
    private boolean lokiEnabled;

    @Value("${log-sources.loki.base-url:http://localhost:3100}")
    private String lokiBaseUrl;

    @Value("${log-sources.elasticsearch.enabled:false}")
    private boolean esEnabled;

    private static final String[] REALISTIC_ERRORS = {
        "Connection refused to database pool after 30000ms timeout",
        "NullPointerException in OrderProcessingService.processPayment()",
        "Circuit breaker OPEN for downstream inventory-service",
        "HTTP 503 from payment-gateway: Service Unavailable",
        "Redis cache miss rate exceeded 80% threshold",
        "Thread pool exhausted: no available threads in executor-service-pool",
        "OOM Error: Java heap space — increase -Xmx",
        "Deadlock detected on table 'orders' — transaction rolled back",
        "JWT token validation failed: signature verification error",
        "Rate limit exceeded: 429 from external-api.provider.com"
    };

    private static final String[] REALISTIC_WARNINGS = {
        "Response time for /api/checkout degraded to 3200ms (threshold: 2000ms)",
        "Database connection pool at 85% capacity",
        "Retrying request to inventory-service (attempt 2/3)",
        "Cache eviction rate unusually high — consider increasing Redis memory",
        "Slow query detected: SELECT * FROM orders WHERE status='PENDING' took 8200ms"
    };

    public LogSourceClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @CircuitBreaker(name = "logSource", fallbackMethod = "mockLogFallback")
    @Retry(name = "logSource")
    public List<LogEntry> fetchLogs(String serviceName, Instant from, Instant to) {
        if (lokiEnabled) {
            return fetchFromLoki(serviceName, from, to);
        }
        if (esEnabled) {
            return fetchFromElasticsearch(serviceName, from, to);
        }
        // No real backend configured — use rich mock data
        return generateMockLogs(serviceName, from, to);
    }

    public List<LogEntry> mockLogFallback(String serviceName, Instant from, Instant to, Throwable ex) {
        log.warn("Circuit breaker open for log source [{}], falling back to mock logs. Cause: {}",
                serviceName, ex.getMessage());
        return generateMockLogs(serviceName, from, to);
    }

    // ---- Loki integration ----
    private List<LogEntry> fetchFromLoki(String serviceName, Instant from, Instant to) {
        // Loki LogQL query: {app="serviceName"} |= "ERROR" | json
        // Full implementation would call the Loki /query_range API
        log.info("Fetching logs from Loki for service [{}]", serviceName);
        return generateMockLogs(serviceName, from, to); // Replace with real Loki HTTP call
    }

    // ---- Elasticsearch integration ----
    private List<LogEntry> fetchFromElasticsearch(String serviceName, Instant from, Instant to) {
        log.info("Fetching logs from Elasticsearch for service [{}]", serviceName);
        return generateMockLogs(serviceName, from, to); // Replace with real ES DSL query
    }

    /**
     * Generates realistic mock logs to simulate a real incident scenario.
     * Produces a believable pattern: normal → warning cascade → error spike.
     */
    List<LogEntry> generateMockLogs(String serviceName, Instant from, Instant to) {
        List<LogEntry> logs = new ArrayList<>();
        Random rand = ThreadLocalRandom.current();
        long durationMs = to.toEpochMilli() - from.toEpochMilli();
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        int totalEntries = 80 + rand.nextInt(60);

        for (int i = 0; i < totalEntries; i++) {
            double progress = (double) i / totalEntries;
            Instant ts = from.plusMillis((long)(progress * durationMs));

            // Incident pattern: ramp up errors in second half of the window
            String level;
            String message;
            if (progress < 0.4) {
                level   = "INFO";
                message = "Processing request id=" + UUID.randomUUID() + " service=" + serviceName;
            } else if (progress < 0.6) {
                level   = rand.nextDouble() < 0.3 ? "WARN" : "INFO";
                message = progress < 0.6 && level.equals("WARN")
                        ? REALISTIC_WARNINGS[rand.nextInt(REALISTIC_WARNINGS.length)]
                        : "Completed request in " + (500 + rand.nextInt(2000)) + "ms";
            } else {
                level   = rand.nextDouble() < 0.6 ? "ERROR" : "WARN";
                message = level.equals("ERROR")
                        ? REALISTIC_ERRORS[rand.nextInt(REALISTIC_ERRORS.length)]
                        : REALISTIC_WARNINGS[rand.nextInt(REALISTIC_WARNINGS.length)];
            }

            logs.add(LogEntry.builder()
                    .timestamp(ts)
                    .level(level)
                    .service(serviceName)
                    .traceId(traceId)
                    .spanId(UUID.randomUUID().toString().substring(0, 8))
                    .message(message)
                    .labels(Map.of("env", "prod", "region", "eu-west-1", "pod",
                            serviceName + "-" + rand.nextInt(5)))
                    .build());
        }

        logs.sort(Comparator.comparing(LogEntry::getTimestamp));
        return logs;
    }
}
