#!/bin/sh
# Miguel — RA: 25003110
# Autor principal: Pedro Henrique Contardi Soler - RA: 25005592
# Autor secundário: Pedro Henrique Contardi Soler - 25005592
cd "$(dirname "$0")"

SEP=":"
case "$(uname)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac

. ./iniciar-algoritmos.sh 30100
trap 'kill "$ALGORITMOS_PID" 2>/dev/null' EXIT INT TERM

rm -rf bin && mkdir bin
PROTO=$(find ../serversocket/src/protocolo -name '*.java')
javac --release 21 -encoding UTF-8 -cp "lib/*" -d bin $(find src teste -name "*.java") $PROTO &&
java -cp "bin${SEP}lib/*" TesteServidor &&
java -cp "bin${SEP}lib/*" TesteOrcamentos &&
java -cp "bin${SEP}lib/*" TestePerfil &&
java -cp "bin${SEP}lib/*" TesteOcorrencias &&
java -cp "bin${SEP}lib/*" TesteColheitas &&
java -cp "bin${SEP}lib/*" TesteManutencao &&
java -cp "bin${SEP}lib/*" TesteRelatorioSafra
