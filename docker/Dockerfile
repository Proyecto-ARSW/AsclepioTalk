# ── Etapa 1: compilar con Maven ──────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
# Descarga dependencias en capa separada para aprovechar la caché de Docker
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Etapa 2: imagen de runtime mínima ────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S talk && adduser -S talk -G talk
USER talk

COPY --from=builder /build/target/asclepio-talk-*.jar app.jar

EXPOSE 3003

ENTRYPOINT ["java", "-jar", "app.jar"]

# Daniel Useche
