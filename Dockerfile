# --- Build stage ---
FROM gradle:jdk21 AS build
WORKDIR /app

ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx400m -Dorg.gradle.daemon=false"

COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew clean build -x test

# --- Run stage ---
FROM eclipse-temurin:21-jre AS run
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
