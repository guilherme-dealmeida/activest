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
# não precisa conter o src. O dev-watch.sh recompila src/ quando algo muda e
# avisa o DevTools, que reinicia a aplicação — ver o comentário no script.
# ---------------------------------------------------------------------------
FROM deps AS dev
WORKDIR /app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=docker
COPY dev-watch.sh /usr/local/bin/dev-watch.sh
# O sed tira o CR: num checkout do Windows o script pode vir com CRLF, e aí o
# shebang vira "/bin/sh\r" e o container nem sobe.
RUN sed -i 's/\r$//' /usr/local/bin/dev-watch.sh && chmod +x /usr/local/bin/dev-watch.sh
CMD ["/usr/local/bin/dev-watch.sh"]

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
