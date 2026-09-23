# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# deps: instala node_modules dentro da imagem Linux.
# Importante no Windows — binários nativos compilados no host não funcionam aqui.
#
# Debian e não Alpine: as camadas de node:24-alpine ficaram corrompidas no store
# local do Docker Desktop (arquivos com tamanho certo e conteúdo todo zero —
# `exec format error` no entrypoint, segfault no binário do node). O pull não
# as rebaixa porque o digest confere; só uma purga do store resolveria. Alpine
# em si está bom: nginx:alpine abaixo e postgres/redis do compose rodam normal.
# Voltar para -alpine é seguro depois de purgar o store, e economiza ~700 MB.
# ---------------------------------------------------------------------------
FROM node:24 AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci

# ---------------------------------------------------------------------------
# dev: servidor Vite com hot reload.
# O código entra por bind mount; node_modules vem de volume anônimo.
# ---------------------------------------------------------------------------
FROM deps AS dev
WORKDIR /app
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]

# ---------------------------------------------------------------------------
# build + runtime: build estático servido por nginx.
#   docker build --target runtime -t primevest-web ./primevest-web
# ---------------------------------------------------------------------------
FROM deps AS build
WORKDIR /app
COPY . .
RUN npm run build

FROM nginx:alpine AS runtime
COPY --from=build /app/dist /usr/share/nginx/html
# SPA: qualquer rota desconhecida cai no index.html, senão F5 em /dashboard dá 404.
RUN printf 'server {\n\
  listen 80;\n\
  root /usr/share/nginx/html;\n\
  index index.html;\n\
  location /api/ { proxy_pass http://api:8080; proxy_set_header Host $host; }\n\
  location / { try_files $uri $uri/ /index.html; }\n\
}\n' > /etc/nginx/conf.d/default.conf
EXPOSE 80
