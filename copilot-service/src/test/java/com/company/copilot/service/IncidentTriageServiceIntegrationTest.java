package com.company.copilot.service;

import com.company.common.events.IncidentCreatedEvent;
import com.company.common.events.LogCorrelationResultEvent;
import com.company.common.model.Severity;
import com.company.copilot.model.TriageResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class IncidentTriageServiceIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private IncidentTriageService triageService;

    @MockBean
    private ChatClient chatClient;

    @MockBean
    private VectorStore vectorStore;

    @Test
    void shouldTriageIncidentAndReturnStructuredResult() {
        // Given
        var incident = IncidentCreatedEvent.builder()
                .incidentId("INC-001")
                .title("Payment service returning 503 errors")
                .description("Customers unable to complete checkout for 15 minutes")
                .severity(Severity.P1_CRITICAL)
                .affectedService("payment-service")
                .affectedRegions(List.of("eu-west-1"))
                .reportedBy("automated-alert")
                .occurredAt(Instant.now())
                .build();

        var logResult = LogCorrelationResultEvent.builder()
                .incidentId("INC-001")
                .correlationId("corr-001")
                .logsByService(Map.of("payment-service", List.of(
                        LogCorrelationResultEvent.LogEntry.builder()
                                .timestamp(Instant.now())
                                .level("ERROR")
                                .service("payment-service")
                                .message("Connection refused to database pool")
                                .build()
                )))
                .anomalies(List.of("[payment-service] ANOMALY: 15 errors in window"))
                .errorPatterns(List.of("[payment-service] 15x: Connection refused to database pool"))
                .correlatedAt(Instant.now())
                .build();

        String mockAiResponse = """
                {
                  "severity_assessment": "P1 - full checkout outage affecting all customers",
                  "root_cause": "Database connection pool exhaustion in payment-service",
                  "confidence": "HIGH",
                  "evidence": ["15x Connection refused to database pool", "Error spike in last 10 minutes"],
                  "immediate_actions": ["Restart payment-service pods", "Scale up DB connection pool"],
                  "runbook": {
                    "diagnosis_steps": ["Check DB connection pool metrics", "Review recent deployments"],
                    "mitigation_steps": ["kubectl rollout restart deployment/payment-service"],
                    "rollback_procedure": "Revert to previous deployment if restart fails",
                    "escalation_path": "Page DBA team if DB-level issue confirmed"
                  },
                  "blast_radius": "All customers attempting checkout",
                  "prevention": ["Implement connection pool monitoring alerts", "Add circuit breaker for DB layer"],
                  "summary": "Payment service DB pool exhausted causing 503 errors for all checkout attempts."
                }
                """;

        // Mock ChatClient fluent API chain
        var callSpec    = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        var promptSpec  = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        var toolSpec    = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        var systemSpec  = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        var userSpec    = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);

        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.system(any(String.class))).thenReturn(systemSpec);
        when(systemSpec.user(any(String.class))).thenReturn(userSpec);
        when(userSpec.tools(any())).thenReturn(toolSpec);
        when(toolSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(mockAiResponse);
        when(vectorStore.similaritySearch(any())).thenReturn(List.of());

        // When
        TriageResult result = triageService.triageIncident(incident, logResult);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRootCause()).contains("Database connection pool");
        assertThat(result.getConfidence()).isEqualTo("HIGH");
        assertThat(result.getImmediateActions()).hasSize(2);
        assertThat(result.getRunbook()).isNotNull();
        assertThat(result.getRunbook().getMitigationSteps()).isNotEmpty();
    }
}
