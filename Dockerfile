# ── Multi-stage Dockerfile (used by all services) ──────────────────────────
# Build: docker build --build-arg SERVICE=copilot-service -t copilot-service .
# ─────────────────────────────────────────────────────────────────────────────

ARG SERVICE=copilot-service

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

ARG SERVICE

COPY gradle gradle
COPY gradlew .
COPY settings.gradle .
COPY build.gradle .

# Copy all subprojects
COPY common-lib common-lib
COPY incident-service incident-service
COPY log-aggregator log-aggregator
COPY copilot-service copilot-service
COPY api-gateway api-gateway

RUN ./gradlew :${SERVICE}:bootJar -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

ARG SERVICE
ENV SERVICE_NAME=${SERVICE}

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app
COPY --from=builder /workspace/${SERVICE}/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
