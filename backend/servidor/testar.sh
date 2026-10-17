#!/bin/sh
cd "$(dirname "$0")"

SEP=":"
case "$(uname)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac

rm -rf bin && mkdir bin
javac -encoding UTF-8 -cp "lib/*" -d bin $(find src teste -name "*.java") && java -cp "bin${SEP}lib/*" TesteServidor
