#!/bin/sh
cd "$(dirname "$0")"
rm -rf bin && mkdir bin
javac -encoding UTF-8 -d bin $(find src teste -name "*.java") && java -cp bin TesteGateway
