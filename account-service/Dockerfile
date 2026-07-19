FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar account-service.jar
ENTRYPOINT ["java", "-jar", "account-service.jar"]