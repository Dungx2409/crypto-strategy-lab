# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

ARG APP_MODULE=api-app

# 1. Copy pom hierarchy first to leverage Docker layer caching for dependencies
COPY pom.xml .
COPY core/pom.xml core/
COPY infrastructure/pom.xml infrastructure/
COPY api-app/pom.xml api-app/
COPY worker-app/pom.xml worker-app/
COPY integration-tests/pom.xml integration-tests/

# Pre-fetch maven dependencies in a cached layer
RUN mvn dependency:go-offline -B -pl "${APP_MODULE}" -am || true

# 2. Copy source trees required for building the module
COPY core/src core/src
COPY infrastructure/src infrastructure/src
COPY ${APP_MODULE}/src ${APP_MODULE}/src

# 3. Package the target module using cached dependencies.
# Explicitly count the jar found so a wildcard match >1 (or 0) fails the build
# instead of silently copying the wrong artifact.
RUN mvn -pl "${APP_MODULE}" -am clean package -DskipTests \
    && JAR_COUNT=$(ls "${APP_MODULE}"/target/*-exec.jar 2>/dev/null | wc -l) \
    && if [ "${JAR_COUNT}" -ne 1 ]; then \
         echo "Expected exactly 1 *-exec.jar in ${APP_MODULE}/target, found ${JAR_COUNT}" >&2; \
         exit 1; \
       fi \
    && cp "${APP_MODULE}"/target/*-exec.jar /tmp/application.jar

# 4. Minimal runtime image.
# Pin to an exact Temurin patch version (not the floating "21-jre" tag) so the
# runtime doesn't silently change between CD runs. Bump this deliberately,
# check https://hub.docker.com/_/eclipse-temurin/tags for the current LTS patch.
FROM eclipse-temurin:21.0.12_8-jre

WORKDIR /app

# Run as non-root user for security
RUN groupadd -r appgroup && useradd -r -g appgroup -u 1001 appuser

COPY --from=build --chown=appuser:appgroup /tmp/application.jar application.jar

# Build metadata for traceability in CD (which commit/pipeline produced this image)
ARG GIT_SHA=unknown
ARG BUILD_DATE=unknown
ARG APP_MODULE=api-app
LABEL org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.title="crypto-strategy-lab-${APP_MODULE}"

USER appuser

EXPOSE 8080 8081

ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", \
            "/app/application.jar"]