FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar product-service-0.0.1.jar
ENTRYPOINT ["java", "-jar", "product-service-0.0.1.jar"]