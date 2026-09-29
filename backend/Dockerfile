FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

ARG MAVEN_PROXY_HOST=""
ARG MAVEN_PROXY_PORT=12450
ENV MAVEN_PROXY_HOST=${MAVEN_PROXY_HOST}
ENV MAVEN_PROXY_PORT=${MAVEN_PROXY_PORT}

COPY pom.xml .
COPY .mvn/settings.xml /opt/maven-settings/settings.xml
COPY .mvn/docker-proxy-settings.xml /opt/maven-settings/docker-proxy-settings.xml
RUN --mount=type=cache,target=/root/.m2 \
    if [ -n "$MAVEN_PROXY_HOST" ]; then \
        sed -i "s|__MAVEN_PROXY_HOST__|$MAVEN_PROXY_HOST|g; s|__MAVEN_PROXY_PORT__|$MAVEN_PROXY_PORT|g" /opt/maven-settings/docker-proxy-settings.xml \
        && SETTINGS=/opt/maven-settings/docker-proxy-settings.xml; \
    else SETTINGS=/opt/maven-settings/settings.xml; fi \
    && mvn -s "$SETTINGS" -B dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    if [ -n "$MAVEN_PROXY_HOST" ]; then SETTINGS=/opt/maven-settings/docker-proxy-settings.xml; else SETTINGS=/opt/maven-settings/settings.xml; fi \
    && mvn -s "$SETTINGS" -B clean package -DskipTests

FROM eclipse-temurin:21-jre 

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
