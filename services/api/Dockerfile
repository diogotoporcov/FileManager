# syntax=docker/dockerfile:1
ARG JDK_IMAGE=eclipse-temurin:25.0.3_9-jdk-alpine-3.23
ARG JRE_IMAGE=eclipse-temurin:25.0.3_9-jre-alpine-3.23

FROM ${JDK_IMAGE} AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle.kts ./
COPY gradle ./gradle
COPY services/api/build.gradle.kts services/api/build.gradle.kts
COPY services/api/src services/api/src
COPY services/gateway/build.gradle.kts services/gateway/build.gradle.kts

RUN chmod +x gradlew && ./gradlew --no-daemon :services:api:bootJar

FROM ${JRE_IMAGE}
RUN apk add --no-cache curl \
    && addgroup -S filemanager \
    && adduser -S filemanager -G filemanager

WORKDIR /app
COPY --from=build /workspace/services/api/build/libs/filemanager-api-0.0.1-SNAPSHOT.jar /app/app.jar

USER filemanager
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
