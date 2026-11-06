#!/bin/sh
# Sobe o ServerSocket de algoritmos (backend/serversocket) em segundo plano para a API HTTP usar.
# Uso: . ./iniciar-algoritmos.sh [porta]   (define ALGORITMOS_PID)
# Este arquivo é carregado com "." pelos scripts já posicionados em backend/api.
# $0 pertence ao chamador; usá-lo aqui repetia o caminho relativo e falhava.
PORTA="${1:-${SERVIDOR_ALGORITMOS_PORT:-3000}}"
DIR="../serversocket"

sh "$DIR/compilar.sh" || exit 1

java -cp "$DIR/bin" Servidor "$PORTA" &
ALGORITMOS_PID=$!
export SERVIDOR_ALGORITMOS_HOST="${SERVIDOR_ALGORITMOS_HOST:-127.0.0.1}"
export SERVIDOR_ALGORITMOS_PORT="$PORTA"
sleep 0.5
