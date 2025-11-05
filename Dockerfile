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
COPY --from=build /workspace/app/gateway/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]