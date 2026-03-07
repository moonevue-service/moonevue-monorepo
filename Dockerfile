FROM eclipse-temurin:21-jdk-alpine as build
WORKDIR /workspace/app

# Copy Maven wrapper files
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Copy all modules
COPY useful useful
COPY core core
COPY gateway gateway
COPY finance finance
COPY auth auth

RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
# expose port for clarity (optional)
EXPOSE 8080
COPY --from=build /workspace/app/gateway/target/*.jar app.jar
# use shell form to expand PORT env var
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app.jar"]