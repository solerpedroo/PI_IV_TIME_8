# Manual do usuário do AgroGestão

Este manual ensina a usar o AgroGestão sem precisar de conhecimento técnico. Ele
segue o caminho natural de quem começa: criar a conta, cadastrar a propriedade,
organizar as áreas e acompanhar a rotina da lavoura.

Os nomes de botões e campos abaixo são os que aparecem nas telas. Para detalhes
técnicos, veja o [contrato da API](06-contrato-api.md) e os
[casos de uso](03-casos-de-uso.md). O resultado das últimas validações está no
[relatório de testes](10-relatorio-de-testes.md).

## 1. Sobre o sistema

O AgroGestão reúne em um só lugar o que normalmente fica espalhado em cadernos,
planilhas e mensagens: propriedades, áreas de cultivo (talhões), safras,
atividades, estoque de insumos, ocorrências de campo, custos e relatórios.

Pontos que ajudam a entender o sistema:

- **Tudo é da propriedade que está ativa.** Se você tem mais de uma propriedade, as telas mostram só os dados da que está selecionada.
- **Os dados se conectam.** Uma saída de estoque ligada a uma atividade gera um custo automaticamente; o relatório da propriedade junta atividades, ocorrências e custos.
- **Seus dados são só seus.** Outro usuário não vê o que você registra.
- **A tela só confirma depois do servidor.** A mensagem verde de sucesso aparece quando o servidor realmente gravou. Se algo falhar, você vê a mensagem de erro e o botão **Tentar novamente**.

## 2. Requisitos para acesso

- Um navegador atualizado no computador ou no celular (os testes do projeto usam o Chromium, base do Chrome e do Edge).
- O endereço do sistema, informado pela equipe responsável. Em ambiente de desenvolvimento, `http://localhost:8080`.
- Um e-mail que você consiga abrir, para receber os códigos de confirmação.

**Limitações importantes de hoje**

- **Dependência do servidor.** Os dados vêm do servidor. Se ele estiver fora do ar, as telas mostram uma mensagem de erro e o botão **Tentar novamente**, e os valores aparecem como `-` até a resposta chegar. Nada é inventado para preencher a tela.
- **Modo em memória.** Quando o servidor roda sem banco de dados (modo `--memoria`, usado em testes e demonstrações), tudo some ao desligá-lo. Com o banco de dados ligado, os dados permanecem.
- **E-mail em ambiente local.** Se o envio de e-mail real não estiver configurado, o código de confirmação não chega na sua caixa de entrada: ele aparece no terminal do servidor. Peça o código a quem opera o ambiente.
- **Sessão.** Você fica conectado por até 12 horas. Se o servidor for reiniciado, será preciso entrar de novo.

## 3. Criar conta e entrar

### Criar sua conta

1. Na tela de login, clique em **Criar conta**.
2. Preencha **Nome completo**, **E-mail**, **Telefone**, **Senha**, **Confirmar senha**, **Nome da propriedade** e **Área da fazenda**.
3. Marque o aceite dos termos e clique para criar a conta.

Regras de preenchimento:

- O telefone segue o formato `(11) 91234-5678`.
- A senha precisa ter 8 ou mais caracteres, com 1 letra maiúscula, 1 número e 1 símbolo.
- Cada e-mail só pode ter uma conta.

### Confirmar o e-mail

1. Você recebe um código de 6 dígitos.
2. Na tela de confirmação, digite o código.
3. Se não recebeu, peça um novo envio. Há um intervalo de 30 segundos entre os pedidos.

Sem confirmar o e-mail, não é possível entrar.

### Entrar

1. Informe **E-mail** e **Senha** e clique em **Entrar**.
2. Você chega ao **Painel**.

Se errar a senha, a mensagem é "E-mail ou senha inválidos." (o sistema não revela se o e-mail existe).

### Esqueci minha senha

1. Na tela de login, clique em **Esqueceu a senha?**.
2. Informe o e-mail. O sistema sempre responde que o pedido foi recebido.
3. Digite o código recebido, a **Nova senha** e a **Confirmar senha**.
4. O código vale 30 minutos. Ao trocar a senha, você é desconectado de todos os lugares.

### Sair

Clique no seu nome, no canto inferior da barra lateral, e escolha **Sair**. Confirme em "Sair da conta?".

## 4. Gerenciar propriedades e talhões

### Propriedades

Abra **Configurações** na barra lateral e vá até **Dados da Propriedade**.

- **Nova propriedade:** informe **Nome da propriedade**, **Localização**, **Município**, **Área total (ha)** e **Safra atual** (no formato `2026/27`) e clique em **Salvar propriedade**.
- **Trocar a propriedade ativa:** escolha em **Propriedade ativa**. A tela recarrega e passa a mostrar os dados da propriedade escolhida.
- **Propriedade padrão:** a que abre quando você entra. Escolha em **Propriedade padrão** e clique em **Salvar propriedade padrão**. Isso vale a partir do próximo login.

Não é possível excluir a última propriedade, nem uma que já tenha talhões, custos, estoque ou histórico, nem uma que esteja em uso em outra sessão.

### Culturas

Antes de cadastrar um talhão, cadastre as culturas que você planta.

1. Abra **Culturas**.
2. Digite o **Nome da cultura** (por exemplo, Soja) e clique em **Cadastrar cultura**.

O talhão só aceita culturas que estejam nessa lista.

### Áreas de Cultivo (talhões)

1. Abra **Áreas de Cultivo** e clique em **Novo Talhão**.
2. Preencha **Nome do talhão**, **Cultura**, **Hectares**, **Safra** e **Status** (Ativo, Irrigação pendente ou Em preparo).
3. Clique em **Salvar talhão**.

O nome do talhão não pode se repetir na mesma propriedade. A tela mostra o total de hectares e a quantidade de talhões.

**Localização no mapa (opcional).** Em **Editar localização**, informe um ponto (**Latitude** e **Longitude**, ou use **Usar minha localização**) ou desenhe a área pelos **Vértices do polígono** (de 3 a 50 pontos, sem lados cruzados). Clique em **Salvar localização**. Para apagar, use **Remover localização**. Não dá para remover a localização de um talhão que tenha ocorrência marcada dentro dela.

## 5. Operação agrícola

### Safras

Uma safra é um ciclo de uma cultura em um ou mais talhões, dentro de um período.

1. Abra **Safras** e clique em **Nova safra**.
2. Preencha **Nome**, **Cultura**, **Início**, **Término** e **Talhões** (ao menos um).
3. Clique em **Criar ciclo**. A safra começa em **Planejamento**.
4. Conforme o trabalho avança, use o botão **Avançar para** a próxima etapa: Planejamento, Preparação, Plantio, Manejo, Colheita e Finalização. Não é possível voltar.
5. **Histórico da safra** mostra o que aconteceu nos talhões durante o período.

Regras que protegem seus dados:

- Um talhão não pode estar em duas safras com períodos que se cruzam.
- Só é possível excluir a safra enquanto ela está em Planejamento.
- Safra finalizada fica só para consulta.

**Registrar colheita.** A partir da etapa Plantio, clique em **Registrar colheita**, escolha **Safra** e **Talhão**, informe **Data da colheita**, **Quantidade**, **Unidade** (kg, t ou saca) e, se quiser, **Observação**, e clique em **Salvar colheita**. A tela calcula a produtividade e compara os ciclos.

### Atividades

1. Abra **Atividades** e clique em **Nova atividade**.
2. Preencha **Nome da atividade**, **Tipo** (Aplicação, Irrigação, Colheita, Manutenção ou Manejo), **Talhão**, **Safra** (opcional), **Data**, **Horário**, **Responsável** e **Prioridade**. Se quiser, anexe uma foto em **Evidência fotográfica (opcional)**.
3. Clique em **Salvar atividade**.

Você alterna entre **Kanban** (colunas por status) e **Lista**. Na visão Kanban, o **Calendário** ao lado mostra as atividades por **Semana** ou **Mês**.

O status segue esta ordem: Pendente, Agendada, Em andamento e Concluída. Uma atividade Pendente precisa ser agendada antes de iniciar. Você muda o status pelo menu do cartão ou arrastando o cartão. Para **Concluir** ou cancelar, confirme na janela que abrir (no cancelamento, o motivo é opcional). Atividade concluída não pode ser cancelada nem reaberta.

Concluir uma atividade **não** baixa o estoque nem gera custo sozinho. Para isso, registre a saída no estoque ligando a atividade (veja a seção 6).

## 6. Estoque, ocorrências e custos

### Estoque (Insumos)

1. Abra **Insumos** e clique em **Novo produto**.
2. Preencha **Nome**, **Categoria** (Fertilizante, Fungicida, Herbicida, Inseticida ou Semente), **Unidade**, **Saldo inicial**, **Estoque mínimo**, **Limiar crítico (opcional)** e **Custo unitário (R$)**. Clique em **Cadastrar**.

No dia a dia, use as ações do produto:

- **Registrar entrada:** informe **Produto**, **Quantidade**, **Fornecedor** e o custo, e clique em **Confirmar entrada**. O custo médio do produto é recalculado.
- **Registrar saída:** informe **Produto** e **Quantidade**. Em **Atividade consumidora (opcional)** escolha uma atividade, ou **Saída manual (sem atividade)**. Clique em **Confirmar saída**. Se você ligou uma atividade, o sistema gera um custo na categoria Insumos.
- **Ajustar saldo:** use quando a contagem física for diferente. Informe o **Saldo contado** e o **Motivo** e clique em **Confirmar ajuste**.
- **Histórico:** mostra cada movimentação, com saldo antes e depois.

Cada produto tem um indicador: **Normal**, **Baixo** (abaixo do mínimo) ou **Crítico** (no limiar crítico ou abaixo). **O saldo nunca fica negativo**: uma saída maior que o saldo é recusada. Só é possível excluir um produto com saldo zero, e ele fica arquivado, com o histórico preservado.

### Ocorrências

Use para registrar problemas de campo e acompanhá-los até a solução.

1. Abra **Ocorrências** e clique em **Registrar ocorrência**.
2. Preencha **Título**, **Tipo** (Praga, Doença, Clima, Equipamento ou Outro), **Talhão (opcional)** (ou "Ocorrência geral da propriedade"), **Data da ocorrência**, **Prioridade** e **Descrição**. Você pode anexar uma foto e, se o talhão tiver área desenhada no mapa, marcar a posição por **Latitude** e **Longitude**.
3. Toda ocorrência nasce como **Aberta**.

Para acompanhar, abra a ocorrência e use a **Linha do tempo**: **Acompanhar** muda o status para **Em acompanhamento**, e **Publicar atualização** acrescenta uma nota. Para encerrar, use **Marcar resolvida**, informe o **Desfecho** e clique em **Confirmar resolução**. Ocorrências excluídas continuam consultáveis.

Se alguém alterar a mesma ocorrência antes de você salvar, o sistema avisa que a versão ficou antiga. Abra de novo e repita a alteração.

### Custos e orçamento

1. Abra **Custos** e clique em **Registrar custo**.
2. Preencha **Descrição**, **Categoria** (Insumos, Mão de Obra, Maquinário, Combustível ou Outros), **Valor**, **Data** e, se quiser, **Talhão** e **Safra**. Clique em **Salvar lançamento**.

Para filtrar, use **Selecionar período** (**De** e **Até**) e **Aplicar período**; **Limpar filtros** volta ao total. A tela mostra custos por categoria, por cultura e a evolução mensal.

**Orçamento da safra.** Em **Definir orçamento**, escolha a **Safra**, informe o **Planejado (R$)** e **Avisar ao usar (%)** (de 50 a 100) e clique em **Salvar orçamento**. A tela compara o planejado com o realizado e mostra se você está **dentro**, **próximo** do limite ou **acima**. Safra sem orçamento não mostra meta inventada. Use **Remover orçamento** para apagar.

Custos de safra finalizada não podem ser criados, alterados nem excluídos. Custos que vieram de uma saída de estoque ou de uma manutenção são corrigidos na origem, não no lançamento.

### Manutenção de equipamentos

Em **Manutenção**, cadastre máquinas com **Salvar equipamento**, programe revisões com **Salvar preventiva** e registre serviços feitos com **Registrar intervenção**. Quando você informa o valor de uma intervenção, o custo entra sozinho em Maquinário.

## 7. Relatórios e dashboard

### Painel

O **Painel** é a primeira tela depois do login e resume a propriedade: **Alertas**, **Culturas em Safra** e **Atividades da propriedade**. O sino de **Notificações** avisa sobre atrasos, estoque baixo, ocorrências e atividades do dia, e ao clicar você vai direto ao registro. No primeiro acesso, um passeio guiado apresenta as telas; você pode seguir com **Próximo** ou sair com **Pular tour**.

### Relatórios

Cada relatório abre por um botão na própria tela:

| Relatório | Onde | O que mostra |
|---|---|---|
| Relatório da propriedade | **Painel** | Atividades, ocorrências e custos do período. |
| Relatório de atividades | **Atividades** | Atividades planejadas e executadas, com atrasos. |
| Relatório de estoque | **Insumos** | Entradas e saídas do período. |
| Relatório da safra | **Safras** | O ciclo completo: talhões, registros, custos e pendências. |

Nos relatórios por período, informe o início e o fim (o início não pode ser depois do fim). Use **Exportar CSV** para levar o resultado ao Excel ou a outra planilha: o arquivo traz exatamente o que você viu na tela. Se uma das fontes de dados falhar, o relatório não aparece com zero: ele mostra o erro.

## 8. Perfil e segurança

Clique no seu nome, na barra lateral, e escolha **Meu perfil** ou **Configurações**.

- **Perfil do Usuário:** altere **Nome completo**, **E-mail** e **Telefone** e clique em **Salvar alterações**.
- **Trocar o e-mail:** informe também a **Senha atual para trocar e-mail**. O novo endereço fica pendente e o antigo continua valendo. Digite o **Código recebido** e clique em **Confirmar e-mail** (ou **Reenviar código**). Ao confirmar, você é desconectado e precisa entrar de novo.
- **Segurança:** informe **Senha atual**, **Nova senha** e **Confirmar nova senha** e clique em **Atualizar senha**. Você é desconectado de todos os dispositivos.
- **Preferências de Notificação:** escolha os avisos que quer receber (estoque, clima, atividades, ocorrências e relatórios) e clique em **Salvar preferências**. Hoje isso só grava a escolha; clima e relatórios por aviso ainda não são enviados.
- **Equipe, convites e plano:** aparecem apenas como informação. Não alteram o que cada pessoa pode fazer.

## 9. Perguntas frequentes

**Não recebi o código de confirmação.** Peça um novo envio, aguardando 30 segundos. Em ambiente local sem e-mail configurado, o código aparece no terminal do servidor.

**Digitei o código e deu "Código inválido ou expirado".** O código de redefinição de senha vale 30 minutos. Confira se digitou os 6 dígitos corretamente e, se preciso, peça um novo.

**Fui desconectado sozinho.** A sessão dura até 12 horas, e trocar senha ou e-mail, ou reiniciar o servidor, encerra todas as sessões. Entre de novo.

**A tela mostra um erro e `-` no lugar dos números.** O servidor não respondeu. Clique em **Tentar novamente**. Se continuar, avise quem opera o sistema.

**Meus dados sumiram.** Se o servidor está no modo em memória, desligá-lo apaga tudo. Com banco de dados ligado, os dados permanecem.

**Não consigo cadastrar o talhão: "Selecione uma cultura cadastrada".** Cadastre a cultura antes, na tela **Culturas**.

**Não consigo excluir uma propriedade, safra ou produto.** Há uma regra de proteção: propriedade com talhões, custos, estoque ou histórico; safra fora de Planejamento ou com custos; produto com saldo. Resolva a dependência e tente de novo.

**Não consigo editar uma safra.** Safra finalizada fica só para consulta, assim como seus custos e orçamento.

**A saída de estoque foi recusada.** A quantidade é maior que o saldo. O sistema nunca deixa o saldo negativo. Confira a contagem e, se preciso, use **Ajustar saldo**.

**A atividade não inicia.** Uma atividade Pendente precisa ser agendada antes de entrar em andamento.

**Concluí a atividade e o estoque não baixou.** Concluir não baixa estoque. Registre a saída em **Insumos** ligando a atividade em **Atividade consumidora**.

**Vejo "versão antiga" ao salvar uma ocorrência.** Outra pessoa ou aba alterou o registro antes. Abra de novo e refaça a alteração.
