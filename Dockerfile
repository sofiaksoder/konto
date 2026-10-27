# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so this layer is cached until the pom changes.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src src
# Tests run in CI against a real MariaDB (Testcontainers); the image build only packages.
RUN mvn -B -ntp -DskipTests package

# ---- run ----
# Pinned to Ubuntu 22.04 (jammy): the default 24.04-based tag segfaults on some Docker Desktop hosts.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd --system konto && useradd --system --gid konto --no-create-home konto
COPY --from=build /build/target/*.jar app.jar
USER konto

EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "app.jar"]
