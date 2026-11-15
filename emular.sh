#!/bin/sh
# Sobe AgroGestão com MongoDB Atlas: carrega .env, compila se preciso, API + socket.
# Uso: bash emular.sh [--build] [porta]
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$ROOT/.env"
API_DIR="$ROOT/backend/api"
BIN="$API_DIR/bin/Servidor.class"

for arg in "$@"; do
  if [ "$arg" = "--memoria" ]; then
    echo "Este projeto usa MongoDB Atlas. Rode bash emular.sh sem --memoria." >&2
    echo "Configure .env (veja docs/mongodb-atlas.md)." >&2
    exit 1
  fi
done

if [ ! -f "$ENV_FILE" ]; then
  echo "Arquivo .env não encontrado na raiz." >&2
  echo "  cp .env.example .env" >&2
  echo "Preencha MONGODB_URI e siga docs/mongodb-atlas.md" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

if [ -z "${MONGODB_URI:-}" ]; then
  echo "MONGODB_URI vazio em .env — persistência Atlas é obrigatória para emular." >&2
  exit 1
fi

BUILD=false
PORT=""
for arg in "$@"; do
  case "$arg" in
    --build) BUILD=true ;;
    [0-9]*) PORT="$arg" ;;
  esac
done

if [ "$BUILD" = true ] || [ ! -f "$BIN" ]; then
  echo "Compilando backend/api…"
  sh "$API_DIR/compilar.sh"
fi

HOST="${SERVIDOR_PORT:-8080}"
if [ -n "$PORT" ]; then
  HOST="$PORT"
fi

echo ""
echo "AgroGestão (MongoDB Atlas)"
echo "  App:    http://127.0.0.1:${HOST}/login.html"
echo "  Pronto: http://127.0.0.1:${HOST}/ready"
echo "  Parar:  desativar + Enter no terminal do servidor"
echo ""

if [ -n "$PORT" ]; then
  exec sh "$API_DIR/rodar.sh" "$PORT"
fi
exec sh "$API_DIR/rodar.sh"