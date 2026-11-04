<!-- Autor: Pedro André do Carmo Chavier - RA: 25018639 -->
# Georreferenciamento - #230 (#231, #232, #233)

Cada talhão pode ter uma localização **simplificada**: um ponto ou um polígono leve
(de 3 a 50 vértices). O mapa de Áreas de Cultivo deixou de ser um desenho fixo de
exemplo e passou a mostrar só os talhões com localização cadastrada e as ocorrências
abertas deles. Fora do escopo: satélite, imagem, NDVI, camadas de mapa e GIS.
O contrato da API está no [README do servidor](../backend/api/README.md#georreferenciamento).

## Persistir a localização do talhão (#231)

- `PUT /api/talhoes/{id}/localizacao` grava `{tipo, pontos: [{lat, lng}]}` no campo
  `localizacao` do talhão (coleção `talhoes`); `DELETE` grava `localizacao: null`.
- A validação é feita no serversocket (`GeometriaDeTalhao.validarLocalizacao`):
  - `tipo`: `ponto` (exatamente 1 ponto) ou `poligono` (3 a 50 vértices);
  - `lat` entre -90 e 90 e `lng` entre -180 e 180, números finitos, guardados com 6 casas;
  - no polígono, o último vértice igual ao primeiro é descartado (o fechamento é implícito),
    dois vértices seguidos iguais são recusados, vértices todos na mesma reta (área zero) são
    recusados e os lados não podem se cruzar nem voltar sobre o vizinho (checagem par a par, O(n²)).
- Os erros voltam campo a campo (`tipo` e `pontos`) em 400. Tipos errados (`pontos` em texto,
  `lat` que não é número) são recusados pela API antes de chegar ao socket.
- Talhão de outra propriedade ou inexistente: 404. Propriedade trocada no meio da escrita: 409.
- A edição normal do talhão (`PUT /api/talhoes/{id}`) só faz `$set` de nome, cultura, safra,
  hectares e status: a localização é preservada.
- A área calculada é **informativa**: não substitui os `hectares` digitados no cadastro.
  A resposta do `PUT` traz `areaCalculadaHectares` (só no polígono) e `centro`, que não são gravados.

### Contas (serversocket, funções puras)

- Projeção local em metros a partir do primeiro vértice:
  `x = (lng - lng0) * cos(lat0) * 111320` e `y = (lat - lat0) * 110540`.
- Área: fórmula do laço (shoelace) na projeção, em m², dividida por 10.000 e com 2 casas.
- Centro: centroide do polígono na mesma projeção, convertido de volta para lat/lng; no ponto,
  o próprio ponto.
- Ponto no polígono: teste do raio direto em lat/lng (a área é pequena). **Decisão:** o ponto
  na borda (até ~0,1 mm) e o próprio vértice contam como dentro, para que a ocorrência marcada
  na cerca continue no talhão.

## Associar a ocorrência à área do talhão (#232)

- A ocorrência aceita `localizacao: {lat, lng}` opcional.
- Só é aceita quando a ocorrência tem `talhaoId` **e** o talhão tem polígono, e o ponto cai
  dentro dele (`GeometriaDeTalhao.pontoNoTalhao`). Sem talhão, com talhão sem localização ou com
  talhão de ponto: 400 no campo `localizacao` ("Cadastre o polígono do talhão para marcar a
  posição da ocorrência."). Nada de geometria inventada: sem posição, a ocorrência fica "no talhão"
  e aparece no centro dele no mapa.
- A posição entra também em `dados` de cada evento da linha do tempo.
- O `PUT` da ocorrência substitui os campos do cadastro, como antes: a tela reenvia a posição ao
  editar.
- Trocar o polígono deixando alguma ocorrência não excluída fora dele, ou trocar por um ponto, responde
  409 com mensagem genérica e os campos `ocorrenciaId` / `ocorrenciaTitulo`. Remover a localização com
  ocorrência marcada responde 409 "Há ocorrências marcadas neste polígono.". Assim nunca sobra ponto fora da área.

## Visualizar talhões no mapa (#233)

- `GET /api/mapa?cultura=&ocorrencias=abertas|todas` devolve os talhões localizados (com
  `localizacao`, `centro`, `areaCalculadaHectares` e as ocorrências), os `semLocalizacao` (com o
  número de ocorrências abertas), `ocorrenciasSemTalhao` e os `limites` (`null` sem nenhum talhão
  localizado). Padrão: `abertas` (status diferente de `RESOLVIDA`); excluídas nunca entram.
  A consulta carrega **todos** os talhões e ocorrências não excluídas da propriedade (filtro opcional
  por `cultura`); não há paginação — adequado ao escopo PI/fazenda única; propriedades muito grandes
  precisarão de recorte ou paginação futura.
- Áreas de Cultivo desenha um SVG simples (`viewBox` 320 x 220): projeção equirretangular dentro
  dos limites, com margem de 8%, escala horizontal corrigida pelo cosseno da latitude média e caixa
  mínima de ±0,002° em volta de um único ponto. Polígono = `polygon` com a cor da cultura; ponto =
  `circle`; ocorrência = marcador com a cor da prioridade (Alta, Média, Baixa). Ocorrências sem posição
  ficam no centro do talhão, espalhadas em volta para não se sobreporem.
- Clicar num talhão abre um painel com cultura, hectares, área calculada e a diferença para os hectares
  cadastrados, ocorrências abertas, link para Ocorrências filtradas pelo talhão e "Ver no OpenStreetMap"
  (só um link externo). Clicar num marcador abre o detalhe da ocorrência.
- A lista "Sem localização" tem "Definir localização" por talhão; a tabela ganhou "Localização" no menu
  de ações. O modal escolhe Ponto (latitude, longitude e "Usar minha localização", opcional) ou Polígono
  (um vértice por linha). Os erros do servidor voltam no campo e o sucesso só aparece depois da confirmação.
- Sem nenhum talhão localizado, o mapa fica vazio com a mensagem "Nenhum talhão com localização. Defina a
  localização na lista ao lado."; erro de API mostra a mensagem e "Tentar novamente".
- Em Ocorrências, o formulário mostra latitude e longitude só quando o talhão escolhido tem polígono; com
  talhão sem polígono aparece "Este talhão não tem polígono: a ocorrência fica no talhão.". O detalhe mostra
  a posição e "Ver no mapa".
- Localização e mapa não entram no `AgroLocal`: vêm sempre da API.

## Evidência

- `sh backend/serversocket/testar.sh`: `TesteGeometria` com 39 verificações (ponto arredondado, faixas,
  tipo inválido, 2 e 51 vértices, fechamento descartado, laço, área zero, lado que volta, quadrado de 0,01°
  no equador com ~123 ha, quadrado de ~100 m em Campinas com ~1 ha, centro, dentro, fora, borda, polígono
  côncavo, mapa com 2 talhões, mapa vazio e lista nula). `TesteAlgoritmos` 73 e `TesteProtocolo` 29 continuam.
- `sh backend/api/testar.sh`: `TesteGeorreferenciamento` com 40 verificações em memória (401, recusas campo a
  campo, 404, contexto trocado, ponto e polígono gravados, edição do talhão preservando a localização, ocorrência
  sem talhão, com talhão de ponto, sem localização, fora, dentro, edição e timeline com posição, mapa com
  posição própria e no centro, resolvida e excluída, filtro de cultura, trocar ou remover com ocorrência marcada,
  isolamento). Com `--mongo`, mais a leitura por repositórios novos (reinício).
- `node tests/georreferenciamento-real.cjs` (com o serversocket na porta 3000): mapa vazio sem desenho de exemplo,
  polígono e ponto pela tela, latitude 95 recusada no campo, área calculada e diferença, recarga, marcadores,
  bloco de posição em Ocorrências, ponto fora recusado, resolvida sai do mapa, 409 ao remover e remoção do ponto.
