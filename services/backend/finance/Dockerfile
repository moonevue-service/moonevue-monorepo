# ============================================
# Finance — Dockerfile de Produção (multi-stage)
# ============================================
# Build context: raiz do projeto (.)
# Uso: docker build -f finance/Dockerfile -t moonevue/finance:latest .

# --- Etapa 1: Compilação ---
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# Copia POMs primeiro (cache de dependências)
COPY pom.xml .
COPY core/pom.xml core/pom.xml
COPY useful/pom.xml useful/pom.xml
COPY finance/pom.xml finance/pom.xml
# Dummy POMs
COPY auth/pom.xml auth/pom.xml
COPY gateway/pom.xml gateway/pom.xml

# Baixa dependências offline
RUN mvn dependency:go-offline -pl finance -am -q -B

# Copia código-fonte
COPY core/src core/src
COPY useful/src useful/src
COPY finance/src finance/src

# Build
RUN mvn package -pl finance -am -DskipTests -q -B \
    && mv finance/target/finance.jar finance/target/app.jar

# --- Etapa 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

RUN mkdir -p /data/certs && chown appuser:appgroup /data/certs

COPY --from=build /workspace/finance/target/app.jar app.jar

LABEL maintainer="moonevue" \
      service="finance" \
      version="1.0.0"

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl --no-verbose --spider http://localhost:8082/actuator/health || exit 1

USER appuser

ENTRYPOINT ["java"]
CMD ["-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

