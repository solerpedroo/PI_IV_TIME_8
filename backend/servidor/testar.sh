#!/bin/sh
# Autor principal: Pedro Henrique Contardi Soler - RA: 25005592
cd "$(dirname "$0")"

SEP=":"
case "$(uname)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac

rm -rf bin && mkdir bin
javac --release 21 -encoding UTF-8 -cp "lib/*" -d bin $(find src teste -name "*.java") && java -cp "bin${SEP}lib/*" TesteServidor && java -cp "bin${SEP}lib/*" TesteOcorrencias
