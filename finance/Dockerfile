# Produção: multi-stage build para módulo finance
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY core/pom.xml core/pom.xml
COPY useful/pom.xml useful/pom.xml
COPY finance/pom.xml finance/pom.xml
RUN mvn -q -e -DskipTests dependency:go-offline

COPY . .
RUN mvn -q -DskipTests -pl finance -am package

FROM eclipse-temurin:21-jre
RUN useradd -ms /bin/bash appuser
WORKDIR /app
COPY --from=build /workspace/finance/target/*-SNAPSHOT.jar /app/app.jar
ENV TZ=America/Sao_Paulo JAVA_OPTS=""
EXPOSE 8082
USER appuser
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]