# ── Stage 1: Build ─────────────────────────────────────────────────────────
# Uses the official Maven image instead of ./mvnw: on Windows checkouts mvnw often
# gets CRLF line endings, which breaks `./mvnw` inside Linux ("/bin/sh^M").
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src src
RUN mvn -B -q package -DskipTests

# ── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /app

# curl is needed by the Docker healthcheck (not included in the JRE image)
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r appuser && useradd -r -g appuser appuser     && mkdir -p /data/media && chown appuser:appuser /data/media

COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appuser app.jar
USER appuser

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
  CMD curl -fs http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
