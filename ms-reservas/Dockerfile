# syntax=docker/dockerfile:1
# =========================================================================
# Etapa 1 - Build: compila ms-reservas (y common-lib) dentro del contenedor.
# El contexto de build es la RAÍZ del repo (ver docker-compose.yml) porque
# el módulo hereda del pom padre y depende de common-lib.
# =========================================================================
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Primero los pom.xml (Maven necesita todos los módulos declarados en el pom padre)
COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY ms-catalogo/pom.xml ms-catalogo/
COPY ms-reservas/pom.xml ms-reservas/
COPY geoapi/pom.xml geoapi/
COPY api-gateway/pom.xml api-gateway/

# Después solo el código que hace falta para este servicio
COPY common-lib/src common-lib/src
COPY ms-reservas/src ms-reservas/src

# -am compila también common-lib; la caché de ~/.m2 acelera los builds siguientes
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ms-reservas -am package -DskipTests

# =========================================================================
# Etapa 2 - Runtime: imagen liviana solo con el JRE 21 y el JAR
# =========================================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/ms-reservas/target/ms-reservas-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8082
# Formato exec: Java recibe SIGTERM y se apaga ordenadamente
ENTRYPOINT ["java", "-jar", "app.jar"]
