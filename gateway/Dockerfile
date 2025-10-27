# Produção: multi-stage build para módulo gateway
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache de dependências: copie POMs primeiro
COPY pom.xml .
COPY core/pom.xml core/pom.xml
COPY useful/pom.xml useful/pom.xml
COPY gateway/pom.xml gateway/pom.xml
RUN mvn -q -e -DskipTests dependency:go-offline

# Copia fontes e empacota somente o módulo gateway
COPY . .
RUN mvn -q -DskipTests -pl gateway -am package

# Runtime leve
FROM eclipse-temurin:21-jre
RUN useradd -ms /bin/bash appuser
WORKDIR /app
COPY --from=build /workspace/gateway/target/*-SNAPSHOT.jar /app/app.jar
ENV TZ=America/Sao_Paulo JAVA_OPTS=""
EXPOSE 8080
USER appuser
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]