#!/bin/sh
# Autor principal: Pedro Henrique Contardi Soler - RA: 25005592

cd "$(dirname "$0")"

: "${MONGODB_URI:?Defina MONGODB_URI (MongoDB Atlas). Use um banco terminado em _teste. Veja docs/mongodb-atlas.md}"

rm -rf bin && mkdir bin
javac --release 21 -encoding UTF-8 -cp "lib/*" -d bin $(find src teste -name "*.java") && java -cp "bin:lib/*" TesteMongo