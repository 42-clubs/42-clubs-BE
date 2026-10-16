# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 clubs \
    && useradd --uid 10001 --gid clubs --no-create-home clubs \
    && mkdir -p /app/data/photos \
    && chown -R clubs:clubs /app
WORKDIR /app
COPY --from=build --chown=clubs:clubs /workspace/build/libs/*-SNAPSHOT.jar /app/app.jar
USER clubs:clubs
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
