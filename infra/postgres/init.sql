-- Create application databases
CREATE DATABASE incidents;
CREATE DATABASE copilot;

-- incidents DB schema
\connect incidents;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE incidents (
    id                    VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    title                 VARCHAR(500) NOT NULL,
    description           TEXT,
    severity              VARCHAR(20)  NOT NULL,
    status                VARCHAR(30)  NOT NULL DEFAULT 'OPEN',
    affected_service      VARCHAR(100) NOT NULL,
    reported_by           VARCHAR(100),
    ai_root_cause         TEXT,
    ai_recommended_actions TEXT,
    ai_runbook            TEXT,
    ai_summary            TEXT,
    occurred_at           TIMESTAMPTZ,
    resolved_at           TIMESTAMPTZ,
    time_to_resolve_minutes BIGINT,
    created_at            TIMESTAMPTZ DEFAULT NOW(),
    updated_at            TIMESTAMPTZ DEFAULT NOW(),
    version               BIGINT DEFAULT 0
);

CREATE TABLE incident_affected_regions (
    incident_id  VARCHAR(36) REFERENCES incidents(id) ON DELETE CASCADE,
    region       VARCHAR(50),
    PRIMARY KEY (incident_id, region)
);

CREATE INDEX idx_incident_status   ON incidents(status);
CREATE INDEX idx_incident_severity ON incidents(severity);
CREATE INDEX idx_incident_service  ON incidents(affected_service);
CREATE INDEX idx_incident_occurred ON incidents(occurred_at DESC);

-- copilot DB schema (pgvector for RAG)
\connect copilot;

CREATE EXTENSION IF NOT EXISTS vector;

-- Spring AI PgVectorStore table
CREATE TABLE IF NOT EXISTS vector_store (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT,
    metadata  JSON,
    embedding vector(1536)
);

CREATE INDEX ON vector_store USING hnsw (embedding vector_cosine_ops);
