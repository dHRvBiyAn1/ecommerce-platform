# Runtime-only image. Build context is the repo root (set in docker-compose.yml
# via `context: ..`). Expects services/auth-service/target/*.jar to exist —
# build it first with the multi-module Maven reactor:
#
#   ./mvnw -B -ntp -DskipTests install
#   docker compose -f docker/docker-compose.yml build auth-service
#
# Spring Boot's layered jars are extracted at build time so dependency layers
# can be cached separately from application code.
FROM eclipse-temurin:17-jdk-jammy AS layers
WORKDIR /work
COPY services/auth-service/target/auth-service-1.0.0-SNAPSHOT.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract --destination /work/extracted

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system spring \
 && useradd --system --gid spring spring \
 && apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
USER spring
COPY --from=layers /work/extracted/dependencies/ ./
COPY --from=layers /work/extracted/spring-boot-loader/ ./
COPY --from=layers /work/extracted/snapshot-dependencies/ ./
COPY --from=layers /work/extracted/application/ ./
EXPOSE 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
