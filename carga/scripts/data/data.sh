#!/bin/sh
# carga/data/ <-> bucket R2 (docs/planos/PLANO_CARGA_INICIAL.md, 2 e 5.2). Roda no conteiner "data" do compose:
#   docker compose --profile carga run --rm data pull [--minimo]   bucket -> carga/data/ (maquina nova; token so leitura)
#   docker compose --profile carga run --rm data push              carga/data/ -> bucket + manifests (produtor; chave de escrita)
#   docker compose --profile carga run --rm data status            carga/data/ x bucket, por dataset (tamanho)
#
# A aplicacao le so a carga/data/ local. pull e push copiam o que falta ou mudou e NUNCA apagam
# nada do outro lado. Datasets = as pastas de primeiro nivel (b3, cvm, fnet, levantamento, logos);
# manifests/ e so do bucket. rclone configurado por variavel (RCLONE_CONFIG_R2_*, do .env).
set -eu
# sem expansao de * nas opcoes (--exclude *.part)
set -f

ACAO="${1:-status}"
MODO="${2:-}"
BUCKET="r2:${S3_BUCKET:-primevest-data}"
DATA=/data
# o minimo para uma carga completa sem rebaixar o que a origem nao devolve mais ou devolve devagar:
# avisos do FNET, historico e 12 meses de proventos da B3, relatorios de proventos da CVM
MINIMO="fnet b3/proventos b3/proventos-12m cvm/ipe cvm/proventos levantamento"
# --checksum: compara pelo hash (ETag do R2 x md5 local), sem um HEAD por objeto para ler a data
OPCOES="--checksum --transfers 32 --checkers 64 --fast-list --exclude *.part --exclude *.tmp --stats 30s --stats-one-line"

if [ "${DATA_STORAGE:-local}" != "s3" ]; then
  echo "DATA_STORAGE=${DATA_STORAGE:-local}: sem bucket. Use DATA_STORAGE=s3 e as variaveis S3_* no .env."
  exit 0
fi
if [ -z "${RCLONE_CONFIG_R2_ACCESS_KEY_ID:-}" ] || [ -z "${RCLONE_CONFIG_R2_ENDPOINT:-}" ]; then
  echo "Faltam S3_ENDPOINT / S3_ACCESS_KEY / S3_SECRET_KEY no .env." >&2
  exit 2
fi

datasets_bucket() {
  rclone lsf "$BUCKET" --dirs-only --max-depth 1 | sed 's#/$##' | grep -v '^manifests$' || true
}

datasets_local() {
  find "$DATA" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; | sort
}

case "$ACAO" in
  pull)
    if [ "$MODO" = "--minimo" ]; then LISTA="$MINIMO"; else LISTA="$(datasets_bucket)"; fi
    for ds in $LISTA; do
      echo "== pull $ds"
      mkdir -p "$DATA/$ds"
      # shellcheck disable=SC2086
      rclone copy "$BUCKET/$ds" "$DATA/$ds" $OPCOES
    done
    ;;
  push)
    DIA="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    TMP="$(mktemp -d)"
    RESUMO="$TMP/manifest.json"
    printf '{\n  "version": "%s",\n  "datasets": {' "$DIA" > "$RESUMO"
    SEP=""
    for ds in $(datasets_local); do
      echo "== push $ds"
      # shellcheck disable=SC2086
      rclone copy "$DATA/$ds" "$BUCKET/$ds" $OPCOES
      # manifest do dataset: chave e tamanho de cada arquivo, como o bucket tem. Sem hash nem data:
      # no R2 os dois custam um HEAD por objeto (22 mil no fnet), e a listagem ja traz o tamanho
      rclone lsjson -R --files-only --no-mimetype --no-modtime --fast-list "$BUCKET/$ds" > "$TMP/$ds.json"
      rclone copyto "$TMP/$ds.json" "$BUCKET/manifests/$ds.json"
      TAM="$(rclone size --json --fast-list "$BUCKET/$ds")"
      printf '%s\n    "%s": %s' "$SEP" "$ds" "$TAM" >> "$RESUMO"
      SEP=","
    done
    printf '\n  }\n}\n' >> "$RESUMO"
    rclone copyto "$RESUMO" "$BUCKET/manifests/manifest.json"
    cat "$RESUMO"
    rm -rf "$TMP"
    ;;
  status)
    rclone cat "$BUCKET/manifests/manifest.json" 2>/dev/null || echo "(bucket sem manifest ainda: rode push)"
    for ds in $(datasets_bucket); do
      echo "== $ds (bucket x carga/data/, por tamanho)"
      rclone check "$BUCKET/$ds" "$DATA/$ds" --size-only --fast-list --one-way 2>&1 | tail -n 3 || true
    done
    ;;
  *)
    echo "uso: data.sh pull [--minimo] | push | status" >&2
    exit 2
    ;;
esac
