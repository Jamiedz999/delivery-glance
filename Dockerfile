# syntax=docker/dockerfile:1

FROM node:24-alpine AS web-build
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk AS server-build
WORKDIR /server
COPY server/.mvn/ .mvn/
COPY server/mvnw ./
COPY server/pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
COPY server/src ./src
COPY --from=web-build /web/dist ./src/main/resources/static
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=server-build /server/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=5s --retries=10 --start-period=20s \
    CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
