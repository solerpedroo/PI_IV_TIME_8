#!/bin/sh
set -eu

cd "$(dirname "$0")/.."
: "${MONGODB_URI:?Defina MONGODB_URI antes de aplicar o schema}"
: "${MONGODB_DATABASE:?Defina MONGODB_DATABASE antes de aplicar o schema}"

case "$MONGODB_DATABASE" in
  *_teste) : ;;
  *) echo "Recusado: use um banco terminado em _teste para esta operação" >&2; exit 2 ;;
esac

command -v mongosh >/dev/null 2>&1 || {
  echo "mongosh não encontrado; instale o shell do MongoDB para executar este script" >&2
  exit 127
}

mongosh "$MONGODB_URI" --quiet --file mongo/schema-validation.js
