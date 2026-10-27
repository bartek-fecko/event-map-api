# syntax=docker/dockerfile:1.7

# ---- build -----------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# ---- explode Spring Boot layers --------------------------------------------
FROM eclipse-temurin:25-jre AS layers
WORKDIR /layers
COPY --from=build /build/target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination /layers/extracted

# ---- runtime ---------------------------------------------------------------
FROM eclipse-temurin:25-jre

RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app

# Dependencies change rarely, application code on every deploy: separate COPY
# layers keep the large ones cached.
COPY --from=layers --chown=app:app /layers/extracted/dependencies/ ./
COPY --from=layers --chown=app:app /layers/extracted/spring-boot-loader/ ./
COPY --from=layers --chown=app:app /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=app:app /layers/extracted/application/ ./

RUN mkdir -p /app/data/media && chown -R app:app /app

USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
