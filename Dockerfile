# Stage 1: Build
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B -q

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app
COPY --from=builder /build/target/AIscanner_server-1.0-SNAPSHOT.jar app.jar

RUN mkdir -p /data/videos && chown -R appuser:appgroup /data/videos

EXPOSE 8080

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
