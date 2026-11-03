#!/bin/sh
# uso: ./rodar.sh [porta]
cd "$(dirname "$0")"
java -cp bin Servidor "$@"
