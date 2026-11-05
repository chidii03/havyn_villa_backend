# syntax=docker/dockerfile:1

# ---- deps/build stage --------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Gradle wrapper + build scripts first, source last — a source-only change doesn't
# invalidate the (slow) dependency-resolution layer below.
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ---- runtime stage -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Least-privilege runtime (prompt 27 constraint) — never root.
RUN addgroup -S havyn && adduser -S havyn -G havyn -u 1000
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
RUN chown havyn:havyn app.jar
USER 1000

EXPOSE 8080

# Use a bounded share of the container limit rather than host memory. The image
# remains configurable by overriding JAVA_TOOL_OPTIONS at runtime.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -Djava.io.tmpdir=/tmp"

HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=5 \
  CMD wget -q -O- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
