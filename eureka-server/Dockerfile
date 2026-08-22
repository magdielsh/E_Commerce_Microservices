FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="equipo-backend@tuempresa.com"
LABEL service="eureka-server"

# --- Usuario no-root ---
RUN groupadd -g 1000 spring && \
    useradd -u 1000 -g spring -s /usr/sbin/nologin -M spring

# curl solo para el HEALTHCHECK vía Spring Boot Actuator
#RUN apt-get update && \
#    apt-get install -y --no-install-recommends curl && \
#    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# ARG recibido desde Jenkins (buildSpringService.groovy) con la ruta exacta
# del jar compilado por Maven en el agente. El valor por defecto sirve para hacer
# builds manuales locales (docker build -f eureka-server/Dockerfile .)
#ARG JAR_FILE=eureka-server/target/*.jar

# Copiamos el jar YA compilado. Nota: la ruta de origen es relativa a la
# RAÍZ del monorepo (el build context), no a esta carpeta.
#COPY --chown=spring:spring ${JAR_FILE} eureka-server.jar

COPY --chown=spring:spring eureka-server/target/extracted/dependencies/ ./
COPY --chown=spring:spring eureka-server/target/extracted/spring-boot-loader/ ./
COPY --chown=spring:spring eureka-server/target/extracted/snapshot-dependencies/ ./
COPY --chown=spring:spring eureka-server/target/extracted/application/ ./

USER spring:spring

EXPOSE 7198

ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

#HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
#  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Volvemos a usar el JarLauncher de Spring Boot (no "java -jar app.jar"),
# porque ya no tenemos un jar único: tenemos las capas extraídas +
# el bootloader (spring-boot-loader/), que es justamente lo que
# org.springframework.boot.loader.launch.JarLauncher sabe arrancar.
# Forma "exec" para propagar correctamente SIGTERM (shutdown graceful).
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

