FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
ARG APP_MODULE
COPY . .
RUN mvn -pl "${APP_MODULE}" -am package -DskipTests \
    && cp "${APP_MODULE}"/target/*-exec.jar /tmp/application.jar

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /tmp/application.jar application.jar
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
