#!/bin/sh
# Testa SMTP do .env (na raiz do repo) sem subir o servidor HTTP.
set -eu
cd "$(dirname "$0")"
ENV="${1:-../../.env}"
if [ -f "$ENV" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV"
  set +a
else
  echo "Arquivo nao encontrado: $ENV" >&2
  exit 1
fi
bash compilar.sh
DEST="${2:-$SMTP_USERNAME}"
if [ -z "$DEST" ]; then
  echo "Informe o destino: sh testar-smtp.sh [caminho/.env] destino@email.com" >&2
  exit 1
fi
java -cp "bin" servicos.TesteEnvioSmtp "$DEST"
