# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copia o JAR gerado pelo build do módulo
COPY target/*.jar app.jar

# Flags opcionais da JVM
ENV JAVA_OPTS=""

# Profile de execução alinhado ao docker-compose
ENV SPRING_PROFILES_ACTIVE="docker"

# Porta interna do gateway
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]