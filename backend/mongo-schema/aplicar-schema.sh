#!/bin/sh
# Autor: Vinícius Panutti Salgado — R.A. 25007329
# Interrompe a execução em erro de comando ou uso de variável não definida.
set -eu

# Localiza o arquivo de schema a partir do diretório do próprio script.
cd "$(dirname "$0")"
# Exige a URI antes de iniciar o shell do MongoDB.
: "${MONGODB_URI:?Defina MONGODB_URI antes de aplicar o schema}"
# Exige o nome do banco antes de aplicar a validação estrutural.
: "${MONGODB_DATABASE:?Defina MONGODB_DATABASE antes de aplicar o schema}"

# Distingue bancos de teste dos demais antes de executar a alteração de schema.
case "$MONGODB_DATABASE" in
  # Permite diretamente bancos cujo nome termina em _teste.
  *_teste) : ;;
  # Submete qualquer outro nome à autorização explícita de ambiente abaixo.
  *)
    # Exige ALLOW_NON_TEST_SCHEMA=1 para permitir aplicação fora do banco de testes.
    if [ "${ALLOW_NON_TEST_SCHEMA:-}" != "1" ]; then
      # Informa a proteção acionada e como ela pode ser liberada explicitamente.
      echo "Recusado: use um banco terminado em _teste (ou ALLOW_NON_TEST_SCHEMA=1)" >&2
      # Encerra antes de executar o MongoDB quando o banco não está autorizado.
      exit 2
    fi
    ;;
esac

# Confere se mongosh está disponível e trata sua ausência com diagnóstico legível.
command -v mongosh >/dev/null 2>&1 || {
  # Explica a dependência necessária para aplicar o schema.
  echo "mongosh não encontrado; instale o shell do MongoDB para executar este script" >&2
  # Retorna o código convencional de comando não encontrado.
  exit 127
}

# Executa o arquivo de validação no mongosh usando a URI recebida; o JavaScript seleciona o banco configurado.
mongosh "$MONGODB_URI" --quiet --file schema-validation.js
