package com.company.logagg.service;

import com.company.common.events.LogCorrelationResultEvent.LogEntry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LogSourceClientTest {

    private final LogSourceClient client = new LogSourceClient(mock(RestClient.Builder.class));

    @Test
    void generatedLogsShowIncidentPattern() {
        Instant from = Instant.now().minus(30, ChronoUnit.MINUTES);
        Instant to   = Instant.now();

        List<LogEntry> logs = client.generateMockLogs("payment-service", from, to);

        assertThat(logs).isNotEmpty();
        assertThat(logs).allMatch(l -> l.getTimestamp() != null);
        assertThat(logs).allMatch(l -> l.getLevel() != null);

        // Logs must be time-ordered
        for (int i = 1; i < logs.size(); i++) {
            assertThat(logs.get(i).getTimestamp())
                    .isAfterOrEqualTo(logs.get(i - 1).getTimestamp());
        }

        // Incident pattern: both errors and info lines present
        long errorCount = logs.stream().filter(l -> "ERROR".equals(l.getLevel())).count();
        long infoCount  = logs.stream().filter(l -> "INFO".equals(l.getLevel())).count();
        assertThat(errorCount).isGreaterThan(0);
        assertThat(infoCount).isGreaterThan(0);
    }
}
