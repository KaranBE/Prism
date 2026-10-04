# Multi-stage build: run from the repo root, e.g.
#   docker build -f prism-gateway-service/Dockerfile -t prism-gateway-service .
FROM mirror.gcr.io/library/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY prism-common ./prism-common
COPY prism-cache-service ./prism-cache-service
COPY prism-gateway-service ./prism-gateway-service
COPY prism-usage-service ./prism-usage-service
RUN mvn -q -pl prism-gateway-service -am -DskipTests package

FROM mirror.gcr.io/library/eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/prism-gateway-service/target/prism-gateway-service.jar app.jar
EXPOSE 8080 8081 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
