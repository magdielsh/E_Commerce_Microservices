FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar product-service.jar
ENTRYPOINT ["java", "-jar", "product-service.jar"]