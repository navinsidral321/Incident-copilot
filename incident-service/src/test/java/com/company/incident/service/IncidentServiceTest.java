package com.company.incident.service;

import com.company.common.model.IncidentStatus;
import com.company.common.model.Severity;
import com.company.incident.dto.IncidentDto;
import com.company.incident.exception.IncidentNotFoundException;
import com.company.incident.model.Incident;
import com.company.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock IncidentRepository    incidentRepository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @Mock IncidentMapper        incidentMapper;

    @InjectMocks IncidentService incidentService;

    @Test
    void createIncident_savesAndPublishesKafkaEvent() {
        var request = IncidentDto.CreateRequest.builder()
                .title("DB connection pool exhausted")
                .severity(Severity.P1_CRITICAL)
                .affectedService("payment-service")
                .build();

        var entity = Incident.builder()
                .id("INC-001")
                .title(request.getTitle())
                .severity(request.getSeverity())
                .affectedService(request.getAffectedService())
                .affectedRegions(List.of())
                .build();

        var response = IncidentDto.Response.builder()
                .id("INC-001")
                .title(request.getTitle())
                .status(IncidentStatus.OPEN)
                .severity(Severity.P1_CRITICAL)
                .build();

        when(incidentMapper.toEntity(request)).thenReturn(entity);
        when(incidentRepository.save(any())).thenReturn(entity);
        when(incidentMapper.toResponse(entity)).thenReturn(response);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        IncidentDto.Response result = incidentService.createIncident(request);

        assertThat(result.getId()).isEqualTo("INC-001");
        assertThat(result.getStatus()).isEqualTo(IncidentStatus.OPEN);
        verify(incidentRepository).save(any(Incident.class));
        verify(kafkaTemplate).send(eq("incident.created"), eq("INC-001"), any());
    }

    @Test
    void getIncident_notFound_throwsNotFoundException() {
        when(incidentRepository.findById("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.getIncident("MISSING"))
                .isInstanceOf(IncidentNotFoundException.class)
                .hasMessageContaining("MISSING");
    }

    @Test
    void updateIncident_toResolved_setsResolvedAt() {
        var incident = Incident.builder()
                .id("INC-002")
                .status(IncidentStatus.TRIAGED)
                .occurredAt(Instant.now().minusSeconds(900))
                .affectedRegions(List.of())
                .build();

        var request = IncidentDto.UpdateRequest.builder()
                .status(IncidentStatus.RESOLVED)
                .build();

        when(incidentRepository.findById("INC-002")).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any())).thenReturn(incident);
        when(incidentMapper.toResponse(any())).thenReturn(IncidentDto.Response.builder()
                .id("INC-002").status(IncidentStatus.RESOLVED).build());

        var result = incidentService.updateIncident("INC-002", request);

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isNotNull();
        assertThat(incident.getTimeToResolveMinutes()).isGreaterThan(0);
    }

    @Test
    void applyAiAnalysis_callsRepositoryUpdate() {
        when(incidentRepository.updateAiAnalysis(any(), any(), any(), any(), any())).thenReturn(1);

        assertThatNoException().isThrownBy(() ->
                incidentService.applyAiAnalysis("INC-001", "DB pool exhausted",
                        "[\"rollback\"]", "{}", "Short summary"));

        verify(incidentRepository).updateAiAnalysis(
                eq("INC-001"), anyString(), anyString(), anyString(), anyString());
    }
}
