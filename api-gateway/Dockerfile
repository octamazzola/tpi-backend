# 1. Imagen base: JRE 21 oficial de Eclipse Temurin sobre Linux Alpine minimalista (~150MB total)
FROM eclipse-temurin:21-jre-alpine

# 2. Directorio de trabajo dentro del sistema de archivos del contenedor
WORKDIR /app

# 3. Copiamos el JAR ejecutable generado por Maven en el host y lo renombramos a app.jar
COPY target/api-gateway-1.0.0-SNAPSHOT.jar app.jar

# 4. Documentación: informamos el puerto en el que escucha Spring Boot (Gateway)
EXPOSE 8080

# 5. Comando de arranque: formato exec para propagar señales de terminación ordenadamente
ENTRYPOINT ["java", "-jar", "app.jar"]
