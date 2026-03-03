# 🚨 AI-Powered Production Incident Copilot

> **Enterprise-grade, event-driven AI assistant for real-time incident triage, root cause analysis, and multi-service log correlation — built on Java 21, Spring Boot 3, Spring AI, and Apache Kafka.**

---

| Capability | Implementation |
|---|---|
| **Agentic AI** | GPT-4o autonomously calls tools (service health, deployment history, logs) before forming conclusions |
| **RAG Pipeline** | pgvector similarity search retrieves similar past incidents to improve triage quality |
| **Event-Driven Architecture** | Kafka-based async pipeline decouples all 4 microservices — no synchronous coupling |
| **Distributed Systems Patterns** | Circuit breakers, retries, optimistic locking, idempotency guards, Redis caching |
| **Observability** | Micrometer → Prometheus → Grafana; structured logging with trace IDs |
| **Production Hardening** | Test containers integration tests, multi-stage Docker builds, health checks |
| **Cloud-Native** | Container-first design, environment variable config, 12-factor compliant |
| **OpenStack/Cloud Alignment** | Infrastructure alert tool calls OpenStack Alarm API; region-aware log correlation |

---

## 🏗️ Architecture

```
                         ┌─────────────────────────────────────────────────────────┐
                         │                    API Gateway :8080                    │
                         │          Spring Cloud Gateway + Circuit Breakers         │
                         └───────┬─────────────────────┬───────────────────────────┘
                                 │                     │
               ┌─────────────────▼──────┐   ┌──────────▼──────────────┐
               │   incident-service     │   │    copilot-service       │
               │        :8081           │   │         :8083            │
               │  • CRUD incidents      │   │  • Spring AI / GPT-4o   │
               │  • PostgreSQL          │   │  • Agentic tool calling  │
               │  • Publishes events    │   │  • RAG (pgvector)        │
               │  • REST API            │   │  • SSE streaming chat    │
               └────────────┬───────────┘   └──────────┬───────────────┘
                            │                          │
                            └──────────┬───────────────┘
                                       │  Apache Kafka
                              ┌────────▼──────────────────┐
                              │  Topics:                  │
                              │  • incident.created       │
                              │  • log.correlation.request│
                              │  • log.correlation.result │
                              └────────┬──────────────────┘
                                       │
                         ┌─────────────▼──────────────┐
                         │      log-aggregator         │
                         │           :8082             │
                         │  • Multi-service log pull   │
                         │  • Anomaly detection        │
                         │  • Error pattern extraction │
                         │  • Loki / ES integration    │
                         │  • Redis result caching     │
                         └─────────────────────────────┘
```

---

## 🤖 How the AI Triage Pipeline Works

When an incident is created, the following happens **automatically**:

```
1. incident-service  →  saves to PostgreSQL
                     →  publishes IncidentCreatedEvent to Kafka

2. log-aggregator    ←  consumes IncidentCreatedEvent
                     →  pulls logs from the 30-min window before incident
                     →  runs anomaly detection + error pattern extraction
                     →  publishes LogCorrelationResultEvent

3. copilot-service   ←  consumes BOTH events
                     →  searches pgvector for similar past incidents (RAG)
                     →  calls GPT-4o with full context + tool access
                        GPT-4o autonomously calls:
                         • getRecentIncidentsForService()
                         • getServiceHealth()
                         • getLastDeployment()       ← deployment correlation!
                         • getErrorLogsForService()
                         • getInfrastructureAlerts() ← OpenStack/K8s alerts
                     →  parses structured JSON response
                     →  PATCH incident-service with root cause + runbook
                     →  stores new incident embedding for future RAG
```

---

## 📁 Project Structure

```
incident-copilot/
├── settings.gradle                     # Monorepo root
├── build.gradle                        # Shared dependencies + plugins
├── docker-compose.yml                  # Full local stack
├── Dockerfile                          # Multi-stage build for all services
│
├── common-lib/                         # Shared DTOs, events, domain model
│   └── src/main/java/com/company/common/
│       ├── model/    Severity, IncidentStatus
│       ├── events/   IncidentCreatedEvent, LogCorrelationRequest/ResultEvent
│       └── dto/      ApiError
│
├── incident-service/                   # Incident CRUD + Kafka publisher
│   └── src/main/java/com/company/incident/
│       ├── model/      Incident (JPA entity, optimistic locking)
│       ├── repository/ IncidentRepository (custom JPQL queries)
│       ├── service/    IncidentService, IncidentMapper (MapStruct)
│       ├── controller/ IncidentController (REST)
│       └── exception/  GlobalExceptionHandler
│
├── log-aggregator/                     # Log fetching + anomaly detection
│   └── src/main/java/com/company/logagg/
│       ├── service/ LogCorrelationService, LogSourceClient (CB + Retry)
│       └── event/   LogCorrelationConsumer
│
├── copilot-service/                    # 🤖 AI core
│   └── src/main/java/com/company/copilot/
│       ├── config/  CopilotConfig (ChatClient, VectorStore), CopilotPrompts
│       ├── tools/   IncidentTriageTools (@Tool methods for GPT-4o)
│       ├── service/ IncidentTriageService (agentic triage pipeline)
│       ├── event/   IncidentEventConsumer
│       ├── model/   TriageResult
│       └── controller/ CopilotController (REST + SSE streaming)
│
├── api-gateway/                        # Spring Cloud Gateway
│   └── application.yml                 # Routes, circuit breakers, rate limiting
│
└── infra/
    ├── postgres/init.sql               # pgvector setup, schema
    └── prometheus/prometheus.yml       # Metrics scraping
```

---

## ⚡ Quick Start

### Prerequisites

- Java 21
- Docker & Docker Compose
- OpenAI API key

### 1. Clone and configure

```bash
git clone <repo>
cd incident-copilot
export OPENAI_API_KEY=sk-...
```

### 2. Start the full stack

```bash
docker-compose up -d
```

Services will be available at:
| Service | URL |
|---|---|
| API Gateway | http://localhost:8080 |
| Incident Service | http://localhost:8081/swagger-ui.html |
| Log Aggregator | http://localhost:8082 |
| Copilot Service | http://localhost:8083/swagger-ui.html |
| Kafka UI | http://localhost:9090 |
| Prometheus | http://localhost:9091 |
| Grafana | http://localhost:3000 |

### 3. Trigger a full AI triage

```bash
# Create a P1 incident — this triggers the full async pipeline
curl -X POST http://localhost:8080/api/v1/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Payment service returning 503 errors - checkout completely down",
    "description": "All customers unable to complete purchases. Error rate at 100%. Started 15 minutes ago.",
    "severity": "P1_CRITICAL",
    "affectedService": "payment-service",
    "affectedRegions": ["eu-west-1"],
    "reportedBy": "pagerduty-alert"
  }'

# Note the incident ID from the response, then poll for AI analysis:
curl http://localhost:8080/api/v1/incidents/{incidentId}

# Chat with the copilot about the incident:
curl -X POST http://localhost:8080/api/v1/copilot/incidents/{incidentId}/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Should I roll back the deployment from 45 minutes ago?"}'

# Stream a post-mortem document:
curl -N http://localhost:8080/api/v1/copilot/incidents/{incidentId}/postmortem/stream
```

---

## 🧪 Running Tests

```bash
# All unit + integration tests (requires Docker for Testcontainers)
./gradlew test

# Specific service
./gradlew :copilot-service:test
./gradlew :incident-service:test
./gradlew :log-aggregator:test

# With test report
./gradlew test jacocoTestReport
```
## 🛣️ Production Readiness Checklist

- [x] Structured JSON logging with correlation IDs
- [x] Prometheus metrics on all services
- [x] Health endpoints with deep checks
- [x] Circuit breakers + retries on all inter-service calls
- [x] Optimistic locking to prevent concurrent update conflicts
- [x] Kafka consumer idempotency guards (Redis deduplication)
- [x] Multi-stage Docker builds (non-root user, minimal JRE)
- [x] Environment variable configuration (12-factor)
- [x] Testcontainers integration tests
- [ ] Distributed tracing (add Micrometer Tracing + Zipkin/Jaeger)
- [ ] Secret management (Vault / AWS Secrets Manager)
- [ ] Kubernetes Helm chart
- [ ] GitHub Actions CI/CD pipeline

---

## 📚 Tech Stack Reference

| Layer | Technology |
|---|---|
| Language | Java 21 (records, virtual threads friendly) |
| Framework | Spring Boot 3.3 |
| AI | Spring AI 1.0.0-M6 + OpenAI GPT-4o |
| RAG | pgvector + Spring AI VectorStore |
| Messaging | Apache Kafka + Spring Kafka |
| Persistence | PostgreSQL + Spring Data JPA + Hibernate |
| Caching | Redis + Spring Data Redis |
| Gateway | Spring Cloud Gateway |
| Resilience | Resilience4j (CB, Retry, Rate Limiter) |
| Observability | Micrometer + Prometheus + Grafana |
| Testing | JUnit 5 + Testcontainers + Mockito |
| Build | Gradle 8 (monorepo) |
| Containerisation | Docker + Docker Compose |
