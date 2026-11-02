#!/bin/sh
# Hot reload do backend no container de desenvolvimento.
#
# O DevTools reinicia o Spring quando mudam as classes em target/classes, mas
# nada recompilava o src montado por bind mount: o spring-boot:run compila uma
# vez, na subida, e depois só observa target/. Alterar um .java não tinha
# efeito até o container ser reiniciado.
#
# Este script faz a ponte: a cada POLL_SECONDS procura arquivos em src/ mais
# novos que a última compilação e, se houver, roda mvn compile. O compile
# atualiza target/classes (e copia resources, então migration nova também
# entra) e, só se der certo, toca o arquivo-gatilho do DevTools, que reinicia a
# aplicação — Flyway incluído.
#
# Por que gatilho: sem ele o DevTools reinicia ao ver a primeira classe mudar,
# no meio da compilação, e pode subir com metade das classes novas. Com o
# trigger-file ele ignora target/classes e só reinicia quando o compile termina
# bem; compilação com erro mantém a versão anterior no ar.
#
# Polling, e não inotify, porque bind mount do Windows não propaga eventos de
# arquivo para o container; a data de modificação chega, o evento não. É o
# mesmo motivo do CHOKIDAR_USEPOLLING no web.
#
# Limitação: arquivo APAGADO não altera data de nada, então sozinho não dispara
# compilação, e o que já estava em target/classes continua lá. Reinicie o
# container: a subida limpa target/classes (ver abaixo).
#
# Multi-módulo (docs/planos/PLANO_ADM_API.md): o contêiner roda um módulo ($MODULE, ex.:
# activest-app-api) e observa também o activest-core. O Maven roda da raiz com
# -pl $MODULE -am: o reator compila o core junto e resolve a dependência para
# activest-core/target/classes — uma pasta, não um jar, então o DevTools põe o
# core no classloader de reinício e uma classe do core alterada também recarrega.

POLL_SECONDS="${POLL_SECONDS:-2}"
# DEV_WATCH (docs/planos/PLANO_PERFORMANCE_TELAS.md, D2): a varredura de src/ pelo bind mount do Windows custa
# ~2-3 ms por arquivo e, a cada 2 s, deixava o disco montado sempre ocupado (lentidao em todas as telas). Padrao
# false: sem varredura; depois de alterar Java, rode `dev-watch.sh reload` no container (I2). true: volta o
# automatico.
DEV_WATCH="${DEV_WATCH:-false}"
MODULE="${MODULE:-activest-app-api}"
WATCH="activest-core/src $MODULE/src"
TARGETS="activest-core/target $MODULE/target"
# A app-api usa o activest-integration (docs/planos/PLANO_INTEGRACAO_B3.md, F1): mesma regra do core.
if [ "$MODULE" = "activest-app-api" ]; then
  WATCH="$WATCH activest-integration/src"
  TARGETS="$TARGETS activest-integration/target"
fi
MARKER=/tmp/.last-compile
TRIGGER="$MODULE/target/classes/.reloadtrigger"

# Compila o modulo (e o core) e, so se der certo, toca o gatilho do DevTools, que reinicia a aplicacao.
# Compilacao com erro mantem a versao anterior no ar e devolve codigo diferente de zero.
recompile() {
  echo "[dev-watch] recompilando $MODULE..."
  if mvn -B -q -pl "$MODULE" -am compile; then
    touch "$TRIGGER"
    echo "[dev-watch] compilado; o DevTools reinicia a aplicação."
    return 0
  fi
  echo "[dev-watch] compilação FALHOU — a aplicação segue com a versão anterior."
  return 1
}

# Modo reload (PLANO_PERFORMANCE_TELAS.md, I2): `docker exec activest-app-api dev-watch.sh reload`.
# Arquivo apagado ou renomeado nao sai de target/classes por aqui: nesse caso, reinicie o container.
if [ "${1:-}" = "reload" ]; then
  cd /app || exit 1
  touch "$MARKER"
  recompile
  exit $?
fi

# Recompila do zero a cada subida. O target/ vive dentro do container (não é
# montado) e o Maven só copia/atualiza resources, nunca apaga: uma migration
# removida ou renomeada no src/ continuava em target/classes/db/migration e o
# Flyway acusava versão duplicada ("Found more than one migration with version
# 1"), derrubando a API. Custa alguns segundos de compilação na subida.
rm -rf $TARGETS

touch "$MARKER"

if [ "$DEV_WATCH" = "true" ]; then
  echo "[dev-watch] DEV_WATCH=true: varrendo $WATCH a cada ${POLL_SECONDS}s."
  (
    while true; do
      sleep "$POLL_SECONDS"
      if [ -n "$(find $WATCH -type f -newer "$MARKER" -print -quit 2>/dev/null)" ]; then
        # Marca ANTES de compilar: o que for salvo durante a compilação entra na
        # próxima volta em vez de se perder.
        touch "$MARKER"
        recompile || true
      fi
    done
  ) &
else
  echo "[dev-watch] DEV_WATCH=false: sem varredura de src/. Depois de alterar Java: dev-watch.sh reload."
fi

# exec: o Maven vira o processo principal e recebe o SIGTERM do docker stop.
exec mvn -B -pl "$MODULE" -am "-Drun.module=$MODULE" spring-boot:run \
  "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.poll-interval=2s -Dspring.devtools.restart.quiet-period=1s -Dspring.devtools.restart.trigger-file=.reloadtrigger"
