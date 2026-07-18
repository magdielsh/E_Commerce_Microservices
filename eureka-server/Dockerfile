FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar eureka-server-0.0.1.jar
ENTRYPOINT ["java", "-jar", "eureka-server-0.0.1.jar"]