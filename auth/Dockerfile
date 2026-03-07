# ============================================
# Auth — Dockerfile de Produção (multi-stage)
# ============================================
# Build context: raiz do projeto (.)
# Uso: docker build -f auth/Dockerfile -t moonevue/auth:latest .

# --- Etapa 1: Compilação ---
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# Copia POMs primeiro (camada cacheada se nenhum POM mudar)
COPY pom.xml .
COPY core/pom.xml core/pom.xml
COPY useful/pom.xml useful/pom.xml
COPY auth/pom.xml auth/pom.xml
# Dummy POMs para módulos que não participam do build mas estão no <modules>
COPY gateway/pom.xml gateway/pom.xml
COPY finance/pom.xml finance/pom.xml

# Baixa dependências (cache de camada do Docker)
RUN mvn dependency:go-offline -pl auth -am -q -B

# Copia código-fonte
COPY core/src core/src
COPY useful/src useful/src
COPY auth/src auth/src

# Build do módulo auth (+ dependências internas)
RUN mvn package -pl auth -am -DskipTests -q -B \
    && mv auth/target/auth-*.jar auth/target/app.jar

# --- Etapa 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl
# Segurança: roda como usuário não-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copia apenas o JAR final
COPY --from=build /workspace/auth/target/app.jar app.jar

# Metadados
LABEL maintainer="moonevue" \
      service="auth" \
      version="1.0.0"

# Porta do serviço
EXPOSE 8081

# Healthcheck dentro do container
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl --no-verbose --spider http://localhost:8081/actuator/health || exit 1

USER appuser

ENTRYPOINT ["java"]
CMD ["-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

