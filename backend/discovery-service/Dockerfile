FROM eclipse-temurin:17-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY target/discovery-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8760

CMD ["java", "-jar", "app.jar"]
