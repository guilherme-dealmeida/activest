# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Estágio de dependências: baixa o que o pom.xml pede uma única vez.
# Fica em cache enquanto o pom.xml não mudar.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline

# ---------------------------------------------------------------------------
# dev: roda via spring-boot:run com DevTools.
# O código-fonte entra por bind mount (ver docker-compose.yml), então a imagem
# não precisa conter o src — alterar um .java reinicia a aplicação sozinho.
# ---------------------------------------------------------------------------
FROM deps AS dev
WORKDIR /app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=docker
CMD ["mvn", "-B", "spring-boot:run", "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.poll-interval=2s -Dspring.devtools.restart.quiet-period=1s"]

# ---------------------------------------------------------------------------
# build + runtime: imagem enxuta para produção.
#   docker build --target runtime -t primevest-api ./primevest-api
# ---------------------------------------------------------------------------
FROM deps AS build
WORKDIR /app
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd -r -u 1001 primevest
COPY --from=build /app/target/*.jar app.jar
USER primevest
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
