# syntax=docker/dockerfile:1
#
# Backend multi-módulo (docs/planos/PLANO_ADM_API.md): contexto de build = raiz do repositório,
# um serviço por módulo escolhido em MODULE (primevest-app-api ou primevest-adm-api).

# ---------------------------------------------------------------------------
# Estágio de dependências: baixa o que os poms pedem uma única vez.
# Fica em cache enquanto nenhum pom mudar. O go-offline pula os módulos do
# próprio reator (excludeReactor), que não existem em repositório nenhum.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /app
COPY pom.xml .
COPY primevest-core/pom.xml primevest-core/
COPY primevest-adm-api/pom.xml primevest-adm-api/
COPY primevest-app-api/pom.xml primevest-app-api/
RUN mvn -B dependency:go-offline

# ---------------------------------------------------------------------------
# dev: roda via spring-boot:run com DevTools.
# O código-fonte entra por bind mount (ver docker-compose.yml), então a imagem
# não precisa conter o src. O dev-watch.sh recompila o módulo e o core quando
# algo muda e avisa o DevTools, que reinicia a aplicação — ver o script.
# ---------------------------------------------------------------------------
FROM deps AS dev
ARG MODULE=primevest-app-api
ENV MODULE=${MODULE}
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
#   docker build --target runtime --build-arg MODULE=primevest-app-api -t primevest-app-api .
# ---------------------------------------------------------------------------
FROM deps AS build
ARG MODULE=primevest-app-api
WORKDIR /app
COPY primevest-core/src primevest-core/src
COPY primevest-adm-api/src primevest-adm-api/src
COPY primevest-app-api/src primevest-app-api/src
RUN mvn -B -pl ${MODULE} -am clean package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
ARG MODULE=primevest-app-api
WORKDIR /app
RUN useradd -r -u 1001 primevest
COPY --from=build /app/${MODULE}/target/${MODULE}-*-exec.jar app.jar
USER primevest
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
