package com.company.incident.repository;

import com.company.common.model.IncidentStatus;
import com.company.common.model.Severity;
import com.company.incident.model.Incident;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification factory for dynamic incident filtering.
 *
 * Used by IncidentService.searchIncidents() to build queries like:
 *   GET /incidents/search?service=payment-service&severity=P1_CRITICAL&status=OPEN
 *
 * Avoids N hard-coded repository methods for every filter combination.
 */
public class IncidentSpecification {

    private IncidentSpecification() {}

    /**
     * Build a compound WHERE clause from optional filter parameters.
     * Null values are simply skipped (no filter applied for that field).
     */
    public static Specification<Incident> byCriteria(
            String affectedService,
            Severity severity,
            IncidentStatus status) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (affectedService != null && !affectedService.isBlank()) {
                predicates.add(cb.equal(root.get("affectedService"), affectedService));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Incident> occurredBetween(Instant from, Instant to) {
        return (root, query, cb) -> cb.between(root.get("occurredAt"), from, to);
    }

    public static Specification<Incident> hasAiAnalysis() {
        return (root, query, cb) -> cb.isNotNull(root.get("aiRootCause"));
    }

    /** Returns only incidents that are not yet resolved */
    public static Specification<Incident> isActive() {
        return (root, query, cb) ->
                root.get("status").in(List.of(
                        IncidentStatus.OPEN,
                        IncidentStatus.TRIAGED,
                        IncidentStatus.IN_PROGRESS
                ));
    }
}
