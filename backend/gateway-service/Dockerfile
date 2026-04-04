FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY target/gateway-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8820

CMD ["java", "-jar", "app.jar"]
