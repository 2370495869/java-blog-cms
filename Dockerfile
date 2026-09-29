FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw mvnw
COPY pom.xml pom.xml
RUN chmod +x mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system blog && useradd --system --gid blog --home-dir /app blog
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build --chown=blog:blog /workspace/target/java-blog-cms-1.0.0.jar /app/app.jar
USER blog
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD test "$(curl --silent --show-error --max-time 4 --output /dev/null --write-out '%{http_code}' http://127.0.0.1:8080/actuator/health)" = 200
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
