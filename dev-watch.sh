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

# Recompila do zero a cada subida. O target/ vive dentro do container (não é
# montado) e o Maven só copia/atualiza resources, nunca apaga: uma migration
# removida ou renomeada no src/ continuava em target/classes/db/migration e o
# Flyway acusava versão duplicada ("Found more than one migration with version
# 1"), derrubando a API. Custa alguns segundos de compilação na subida.
rm -rf $TARGETS

touch "$MARKER"

(
  while true; do
    sleep "$POLL_SECONDS"
    if [ -n "$(find $WATCH -type f -newer "$MARKER" -print -quit 2>/dev/null)" ]; then
      # Marca ANTES de compilar: o que for salvo durante a compilação entra na
      # próxima volta em vez de se perder.
      touch "$MARKER"
      echo "[dev-watch] alteração em $WATCH — recompilando..."
      if mvn -B -q -pl "$MODULE" -am compile; then
        touch "$TRIGGER"
        echo "[dev-watch] compilado; o DevTools reinicia a aplicação."
      else
        echo "[dev-watch] compilação FALHOU — a aplicação segue com a versão anterior."
      fi
    fi
  done
) &

# exec: o Maven vira o processo principal e recebe o SIGTERM do docker stop.
exec mvn -B -pl "$MODULE" -am "-Drun.module=$MODULE" spring-boot:run \
  "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.poll-interval=2s -Dspring.devtools.restart.quiet-period=1s -Dspring.devtools.restart.trigger-file=.reloadtrigger"
