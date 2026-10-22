#!/bin/sh
# Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592
# uso: ./rodar.sh [porta] [--memoria]
cd "$(dirname "$0")"

# no Windows (Git Bash) o separador do classpath e ";"
SEP=":"
case "$(uname)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac

if [ -z "$MONGODB_URI" ]; then
    export MONGODB_URI="mongodb://127.0.0.1:27017/agrogestao?replicaSet=rs0&serverSelectionTimeoutMS=2000"
fi

java -cp "bin${SEP}lib/*" Servidor "$@"
