#!/bin/sh
# Autor Principal: Pedro Henrique Contardi Soler - 25005592
set -eu
cd "$(dirname "$0")"
: "${MONGODB_URI:?Informe um banco de teste terminado em _teste}"
mkdir -p bin
javac --release 21 -encoding UTF-8 -cp "lib/*" -d bin $(find src teste -name '*.java')
java -cp 'bin:lib/*' TesteEstoqueMongo
