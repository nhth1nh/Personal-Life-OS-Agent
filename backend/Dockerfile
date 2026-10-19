# ── Runtime stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-noble

WORKDIR /app

RUN mkdir -p /app/logs

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
