# syntax=docker/dockerfile:1
# Etapa 1 - Build: geoapi es un proyecto Maven autónomo (no depende de common-lib)
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# Etapa 2 - Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/geoapi-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
