FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ENV TZ=Asia/Shanghai

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        fonts-noto-cjk \
        libreoffice-writer \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/target/*.jar /app/app.jar

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=12 \
  CMD curl --fail --silent http://127.0.0.1:8080/actuator/health >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
