#!/bin/sh
# Autor principal: Pedro Henrique Contardi Soler - RA: 25005592

cd "$(dirname "$0")"

if [ -z "$MONGODB_URI" ]; then
    export MONGODB_URI="mongodb://127.0.0.1:27017/agrogestao_teste?replicaSet=rs0&serverSelectionTimeoutMS=2000"
fi

rm -rf bin && mkdir bin
javac --release 21 -encoding UTF-8 -cp "lib/*" -d bin $(find src teste -name "*.java") && java -cp "bin:lib/*" TesteMongo
