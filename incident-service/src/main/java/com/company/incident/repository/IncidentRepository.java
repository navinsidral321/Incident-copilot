package com.company.incident.repository;

import com.company.common.model.IncidentStatus;
import com.company.common.model.Severity;
import com.company.incident.model.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, String>,
        JpaSpecificationExecutor<Incident> {

    Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);

    Page<Incident> findBySeverity(Severity severity, Pageable pageable);

    Page<Incident> findByAffectedService(String service, Pageable pageable);

    List<Incident> findByStatusIn(List<IncidentStatus> statuses);

    @Query("""
        SELECT i FROM Incident i
        WHERE i.affectedService = :service
          AND i.occurredAt BETWEEN :from AND :to
        ORDER BY i.occurredAt DESC
        """)
    List<Incident> findByServiceAndTimeRange(
            @Param("service") String service,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
        SELECT i FROM Incident i
        WHERE i.severity = 'P1_CRITICAL'
          AND i.status NOT IN ('RESOLVED', 'POST_MORTEM_PENDING')
        ORDER BY i.occurredAt DESC
        """)
    List<Incident> findActiveCriticalIncidents();

    @Modifying
    @Query("""
        UPDATE Incident i
        SET i.aiRootCause = :rootCause,
            i.aiRecommendedActions = :actions,
            i.aiRunbook = :runbook,
            i.aiSummary = :summary,
            i.status = 'TRIAGED'
        WHERE i.id = :id
        """)
    int updateAiAnalysis(
            @Param("id") String id,
            @Param("rootCause") String rootCause,
            @Param("actions") String actions,
            @Param("runbook") String runbook,
            @Param("summary") String summary
    );

    @Query("""
        SELECT COUNT(i) FROM Incident i
        WHERE i.affectedService = :service
          AND i.severity = :severity
          AND i.occurredAt >= :since
        """)
    long countBySeverityAndServiceSince(
            @Param("service") String service,
            @Param("severity") Severity severity,
            @Param("since") Instant since
    );

    Optional<Incident> findTopByAffectedServiceAndStatusNotOrderByOccurredAtDesc(
            String service, IncidentStatus status);
}
