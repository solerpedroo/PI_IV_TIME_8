#!/bin/sh
# Miguel — RA: 25003110
# Autor principal: Pedro Henrique Contardi Soler - RA: 25005592
# Autor secundário: Pedro Henrique Contardi Soler - 25005592
cd "$(dirname "$0")"

# Classpath: ":" no Linux/macOS e ";" no Git Bash do Windows.
SEP=":"
case "$(uname)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac

# Sobe o serviço de algoritmos numa porta só de teste e o encerra ao sair.
. ./iniciar-algoritmos.sh 30100
trap 'kill "$ALGORITMOS_PID" 2>/dev/null' EXIT INT TERM

# Compilação: limpa bin/, reúne API, testes e protocolo do socket.
rm -rf bin && mkdir bin
PROTO=$(find ../serversocket/src/protocolo -name '*.java')
# Classpath de compilação: lib/. A cadeia com && para no primeiro erro.
javac --release 21 -encoding UTF-8 -cp "lib/*" -d bin $(find src teste -name "*.java") $PROTO &&
# Testes: cada linha é uma suíte. O classpath junta bin/ e lib/.
java -cp "bin${SEP}lib/*" TesteServidor &&
java -cp "bin${SEP}lib/*" TesteQualidade &&
java -cp "bin${SEP}lib/*" TesteOrcamentos &&
java -cp "bin${SEP}lib/*" TestePerfil &&
java -cp "bin${SEP}lib/*" TesteGeorreferenciamento &&
java -cp "bin${SEP}lib/*" TesteOcorrencias &&
java -cp "bin${SEP}lib/*" TesteColheitas &&
java -cp "bin${SEP}lib/*" TesteManutencao &&
java -cp "bin${SEP}lib/*" TesteRelatorioSafra
