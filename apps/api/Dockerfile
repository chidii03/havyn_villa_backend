# syntax=docker/dockerfile:1
# Multi-stage build (prompt 27) — build context is apps/api/ itself (see
# infra/docker-compose.yml's `build: context: apps/api`), so paths here are relative
# to this file, not the monorepo root.

# ---- deps/build stage --------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Gradle wrapper + build scripts first, source last — a source-only change doesn't
# invalidate the (slow) dependency-resolution layer below.
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ---- runtime stage -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Least-privilege runtime (prompt 27 constraint) — never root.
RUN addgroup -S havyn && adduser -S havyn -G havyn
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
RUN chown havyn:havyn app.jar
USER havyn

EXPOSE 8080

# Alpine's busybox wget is built in — no extra package needed. Mirrors
# management.health.readinessstate (application.yml) — an "UP" body means both the
# app and its DB/Redis dependencies are actually reachable, not just that the JVM
# process is alive.
HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=5 \
  CMD wget -q -O- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
