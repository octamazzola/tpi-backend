# 1. Imagen base: JRE 21 oficial de Eclipse Temurin sobre Linux Alpine minimalista (~150MB total)
FROM eclipse-temurin:21-jre-alpine

# 2. Directorio de trabajo dentro del sistema de archivos del contenedor
WORKDIR /app

# 3. Copiamos el JAR ejecutable generado por Maven en el host y lo renombramos a app.jar
COPY target/ms-catalogo-1.0.0-SNAPSHOT.jar app.jar

# 4. Documentación: informamos el puerto en el que escucha Spring Boot
EXPOSE 8081

# 5. Comando de arranque: usa el formato exec ['...'] para que Java reciba las señales SIGTERM y apague ordenadamente
ENTRYPOINT ["java", "-jar", "app.jar"]
