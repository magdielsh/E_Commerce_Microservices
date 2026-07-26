FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar gateway.jar
ENTRYPOINT ["java", "-jar", "gateway.jar"]

