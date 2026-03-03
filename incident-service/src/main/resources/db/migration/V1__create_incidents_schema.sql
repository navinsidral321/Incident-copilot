-- V1__create_incidents_schema.sql
-- Flyway migration: initial schema for incident-service
-- Runs automatically on startup before any queries execute.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE incidents (
    id                        VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    title                     VARCHAR(500) NOT NULL,
    description               TEXT,
    severity                  VARCHAR(20)  NOT NULL,
    status                    VARCHAR(30)  NOT NULL DEFAULT 'OPEN',
    affected_service          VARCHAR(100) NOT NULL,
    reported_by               VARCHAR(100),

    -- AI analysis fields populated by copilot-service via PATCH
    ai_root_cause             TEXT,
    ai_recommended_actions    TEXT,
    ai_runbook                TEXT,
    ai_summary                TEXT,

    -- Timing
    occurred_at               TIMESTAMPTZ,
    resolved_at               TIMESTAMPTZ,
    time_to_resolve_minutes   BIGINT,

    -- Audit / optimistic locking
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                   BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE incident_affected_regions (
    incident_id VARCHAR(36) NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    region      VARCHAR(50) NOT NULL,
    PRIMARY KEY (incident_id, region)
);

-- Indexes for the most common query patterns
CREATE INDEX idx_incidents_status          ON incidents(status);
CREATE INDEX idx_incidents_severity        ON incidents(severity);
CREATE INDEX idx_incidents_service         ON incidents(affected_service);
CREATE INDEX idx_incidents_occurred_at     ON incidents(occurred_at DESC);
CREATE INDEX idx_incidents_severity_status ON incidents(severity, status);
-- Partial index: fast P1 active incident lookups for NOC dashboard
CREATE INDEX idx_incidents_p1_active       ON incidents(occurred_at DESC)
    WHERE severity = 'P1_CRITICAL' AND status NOT IN ('RESOLVED', 'POST_MORTEM_PENDING');
