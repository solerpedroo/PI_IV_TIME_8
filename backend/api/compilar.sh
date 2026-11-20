#!/bin/sh
# Autor principal: Pedro Henrique Contardi Soler - RA: 25005592
# Entra na pasta da API para que src/, lib/ e bin/ sejam achados pelos caminhos relativos.
cd "$(dirname "$0")"
# Compilação: apaga a saída anterior e recria a pasta bin/.
rm -rf bin && mkdir bin
# Compilação: localiza as classes Java do protocolo compartilhado com o socket.
PROTO=$(find ../serversocket/src/protocolo -name '*.java')
# Compilação: gera o bytecode Java 21 (fontes da API + protocolo) em bin/.
# Classpath de compilação: bibliotecas em lib/.
javac --release 21 -encoding UTF-8 -cp "lib/*" -d bin $(find src -name "*.java") $PROTO
