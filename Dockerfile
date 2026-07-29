# ===== Stage 1: build avec Gradle =====
FROM gradle:8.8-jdk21 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
# télécharge les dépendances une fois (cache)
RUN gradle dependencies --no-daemon || true
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# ===== Stage 2: runtime léger =====
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd -r -u 1001 -g root spring && mkdir -p /data && chown -R spring:root /data /app
COPY --from=build /app/build/libs/app.jar /app/app.jar
EXPOSE 8080
USER spring
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dserver.port=8080"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
