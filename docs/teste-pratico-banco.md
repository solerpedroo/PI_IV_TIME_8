# Teste prático do banco de dados

Este roteiro valida o código do PR #198 diretamente no MongoDB Atlas, com dados fictícios. Requer JDK 21, Maven, Bash e o `.env` local já configurado. Execute na pasta da branch `feat/dados-usuarios-propriedades`.

## Executar e ver os registros

Na raiz do projeto:

```bash
set -a
. ./.env
set +a
./backend/core/testar.sh --demonstrar --pausar
```

O script compila o core, executa os sete testes locais e a regressão da #100 e grava os cenários das #101–#105 no Atlas. Depois inicia outro processo Java, que abre sua própria conexão e relê os registros sem regravá-los. A saída mostra os dois números de processo, o nome do banco, os resultados por issue e um documento bruto de custo recebido do MongoDB. A URI e a senha não são impressas.

Na máquina usada nesta revisão, Maven está fora do `PATH` e o `target` preexistente não permite gravação. As opções abaixo usam o Maven já disponível e uma pasta de compilação gravável; em outras máquinas, ajuste `MAVEN_BIN` conforme sua instalação ou omita as duas variáveis se o ambiente padrão estiver pronto:

```bash
MAVEN_BIN=/tmp/apache-maven-3.9.11/bin/mvn \
AGRO_BUILD_DIR=/tmp/agro-teste-banco \
./backend/core/testar.sh --demonstrar --pausar
```

`AGRO_BUILD_DIR` deve apontar para uma pasta gravável, preferencialmente com caminho absoluto. O `.env` é carregado pelos comandos anteriores; essas variáveis adicionais não contêm credenciais.

## O que conferir na saída

- **#101:** Talhão A, Soja e Safra 2026, área de 12,75 ha e período de 01/01/2026 a 31/12/2026. A suíte também rejeita período invertido e referências inexistentes ou pertencentes a outro produtor/propriedade.
- **#102:** atividade AGENDADA → EM_ANDAMENTO → CONCLUIDA, com três eventos ainda consultáveis. Ordem repetida, data regressiva e vínculo fora da safra são rejeitados.
- **#103:** entrada de 500 kg e consumo de 50 kg ligados ao insumo e à atividade; a variação líquida do histórico é 450 kg e o mínimo é 100 kg. Unidade divergente e referências inválidas são rejeitadas.
- **#104:** ocorrência ABERTA → EM_ACOMPANHAMENTO → EM_ACOMPANHAMENTO → RESOLVIDA, com quatro eventos preservados. A disputa de dois clientes pela mesma ordem aceita exatamente uma gravação. Ocorrência geral da propriedade também é permitida.
- **#105:** custo direto de R$ 250,00 ligado ao consumo e total de R$ 300,00 na propriedade. O teste rejeita custo duplicado para o mesmo consumo, cultura divergente e origem incompatível.

O leitor independente também tenta consultar os registros com o outro produtor e exige resultados vazios em todos os módulos.

## Inspecionar no Atlas durante a pausa

O comando mostra um filtro com o ID fictício desta execução:

```javascript
{ propriedadeId: ObjectId("ID_MOSTRADO_NO_TERMINAL") }
```

No banco indicado no terminal, aplique esse filtro nas coleções `talhoes`, `culturas`, `safras`, `atividades`, `atividades_historico`, `insumos`, `movimentacoes_insumos`, `ocorrencias`, `ocorrencias_timeline` e `custos`. Os IDs de vínculo permitem seguir da safra até a atividade, o consumo e o custo. Em `propriedades`, o mesmo ID aparece no campo `_id`.

Em `atividades` e `ocorrencias`, `statusInicial` preserva o estado de criação. O estado atual está no evento de maior `ordem` do histórico ou da timeline, como descrito em [modelo-dados.md](modelo-dados.md). O catálogo de insumos guarda o mínimo; a soma das movimentações explica os 450 kg do exemplo.

Depois de inspecionar, pressione **ENTER** no terminal. O teste remove os dados fictícios e consulta novamente o Atlas para confirmar a limpeza. Espere a mensagem `Limpeza conferida no Atlas: nenhum registro fictício desta execução permaneceu.`

Para rodar com limpeza automática, use `--demonstrar` sem `--pausar`. `--atlas` executa os cenários sem o relatório demonstrativo; sem opções, o script executa somente os sete testes locais e não exige credenciais.

## Resultado desta revisão — 23/10/2026

A execução real no `agrogestao_dev` confirmou os valores acima em dois processos Java diferentes, os dois históricos, os vínculos, o isolamento e a limpeza. A revisão conferiu os critérios das #101–#105. O comando agora aceita uma pasta alternativa de compilação, e a limpeza usa uma conexão própria e verifica os registros removidos.

Este roteiro exercita os modelos e repositórios Java e sua persistência no MongoDB. As validações de vínculo estão nos repositórios; MongoDB não oferece chaves estrangeiras para impedir uma inserção manual que os contorne. Autenticação HTTP, telas, rotas CRUD e baixa transacional de estoque pertencem às suas issues (#112, #142, #160 e seguintes).
