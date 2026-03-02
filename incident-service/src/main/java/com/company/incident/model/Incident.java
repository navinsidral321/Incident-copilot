package com.company.incident.model;

import com.company.common.model.IncidentStatus;
import com.company.common.model.Severity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "incidents", indexes = {
    @Index(name = "idx_incident_status",   columnList = "status"),
    @Index(name = "idx_incident_severity", columnList = "severity"),
    @Index(name = "idx_incident_service",  columnList = "affected_service")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Column(name = "affected_service", nullable = false)
    private String affectedService;

    @ElementCollection
    @CollectionTable(name = "incident_affected_regions",
                     joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "region")
    @Builder.Default
    private List<String> affectedRegions = new ArrayList<>();

    @Column(name = "reported_by")
    private String reportedBy;

    // AI-generated fields (populated by copilot-service)
    @Column(name = "ai_root_cause", columnDefinition = "TEXT")
    private String aiRootCause;

    @Column(name = "ai_recommended_actions", columnDefinition = "TEXT")
    private String aiRecommendedActions;

    @Column(name = "ai_runbook", columnDefinition = "TEXT")
    private String aiRunbook;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "time_to_resolve_minutes")
    private Long timeToResolveMinutes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;  // Optimistic locking
}
