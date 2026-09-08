# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY --chmod=0755 mvnw pom.xml ./
COPY .mvn/ .mvn/
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -ntp -DskipTests -N install
COPY services/common/pom.xml services/common/pom.xml
COPY services/common/src/ services/common/src/
COPY services/coupon-service/pom.xml services/coupon-service/pom.xml
COPY services/coupon-service/src/ services/coupon-service/src/
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -ntp -DskipTests -f services/common/pom.xml install \
 && ./mvnw -B -ntp -DskipTests -f services/coupon-service/pom.xml clean package

FROM build AS layers
WORKDIR /work
RUN cp /workspace/services/coupon-service/target/coupon-service-*.jar app.jar \
 && java -Djarmode=layertools -jar app.jar extract --destination /work/extracted

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring \
 && apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
USER spring
COPY --from=layers /work/extracted/dependencies/ ./
COPY --from=layers /work/extracted/spring-boot-loader/ ./
COPY --from=layers /work/extracted/snapshot-dependencies/ ./
COPY --from=layers /work/extracted/application/ ./
EXPOSE 8088
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
