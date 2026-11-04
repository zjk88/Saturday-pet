# Build the Spring Boot application from source.
FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

# Supply node, npm and npx for the Amap stdio MCP server.
FROM node:22-bookworm-slim AS node

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
COPY --from=node /usr/local/ /usr/local/
RUN npm install --global @amap/amap-maps-mcp-server     && npm cache clean --force     && mkdir -p /app/data /app/tmp
COPY --from=build /build/target/saturday-pet-0.0.1-SNAPSHOT.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod,mcp     AMAP_MCP_COMMAND=npx     SERVER_PORT=8123
EXPOSE 8123
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
