FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar gateway-0.0.1-0.0.1.jar
ENTRYPOINT ["java", "-jar", "gateway-0.0.1.jar"]