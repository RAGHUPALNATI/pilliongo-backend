# ---------- Stage 1: build the jar ----------
# A full JDK + Maven image compiles the code and packages it. Nothing from
# this stage ends up in the final image except the jar.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy pom.xml first and download dependencies. Docker caches this layer,
# so later builds only re-download when pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY src ./src
# Tests already ran in the CI job before this; skip them here.
RUN mvn -B -q package -DskipTests

# ---------- Stage 2: the small image that actually runs ----------
# Only a Java runtime (no compiler, no Maven, no source code).
FROM eclipse-temurin:21-jre
WORKDIR /app

# Don't run the app as root inside the container.
RUN useradd --system --no-create-home pilliongo
USER pilliongo

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

# MaxRAMPercentage: let Java use up to 70% of the container's memory limit,
# leaving room for everything else. Sized for a 1 GB server.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-jar", "app.jar"]
