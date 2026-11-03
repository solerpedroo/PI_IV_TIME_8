#!/bin/sh
# Sobe o ServerSocket de algoritmos (backend/serversocket) em segundo plano para a API HTTP usar.
# Uso: . ./iniciar-algoritmos.sh [porta]   (define ALGORITMOS_PID)
cd "$(dirname "$0")"
PORTA="${1:-${SERVIDOR_ALGORITMOS_PORT:-3000}}"
DIR="../serversocket"

sh "$DIR/compilar.sh" || exit 1

java -cp "$DIR/bin" Servidor "$PORTA" &
ALGORITMOS_PID=$!
export SERVIDOR_ALGORITMOS_HOST="${SERVIDOR_ALGORITMOS_HOST:-127.0.0.1}"
export SERVIDOR_ALGORITMOS_PORT="$PORTA"
sleep 0.5
