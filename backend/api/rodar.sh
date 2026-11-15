#!/bin/sh
# Autor principal: Pedro Henrique Contardi Soler - RA: 25005592
# uso: ./rodar.sh [porta] [--memoria]
cd "$(dirname "$0")"

# no Windows (Git Bash) o separador do classpath e ";"
SEP=":"
case "$(uname)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac

usa_memoria=false
for arg in "$@"; do
    case "$arg" in --memoria) usa_memoria=true ;; esac
done

if [ -z "$MONGODB_URI" ] && [ "$usa_memoria" = false ]; then
    echo "Defina MONGODB_URI (MongoDB Atlas). Copie .env.example para .env na raiz e carregue-o." >&2
    echo "Guia: docs/mongodb-atlas.md" >&2
    exit 1
fi

if [ -z "$SERVIDOR_ALGORITMOS_DISABLED" ]; then
    . ./iniciar-algoritmos.sh 3000
    trap 'kill "$ALGORITMOS_PID" 2>/dev/null' EXIT INT TERM
fi

java -cp "bin${SEP}lib/*" Servidor "$@"