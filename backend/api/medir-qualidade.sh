#!/bin/sh
# Autor Principal: Pedro Henrique Contardi Soler - 25005592
cd "$(dirname "$0")"

# Classpath: ":" no Linux/macOS e ";" no Git Bash do Windows.
SEP=":"
case "$(uname)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac

# Sobe o serviço de algoritmos numa porta só desta medição e o encerra ao sair.
. ./iniciar-algoritmos.sh 30101
trap 'kill "$ALGORITMOS_PID" 2>/dev/null' EXIT INT TERM

# Compilação: recria bin/ com as fontes da API, os testes e o protocolo.
rm -rf bin 2>/dev/null
mkdir -p bin
PROTO=$(find ../serversocket/src/protocolo -name '*.java')
# Classpath de compilação: bibliotecas em lib/.
javac --release 21 -encoding UTF-8 -cp "lib/*" -d bin $(find src teste -name "*.java") $PROTO

# Testes: --mongo mede contra o banco; sem a flag, a suíte roda em memória.
# Classpath de execução: bin/ e lib/.
if [ "$1" = "--mongo" ]; then
  exec java -cp "bin${SEP}lib/*" TesteQualidade --mongo
else
  exec java -cp "bin${SEP}lib/*" TesteQualidade
fi
