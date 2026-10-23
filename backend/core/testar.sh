#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
agro_maven="${MAVEN_BIN:-mvn}"
agro_build_dir="${AGRO_BUILD_DIR:-$PWD/target}"
agro_atlas=false
agro_demonstrar=false
agro_pausar=false
for agro_arg in "$@"; do
  case "$agro_arg" in
    --atlas) agro_atlas=true ;;
    --demonstrar) agro_atlas=true; agro_demonstrar=true ;;
    --pausar) agro_pausar=true ;;
    *) echo "Uso: $0 [--atlas | --demonstrar [--pausar]]" >&2; exit 2 ;;
  esac
done
if "$agro_pausar" && ! "$agro_demonstrar"; then
  echo "--pausar exige --demonstrar" >&2
  exit 2
fi
if ! command -v "$agro_maven" >/dev/null 2>&1; then
  echo "Maven não encontrado. Configure MAVEN_BIN com o caminho do executável mvn." >&2
  exit 1
fi
if "$agro_atlas"; then
  : "${MONGODB_URI:?Carregue o .env antes de executar o teste no Atlas}"
fi
"$agro_maven" -q "-Dagro.build.directory=$agro_build_dir" test-compile
agro_testes=(
  CoreApplicationTests
  domain.propriedades.AcessoPropriedadeTest
  domain.operacao.DadosCultivoTest
  domain.operacao.DadosAtividadeTest
  domain.operacao.DadosInsumoTest
  domain.operacao.DadosOcorrenciaTest
  domain.operacao.DadosCustoTest
)
if "$agro_atlas"; then
  agro_testes+=(infra.mongo.MongoAtlasValidacaoTest infra.mongo.CicloDadosAtlasTest)
fi
for agro_teste in "${agro_testes[@]}"; do
  agro_argumentos=""
  if [[ "$agro_teste" == "infra.mongo.CicloDadosAtlasTest" ]] && "$agro_demonstrar"; then
    agro_argumentos=" --demonstrar"
    if "$agro_pausar"; then agro_argumentos+=" --pausar"; fi
  fi
  "$agro_maven" -q "-Dagro.build.directory=$agro_build_dir" exec:exec \
    -Dexec.executable=java -Dexec.classpathScope=test -Dexec.inheritIo=true \
    "-Dexec.args=-classpath %classpath br.com.agrogestao.core.$agro_teste$agro_argumentos"
done
