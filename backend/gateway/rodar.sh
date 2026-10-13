#!/bin/sh
cd "$(dirname "$0")"
if [ ! -f bin/Gateway.class ]
then
    echo "Gateway nao compilado. Rode ./compilar.sh primeiro." >&2
    exit 1
fi
java -cp bin Gateway
