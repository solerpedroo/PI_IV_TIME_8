#!/bin/sh
# Autor principal: Pedro Henrique Contardi Soler - RA: 25005592
cd "$(dirname "$0")"
rm -rf bin && mkdir bin
javac --release 21 -encoding UTF-8 -cp "lib/*" -d bin $(find src -name "*.java")
