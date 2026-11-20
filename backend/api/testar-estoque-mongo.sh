#!/bin/sh
# Autor Principal: Pedro Henrique Contardi Soler - 25005592
# Para no primeiro comando com erro e trata variável vazia como falha.
set -eu
cd "$(dirname "$0")"
# Mongo _teste: sem MONGODB_URI o script para. A mensagem pede um banco cujo nome termina em _teste.
: "${MONGODB_URI:?Informe um banco de teste terminado em _teste}"
# Compilação: garante a pasta bin/ e inclui o protocolo do socket.
mkdir -p bin
PROTO=$(find ../serversocket/src/protocolo -name '*.java')
# Classpath de compilação: lib/.
javac --release 21 -encoding UTF-8 -cp "lib/*" -d bin $(find src teste -name '*.java') $PROTO
# Testes: estoque contra o Mongo _teste. Classpath de execução: bin:lib/*.
java -cp 'bin:lib/*' TesteEstoqueMongo
