# ===== STAGE 1: Build =====
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# ===== STAGE 2: Run =====
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Ubuntu/Debian syntax (not Alpine)
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]