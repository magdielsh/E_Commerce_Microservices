FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar eureka-server.jar
ENTRYPOINT ["java", "-jar", "eureka-server.jar"]
