FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 codeshield && mkdir -p /app/data && chown -R codeshield:codeshield /app
COPY --from=build /workspace/target/codeshield-1.0.0.jar app.jar
USER codeshield
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
