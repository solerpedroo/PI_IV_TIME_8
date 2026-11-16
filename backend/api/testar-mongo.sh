#!/bin/sh
# Autor principal: Pedro Henrique Contardi Soler - RA: 25005592

cd "$(dirname "$0")"

# Mongo _teste: exige MONGODB_URI. A mensagem pede um banco cujo nome termina em _teste.
: "${MONGODB_URI:?Defina MONGODB_URI (MongoDB Atlas). Use um banco terminado em _teste. Veja docs/mongodb-atlas.md}"

# Compilação: apaga bin/ e gera as classes da API e dos testes.
rm -rf bin && mkdir bin
# Classpath de compilação: lib/. Em seguida roda a suíte.
# Testes: TesteMongo fala com o banco _teste, nunca com o banco real da aplicação.
# Classpath de execução: bin:lib/* (separador ":" deste script).
javac --release 21 -encoding UTF-8 -cp "lib/*" -d bin $(find src teste -name "*.java") && java -cp "bin:lib/*" TesteMongo
