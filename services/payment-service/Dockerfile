# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY --chmod=0755 mvnw pom.xml ./
COPY .mvn/ .mvn/
COPY services/common/pom.xml services/common/pom.xml
COPY services/common/src/ services/common/src/
COPY services/auth-service/src/main/openapi/swagger.yaml services/auth-service/src/main/openapi/swagger.yaml
COPY services/payment-service/pom.xml services/payment-service/pom.xml
COPY services/payment-service/src/ services/payment-service/src/
COPY services/order-service/src/main/openapi/swagger.yaml services/order-service/src/main/openapi/swagger.yaml
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -ntp -DskipTests -N install \
 && ./mvnw -B -ntp -DskipTests -f services/common/pom.xml install \
 && ./mvnw -B -ntp -DskipTests -f services/payment-service/pom.xml clean package

FROM build AS layers
WORKDIR /work
RUN cp /workspace/services/payment-service/target/payment-service-*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination /work/extracted

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring \
 && apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
USER spring
COPY --from=layers /work/extracted/dependencies/ ./
COPY --from=layers /work/extracted/spring-boot-loader/ ./
COPY --from=layers /work/extracted/snapshot-dependencies/ ./
COPY --from=layers /work/extracted/application/ ./
EXPOSE 8085
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
