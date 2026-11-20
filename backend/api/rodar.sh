#!/bin/sh
# Autor principal: Pedro Henrique Contardi Soler - RA: 25005592
# uso: ./rodar.sh [porta] [--memoria]
cd "$(dirname "$0")"

# Classpath: no Linux e no macOS o separador é ":".
# no Windows (Git Bash) o separador do classpath e ";"
SEP=":"
case "$(uname)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac

# --memoria sobe a API sem MongoDB, só com dados em memória.
usa_memoria=false
for arg in "$@"; do
    case "$arg" in --memoria) usa_memoria=true ;; esac
done

# Se MONGODB_URI não veio do ambiente, lê o .env da raiz do repositório.
ENV_RAIZ="$(cd "$(dirname "$0")/../.." && pwd)/.env"
if [ -z "${MONGODB_URI:-}" ] && [ -f "$ENV_RAIZ" ]; then
    set -a
    # shellcheck disable=SC1090
    . "$ENV_RAIZ"
    set +a
fi

# Sem URI e sem --memoria o processo para: a API não adivinha o banco.
if [ -z "$MONGODB_URI" ] && [ "$usa_memoria" = false ]; then
    echo "Defina MONGODB_URI (MongoDB Atlas). Copie .env.example para .env na raiz." >&2
    echo "Na raiz do repositório: bash emular.sh" >&2
    echo "Guia: docs/mongodb-atlas.md" >&2
    exit 1
fi

# Sobe o serviço de algoritmos e encerra esse processo junto com a API.
if [ -z "$SERVIDOR_ALGORITMOS_DISABLED" ]; then
    . ./iniciar-algoritmos.sh 3000
    trap 'kill "$ALGORITMOS_PID" 2>/dev/null' EXIT INT TERM
fi

# Classpath de execução: classes em bin/ e dependências em lib/.
java -cp "bin${SEP}lib/*" Servidor "$@"
