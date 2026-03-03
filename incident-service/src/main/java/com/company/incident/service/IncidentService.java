package com.company.incident.service;

import com.company.common.events.IncidentCreatedEvent;
import com.company.common.model.IncidentStatus;
import com.company.common.model.Severity;
import com.company.incident.dto.IncidentDto;
import com.company.incident.exception.IncidentNotFoundException;
import com.company.incident.model.Incident;
import com.company.incident.repository.IncidentRepository;
import com.company.incident.repository.IncidentSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final IncidentMapper incidentMapper;

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Transactional
    public IncidentDto.Response createIncident(IncidentDto.CreateRequest request) {
        Incident incident = incidentMapper.toEntity(request);
        incident.setStatus(IncidentStatus.OPEN);
        if (incident.getOccurredAt() == null) {
            incident.setOccurredAt(Instant.now());
        }

        Incident saved = incidentRepository.save(incident);
        log.info("Created incident [{}] severity={} service={}",
                saved.getId(), saved.getSeverity(), saved.getAffectedService());

        publishIncidentCreatedEvent(saved);
        return incidentMapper.toResponse(saved);
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public IncidentDto.Response getIncident(String id) {
        return incidentMapper.toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<IncidentDto.SummaryResponse> listIncidents(Pageable pageable) {
        return incidentRepository.findAll(pageable).map(incidentMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<IncidentDto.SummaryResponse> searchIncidents(
            String service, String severityStr, String statusStr, Pageable pageable) {

        Severity severity       = severityStr != null ? Severity.valueOf(severityStr) : null;
        IncidentStatus status   = statusStr   != null ? IncidentStatus.valueOf(statusStr) : null;
        Specification<Incident> spec = IncidentSpecification.byCriteria(service, severity, status);
        return incidentRepository.findAll(spec, pageable).map(incidentMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public List<IncidentDto.Response> getActiveCriticalIncidents() {
        return incidentRepository.findActiveCriticalIncidents()
                .stream().map(incidentMapper::toResponse).toList();
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @Transactional
    public IncidentDto.Response updateIncident(String id, IncidentDto.UpdateRequest request) {
        Incident incident = findOrThrow(id);

        if (request.getTitle()       != null) incident.setTitle(request.getTitle());
        if (request.getDescription() != null) incident.setDescription(request.getDescription());
        if (request.getSeverity()    != null) incident.setSeverity(request.getSeverity());
        if (request.getStatus()      != null) {
            incident.setStatus(request.getStatus());
            if (request.getStatus() == IncidentStatus.RESOLVED && incident.getResolvedAt() == null) {
                incident.setResolvedAt(Instant.now());
                long mins = (incident.getResolvedAt().toEpochMilli()
                           - incident.getOccurredAt().toEpochMilli()) / 60_000;
                incident.setTimeToResolveMinutes(mins);
                log.info("Incident [{}] resolved in {} minutes", id, mins);
            }
        }

        return incidentMapper.toResponse(incidentRepository.save(incident));
    }

    /**
     * Called by copilot-service via PATCH /api/v1/incidents/{id}/ai-analysis.
     * Uses a single @Modifying JPQL query — no need to load the full entity.
     */
    @Transactional
    public void applyAiAnalysis(String id, String rootCause,
                                 String actions, String runbook, String summary) {
        int updated = incidentRepository.updateAiAnalysis(id, rootCause, actions, runbook, summary);
        if (updated == 0) {
            log.warn("AI analysis PATCH missed incident [{}] — not found or version conflict", id);
        } else {
            log.info("AI analysis applied to incident [{}]", id);
        }
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    private Incident findOrThrow(String id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
    }

    private void publishIncidentCreatedEvent(Incident incident) {
        var event = IncidentCreatedEvent.builder()
                .incidentId(incident.getId())
                .title(incident.getTitle())
                .description(incident.getDescription())
                .severity(incident.getSeverity())
                .affectedService(incident.getAffectedService())
                .affectedRegions(incident.getAffectedRegions())
                .reportedBy(incident.getReportedBy())
                .occurredAt(incident.getOccurredAt())
                .build();

        kafkaTemplate.send(IncidentCreatedEvent.TOPIC, incident.getId(), event)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish IncidentCreatedEvent for [{}]",
                                incident.getId(), ex);
                    }
                });
    }
}
