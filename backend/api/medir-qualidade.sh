#!/bin/sh
# Autor Principal: Pedro Henrique Contardi Soler - 25005592
cd "$(dirname "$0")"

SEP=":"
case "$(uname)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac

. ./iniciar-algoritmos.sh 30101
trap 'kill "$ALGORITMOS_PID" 2>/dev/null' EXIT INT TERM

rm -rf bin 2>/dev/null
mkdir -p bin
PROTO=$(find ../serversocket/src/protocolo -name '*.java')
javac --release 21 -encoding UTF-8 -cp "lib/*" -d bin $(find src teste -name "*.java") $PROTO

if [ "$1" = "--mongo" ]; then
  exec java -cp "bin${SEP}lib/*" TesteQualidade --mongo
else
  exec java -cp "bin${SEP}lib/*" TesteQualidade
fi