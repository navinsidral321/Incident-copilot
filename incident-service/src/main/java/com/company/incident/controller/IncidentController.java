package com.company.incident.controller;

import com.company.incident.dto.AiAnalysisUpdateRequest;
import com.company.incident.dto.IncidentDto;
import com.company.incident.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
@Tag(name = "Incidents", description = "Incident lifecycle management")
public class IncidentController {

    private final IncidentService incidentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create incident — triggers AI triage pipeline via Kafka automatically")
    public IncidentDto.Response create(@Valid @RequestBody IncidentDto.CreateRequest request) {
        return incidentService.createIncident(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full incident details including AI analysis fields once available")
    public IncidentDto.Response get(@PathVariable String id) {
        return incidentService.getIncident(id);
    }

    @GetMapping
    @Operation(summary = "List all incidents paginated — sort by occurredAt, severity, etc.")
    public Page<IncidentDto.SummaryResponse> list(
            @PageableDefault(size = 20, sort = "occurredAt") Pageable pageable) {
        return incidentService.listIncidents(pageable);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update incident status or editable fields")
    public IncidentDto.Response update(
            @PathVariable String id,
            @RequestBody IncidentDto.UpdateRequest request) {
        return incidentService.updateIncident(id, request);
    }

    @PatchMapping("/{id}/ai-analysis")
    @Operation(summary = "Internal — copilot-service calls this to write AI triage results back")
    public ResponseEntity<Void> applyAiAnalysis(
            @PathVariable String id,
            @RequestBody AiAnalysisUpdateRequest request) {
        incidentService.applyAiAnalysis(id,
                request.getRootCause(),
                request.getRecommendedActions(),
                request.getRunbook(),
                request.getSummary());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/critical/active")
    @Operation(summary = "All active P1_CRITICAL incidents — for NOC dashboard polling")
    public List<IncidentDto.Response> activeCritical() {
        return incidentService.getActiveCriticalIncidents();
    }

    @GetMapping("/search")
    @Operation(summary = "Dynamic search by service, severity, and status")
    public Page<IncidentDto.SummaryResponse> search(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "occurredAt") Pageable pageable) {
        return incidentService.searchIncidents(service, severity, status, pageable);
    }
}
