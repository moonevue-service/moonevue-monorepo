# Produção: multi-stage build para o módulo auth
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY core/pom.xml core/pom.xml
COPY useful/pom.xml useful/pom.xml
COPY auth/pom.xml auth/pom.xml
RUN mvn -q -e -DskipTests dependency:go-offline

COPY . .
RUN mvn -q -DskipTests -pl auth -am package

FROM eclipse-temurin:21-jre
RUN useradd -ms /bin/bash appuser
WORKDIR /app
COPY --from=build /workspace/auth/target/*-SNAPSHOT.jar /app/app.jar
ENV TZ=America_Sao_Paulo JAVA_OPTS=""
EXPOSE 8081
USER appuser
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]