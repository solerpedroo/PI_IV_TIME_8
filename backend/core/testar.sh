#!/usr/bin/env bash
# Autor: Vinícius Panutti Salgado — R.A. 25007329
# Interrompe a execução em falha de comando, variável não definida ou etapa com erro em pipeline.
set -euo pipefail
# Executa os próximos comandos a partir do diretório deste módulo, independentemente de onde o script foi chamado.
cd "$(dirname "$0")"
# Permite indicar o Maven por MAVEN_BIN e usa mvn como padrão.
agro_maven="${MAVEN_BIN:-mvn}"
# Permite redirecionar a saída de build por AGRO_BUILD_DIR sem modificar os fontes.
agro_build_dir="${AGRO_BUILD_DIR:-$PWD/target}"
# Começa com os testes externos do Atlas desativados.
agro_atlas=false
# Começa sem a demonstração de leitura em outra JVM.
agro_demonstrar=false
# Começa sem a pausa de inspeção manual no Atlas.
agro_pausar=false
# Percorre as opções recebidas na linha de comando.
for agro_arg in "$@"; do
  # Interpreta somente os modos documentados para esta suíte.
  case "$agro_arg" in
    # Ativa os testes que usam MongoDB Atlas.
    --atlas) agro_atlas=true ;;
    # Ativa Atlas e também a demonstração de leitura em processo independente.
    --demonstrar) agro_atlas=true; agro_demonstrar=true ;;
    # Solicita manter os dados de demonstração até a confirmação por ENTER.
    --pausar) agro_pausar=true ;;
    # Mostra o uso permitido e retorna erro para uma opção desconhecida.
    *) echo "Uso: $0 [--atlas | --demonstrar [--pausar]]" >&2; exit 2 ;;
  esac
done
# Impede solicitar pausa sem habilitar a demonstração.
if "$agro_pausar" && ! "$agro_demonstrar"; then
  # Explica a combinação obrigatória de opções ao usuário.
  echo "--pausar exige --demonstrar" >&2
  # Encerra com erro de uso antes de compilar ou acessar o banco.
  exit 2
fi
# Confere a disponibilidade do executável Maven configurado.
if ! command -v "$agro_maven" >/dev/null 2>&1; then
  # Orienta como apontar MAVEN_BIN para uma instalação disponível.
  echo "Maven não encontrado. Configure MAVEN_BIN com o caminho do executável mvn." >&2
  # Interrompe a suíte quando não há ferramenta de build utilizável.
  exit 1
fi
# Aplica as proteções de configuração somente se os testes Atlas foram solicitados.
if "$agro_atlas"; then
  # Exige URI definida antes de permitir o acesso ao banco externo.
  : "${MONGODB_URI:?Carregue o .env antes de executar o teste no Atlas}"
  # Evita gravar dados fictícios em agrogestao_dev: o Java também exige o sufixo.
  # Exige banco explicitamente identificado com sufixo _teste para proteger os dados da aplicação.
  if [[ "${MONGODB_DATABASE:-}" != *_teste ]]; then
    # Informa o formato exigido para o nome do banco de testes.
    echo "Para --atlas, defina MONGODB_DATABASE com nome terminado em _teste (ex.: agrogestao_ci_teste)." >&2
    # Aborta o modo Atlas se o banco não estiver devidamente separado.
    exit 1
  fi
fi
# Compila fontes e testes com Maven, usando o diretório de saída configurado para esta execução.
"$agro_maven" -q "-Dagro.build.directory=$agro_build_dir" test-compile
# Define as classes com main que compõem a suíte local de domínio.
agro_testes=(
  # Inclui a validação de configuração do core.
  CoreApplicationTests
  # Inclui as verificações de autorização de propriedade.
  domain.propriedades.AcessoPropriedadeTest
  # Inclui os testes dos modelos de cultivo e do avanço da safra.
  domain.operacao.DadosCultivoTest
  # Inclui os testes do modelo de atividade e seu histórico.
  domain.operacao.DadosAtividadeTest
  # Inclui os testes de catálogo e movimentação de insumos.
  domain.operacao.DadosInsumoTest
  # Inclui os testes do modelo de ocorrência e suas transições.
  domain.operacao.DadosOcorrenciaTest
  # Inclui os testes de custos com centavos exatos e referências operacionais.
  domain.operacao.DadosCustoTest
)
# Acrescenta as suítes de persistência somente no modo Atlas.
if "$agro_atlas"; then
  # Inclui validação de contas/propriedades e o ciclo integrado de dados no banco de testes.
  agro_testes+=(infra.mongo.MongoAtlasValidacaoTest infra.mongo.CicloDadosAtlasTest)
fi
# Executa cada classe da lista para propagar a primeira falha ao chamador.
for agro_teste in "${agro_testes[@]}"; do
  # Reinicia os argumentos adicionais para não contaminar a próxima classe da lista.
  agro_argumentos=""
  # Envia as opções de demonstração somente à suíte integrada que sabe interpretá-las.
  if [[ "$agro_teste" == "infra.mongo.CicloDadosAtlasTest" ]] && "$agro_demonstrar"; then
    # Habilita a leitura independente dos dados persistidos para esta execução.
    agro_argumentos=" --demonstrar"
    # Acrescenta a pausa somente se ela também tiver sido solicitada.
    if "$agro_pausar"; then agro_argumentos+=" --pausar"; fi
  fi
  # Executa a classe com o plugin Maven: usa java, classpath de teste e entrada/saída herdadas; os argumentos das linhas seguintes incluem a classe e as opções de demonstração.
  "$agro_maven" -q "-Dagro.build.directory=$agro_build_dir" exec:exec \
    -Dexec.executable=java -Dexec.classpathScope=test -Dexec.inheritIo=true \
    "-Dexec.args=-classpath %classpath br.com.agrogestao.core.$agro_teste$agro_argumentos"
done
