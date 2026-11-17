#!/bin/sh
# Autor: Vinícius Panutti Salgado — R.A. 25007329
# Smoke das coleções reais. Não reaplica o schema — rode aplicar-schema.sh antes.
# Interrompe o script em erro de comando ou variável ausente.
set -eu

# Usa o diretório do script como base para localizar o smoke test.
cd "$(dirname "$0")"
# Exige uma URI configurada antes de executar o teste de schema.
: "${MONGODB_URI:?Defina MONGODB_URI antes de testar o schema}"
# Exige explicitamente o banco que será usado pelo smoke test.
: "${MONGODB_DATABASE:?Defina MONGODB_DATABASE antes de testar o schema}"

# Separa nomes de bancos de teste dos demais antes de executar as verificações.
case "$MONGODB_DATABASE" in
  # Permite diretamente nomes de bancos terminados em _teste.
  *_teste) : ;;
  # Encaminha os demais nomes à proteção por autorização explícita de ambiente.
  *)
    # Exige ALLOW_NON_TEST_SCHEMA=1 quando o destino não é um banco de teste.
    if [ "${ALLOW_NON_TEST_SCHEMA:-}" != "1" ]; then
      # Explica a proteção de banco acionada antes de interromper o teste.
      echo "Recusado: use um banco terminado em _teste (ou ALLOW_NON_TEST_SCHEMA=1)" >&2
      # Encerra com erro de configuração sem executar o smoke test.
      exit 2
    fi
    ;;
esac

# Confere a instalação do shell MongoDB antes de tentar executar a suíte.
command -v mongosh >/dev/null 2>&1 || {
  # Informa que mongosh precisa estar instalado para rodar este teste.
  echo "mongosh não encontrado; instale o shell do MongoDB para executar este script" >&2
  # Retorna o código de comando não encontrado quando falta o shell MongoDB.
  exit 127
}

# Executa somente o smoke test das coleções reais; o schema precisa ter sido aplicado previamente.
mongosh "$MONGODB_URI" --quiet --file schema-smoke.js
