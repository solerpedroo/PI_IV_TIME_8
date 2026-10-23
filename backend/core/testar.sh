#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
agro_maven="${MAVEN_BIN:-mvn}"
if [[ $# -gt 1 || ( $# -eq 1 && "${1:-}" != "--atlas" ) ]]; then
  echo "Uso: $0 [--atlas]" >&2
  exit 2
fi
"$agro_maven" -q test-compile
agro_testes=(
  CoreApplicationTests
  domain.propriedades.AcessoPropriedadeTest
  domain.operacao.DadosCultivoTest
  domain.operacao.DadosAtividadeTest
  domain.operacao.DadosInsumoTest
  domain.operacao.DadosOcorrenciaTest
  domain.operacao.DadosCustoTest
)
if [[ "${1:-}" == "--atlas" ]]; then
  : "${MONGODB_URI:?Carregue o .env antes de executar --atlas}"
  agro_testes+=(infra.mongo.MongoAtlasValidacaoTest infra.mongo.CicloDadosAtlasTest)
fi
for agro_teste in "${agro_testes[@]}"; do
  "$agro_maven" -q exec:java "-Dexec.mainClass=br.com.agrogestao.core.$agro_teste" -Dexec.classpathScope=test
done
