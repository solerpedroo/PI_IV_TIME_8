# Dicionário de Dados do Sistema de Gestão Agrícola de Propriedades Rurais AgroGestão

## Histórico de Revisões (ou Versões)

| Data | Autor | Versão | Observações |
| ---- | ----- | ------ | ----------- |
| 19/11/2026 | Pedro Henrique Contardi Soler | 2.0.0 | Reescrita no padrão de dicionário de dados: título específico do AgroGestão, histórico e atributos por entidade. Inclui o membro da equipe, que a versão anterior não descrevia. |

## Entidades

A seguir são documentadas as entidades e seus dados (atributos) necessários para suportar a gestão de uma propriedade rural no AgroGestão: conta, fazenda, equipe, áreas de cultivo, safra, atividades, estoque, ocorrências, custos e manutenção. Um registro existe mesmo quando ainda não participou de outra operação. Um talhão sem atividade, um insumo sem saída e um convite ainda não aceito também são representados.

O código de cada entidade é um identificador único gerado pelo sistema, em texto hexadecimal de 24 caracteres. Não é um número sequencial. O usuário não escolhe esse código. Onde um atributo aponta para outra entidade, o valor é o código dessa entidade, e ela precisa já existir na mesma propriedade.

### 1. Usuário

Descrição: Representa a pessoa que possui conta no AgroGestão e pode ser dona de uma ou mais propriedades ou colaboradora de uma propriedade de outra pessoa. A conta existe antes de existir qualquer fazenda, como no cadastro feito a partir de um convite ainda não aceito.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia o usuário em relação aos demais em todas as partes do sistema. Gerado pelo sistema. |
| Nome | Texto | Sim | Nome da pessoa. Até 120 caracteres. Exemplo: "Ana Souza". |
| E-mail | Texto | Sim | E-mail de acesso. Único em todo o sistema, guardado em minúsculas. Exemplo: "ana@teste.com". |
| Telefone | Texto | Sim | Celular no formato "(DD) 9XXXX-XXXX". Exemplo: "(11) 91234-5678". |
| Senha | Texto | Sim | Guarda somente o resultado do embaralhamento. A senha escolhida pela pessoa não é armazenada e este valor não é devolvido nas consultas. |
| E-mail confirmado | Lógico | Sim | Começa falso. Passa a verdadeiro depois que a pessoa informa o código de 6 dígitos enviado ao e-mail. Sem isso, o acesso é recusado. |
| Código de confirmação | Texto | Não | Seis dígitos enviados no cadastro. Fica vazio depois da confirmação. |
| Último envio da confirmação | Inteiro | Sim | Instante, em milissegundos, do último envio do código. Serve para impedir reenvios seguidos. |
| Código de redefinição | Texto | Não | Seis dígitos enviados quando a pessoa pede uma senha nova. Vazio quando não há pedido em aberto. |
| Validade da redefinição | Inteiro | Não | Instante, em milissegundos, em que o código de redefinição deixa de valer. |
| E-mail pendente | Texto | Não | Novo e-mail aguardando confirmação. A conta continua no e-mail antigo até a troca ser confirmada. |
| Código da troca de e-mail | Texto | Não | Código enviado ao e-mail novo. |
| Validade da troca de e-mail | Inteiro | Não | Instante, em milissegundos, em que o código da troca deixa de valer. |
| Último envio da troca de e-mail | Inteiro | Não | Instante, em milissegundos, do último envio desse código. |
| Propriedade padrão | Texto | Não | Código da propriedade aberta no próximo acesso. Vazio enquanto a pessoa ainda não tem fazenda, como no convite não aceito. |
| Versão das propriedades | Inteiro | Não | Contador aumentado quando uma propriedade do usuário é excluída. |
| Preferências do painel | Par chave-valor | Não | Chaves fixas: estoque, clima, atividades, ocorrências e relatórios. O valor de cada uma é lógico. Se o conjunto não existir, todas nascem ligadas, exceto relatórios. |
| Tour concluído | Lógico | Sim | Falso no cadastro. Verdadeiro depois que a pessoa encerra o passeio inicial do painel. |
| Conclusão do tour | Data e hora | Não | Momento em que o tour foi concluído. Vazio enquanto ele não foi feito. |
| Criado em | Data e hora | Sim | Momento da criação da conta, no horário universal. |
| Atualizado em | Data e hora | Não | Momento da última alteração de nome ou telefone. |

### 2. Propriedade

Descrição: Representa uma fazenda gerida no sistema. Uma pessoa pode ter mais de uma. Os demais registros da operação (talhão, safra, estoque, custo, equipe) pertencem a uma propriedade e não são vistos a partir de outra. A propriedade existe mesmo sem talhão, safra ou colaborador.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia a propriedade em relação às demais. Gerado pelo sistema. |
| Proprietário | Texto | Sim | Código do usuário dono. Preenchido pelo sistema a partir de quem está autenticado. |
| Nome | Texto | Sim | Nome da fazenda, até 120 caracteres. Único entre as propriedades do mesmo dono. Exemplo: "Fazenda São João". |
| Localização | Texto | Sim | Descrição do lugar, até 160 caracteres. |
| Município | Texto | Sim | Município da fazenda, até 120 caracteres. Exemplo: "Campinas". |
| Safra atual | Texto | Sim | Período agrícola no formato AAAA/AA. O segundo número é o ano seguinte, com dois dígitos. Exemplo: "2026/27". |
| Área | Decimal | Sim | Área total em hectares. Maior que zero e até 100.000.000. |
| Criada em | Data e hora | Sim | Momento da criação. |
| Atualizada em | Data e hora | Sim | Momento da última alteração. |
| Nome legado | Texto | Não | Nome gravado no primeiro cadastro da conta, antes de existirem localização e município. A leitura usa este valor somente quando o nome atual está vazio. |
| Área legada | Texto | Não | Área informada em texto no primeiro cadastro. Não é convertida em número. |

Não é possível excluir a última propriedade de um dono, nem uma propriedade que ainda tenha talhão, estoque, ocorrência, custo, atividade, safra ou equipamento.

### 3. Membro da equipe

Descrição: Representa uma pessoa ligada a uma propriedade para colaborar nela. Pode ser um registro local, sem conta; um convite enviado a um e-mail que ainda não aceitou; ou um colaborador ativo, depois do aceite. O membro existe mesmo que a pessoa convidada ainda não tenha conta.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia o membro em relação aos demais. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda em que a pessoa colabora. |
| Proprietário | Texto | Sim | Código do usuário dono que incluiu o membro. |
| Usuário | Texto | Não | Código da conta do colaborador. Vazio no registro local e no convite ainda não aceito. Preenchido no aceite. |
| Nome | Texto | Sim | Nome do integrante, até 120 caracteres. No aceite, passa a ser o nome da conta, se ela tiver nome. |
| E-mail do convite | Texto | Não | E-mail para o qual o convite foi enviado. Obrigatório no convite. O aceite só vale se a conta autenticada tiver esse mesmo e-mail. |
| Cargo | Texto | Não | Função informada pelo dono, até 80 caracteres. Exemplo: "Operador de máquinas". |
| Situação | Lista lateral e estática de valores | Sim | Apenas um valor: "local", "convite_pendente" ou "ativo". Não devem ser acrescentados outros. |
| Telas | Par chave-valor | Sim | Chave é uma lista estática das telas do sistema: painel, áreas de cultivo, culturas, safras, atividades, insumos, manutenção, ocorrências, custos e configurações. Valor: lógico, indicando se a tela está liberada. Configurações nasce fechada. As demais nascem liberadas. |
| Token do convite | Texto | Não | Segredo do link de aceite. Existe só enquanto a situação é convite pendente. É apagado no aceite. |
| Validade do convite | Data e hora | Não | Momento em que o link deixa de valer. Vazio depois do aceite. |
| Criado em | Data e hora | Sim | Momento da inclusão. |
| Atualizado em | Data e hora | Sim | Momento da última alteração. |

### 4. Talhão

Descrição: Representa uma área de cultivo dentro de uma propriedade. O talhão existe mesmo que ainda não tenha atividade, ocorrência, custo ou colheita.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia o talhão em relação aos demais. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda dona. Preenchido pelo sistema. |
| Nome | Texto | Sim | Até 80 caracteres. Único na propriedade, sem diferenciar maiúsculas. Um talhão que já tenha atividade não pode ser renomeado. |
| Cultura | Texto | Sim | Nome da cultura cadastrada na propriedade, até 40 caracteres. É o nome, não o código da cultura. Exemplo: "Soja". |
| Safra | Texto | Sim | Período AAAA/AA do talhão. Exemplo: "2026/27". O período 1999/00 é válido, porque o sufixo 00 é o ano 2000. |
| Área | Decimal | Sim | Hectares do talhão. Maior que zero e até 1.000.000. |
| Situação | Lista lateral e estática de valores | Não | "Ativo", "Irrigação pendente" ou "Em preparo". Se ficar vazio, vale "Ativo". Apenas um valor. |
| Localização | Par chave-valor | Não | Chave é o tipo da marca no mapa: "ponto" ou "poligono". Valor: lista de pontos, cada um com latitude e longitude. |
| Criado em | Data e hora | Sim | Momento do cadastro. |
| Atualizado em | Data e hora | Sim | Momento da última alteração. |

### 5. Histórico do talhão

Descrição: Representa um acontecimento que aponta para um talhão, para que a área tenha uma linha do tempo. O histórico existe para atividade, ocorrência e custo já registrados. Não existe talhão só de histórico.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia o evento. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda. |
| Talhão | Texto | Sim | Código do talhão de origem. |
| Tipo | Lista lateral e estática de valores | Sim | "atividade", "ocorrencia" ou "custo". Apenas um valor. |
| Descrição | Texto | Sim | Texto do evento, até 240 caracteres. |
| Criado em | Data e hora | Sim | Momento do registro. |

### 6. Cultura

Descrição: Representa uma cultura agrícola disponível no catálogo de uma propriedade, para ser escolhida em talhões e safras. A cultura existe mesmo que nenhum talhão a utilize ainda. O catálogo nasce vazio. O produtor inclui os nomes.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia a cultura. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda. |
| Proprietário | Texto | Sim | Código do usuário que incluiu a cultura. |
| Nome | Texto | Sim | No mínimo 2 caracteres. Único na propriedade, sem diferenciar maiúsculas. Exemplo: "Milho". |
| Criada em | Data e hora | Sim | Momento da inclusão. |

### 7. Safra

Descrição: Representa um ciclo de cultivo de uma cultura, em um ou mais talhões, entre uma data de início e uma data de fim. A safra existe desde o planejamento, mesmo sem colheita, custo ou atividade.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia a safra. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda. |
| Nome | Texto | Sim | Até 80 caracteres. Único na propriedade, sem diferenciar maiúsculas. Exemplo: "Soja 26". |
| Cultura | Texto | Sim | Nome de uma cultura já cadastrada na propriedade, até 40 caracteres. |
| Talhões | Lista | Sim | Códigos dos talhões da propriedade que participam do ciclo. De 1 a 200, sem repetição. |
| Data de início | Data | Sim | Primeiro dia do ciclo, no formato AAAA-MM-DD. |
| Data de fim | Data | Sim | Último dia do ciclo. Não pode ser anterior ao início. |
| Etapa | Lista lateral e estática de valores | Sim | "Planejamento", "Preparação", "Plantio", "Manejo", "Colheita" ou "Finalização". O avanço é só para a etapa seguinte. Apenas um valor. |
| Histórico de etapas | Lista | Sim | Cada item guarda a etapa vencida e o momento em que ela foi registrada. |
| Finalizada em | Data e hora | Não | Preenchido ao chegar em "Finalização". Safra finalizada não aceita alteração. |
| Criada em | Data e hora | Sim | Momento do cadastro. |
| Atualizada em | Data e hora | Sim | Momento da última alteração. |

Um talhão não pode estar em duas safras cujos períodos se cruzam. A safra não perde um talhão que já tenha colheita, e a data de início não pode ficar depois de uma colheita já registrada.

### 8. Atividade

Descrição: Representa uma tarefa de campo planejada ou executada em um talhão. A atividade existe desde que é criada, mesmo que ainda esteja pendente e sem foto.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia a atividade. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda. |
| Usuário | Texto | Sim | Código de quem criou. |
| Nome | Texto | Sim | Até 100 caracteres. |
| Tipo | Lista lateral e estática de valores | Sim | "Aplicação", "Irrigação", "Colheita", "Manutenção" ou "Manejo". Apenas um valor. |
| Talhão | Texto | Sim | Nome do talhão, até 60 caracteres, comparado sem diferenciar maiúsculas. Não é o código do talhão. |
| Data | Data | Sim | Dia planejado. Aceita AAAA-MM-DD, como "2026-08-12", ou a forma curta "12 ago". |
| Situação | Lista lateral e estática de valores | Sim | "Pendente", "Agendada", "Em andamento", "Concluída" ou "Cancelada". Nasce "Pendente" ou "Agendada". |
| Responsável | Texto | Não | Até 100 caracteres. |
| Prioridade | Lista lateral e estática de valores | Não | "Baixa", "Média" ou "Alta". Apenas um valor. |
| Horário | Texto | Não | Hora do dia, de 00:00 a 23:59. |
| Safra | Texto | Não | Código da safra vinculada. O talhão precisa pertencer a ela. |
| Nome da safra | Texto | Não | Cópia do nome da safra no momento do vínculo. Preenchido pelo sistema. |
| Cultura | Texto | Não | Cópia da cultura da safra. Preenchida pelo sistema. |
| Foto de evidência | Par chave-valor | Não | Anexada depois do cadastro, não no mesmo envio. Chaves: conteúdo da imagem (até 5 MB, não sai nas consultas), tipo ("image/jpeg", "image/png", "image/webp" ou "image/gif"), nome do arquivo, tamanho em bytes e momento do envio. |
| Versão | Inteiro | Sim | Começa em 1 e controla alterações simultâneas. |
| Criada em | Data e hora | Sim | Momento do cadastro. |

A atividade e o primeiro evento do histórico dela são gravados juntos.

### 9. Histórico da atividade

Descrição: Representa um evento da vida de uma atividade, da criação até as mudanças de situação. Toda atividade tem pelo menos o evento inicial.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia o evento. Gerado pelo sistema. |
| Atividade | Texto | Sim | Código da atividade de origem. |
| Propriedade | Texto | Sim | Código da fazenda. |
| Ordem | Inteiro | Sim | Sequência a partir de 0. Única dentro da atividade. |
| Registrado em | Data e hora | Sim | Momento do evento. |
| Situação | Texto | Sim | Situação da atividade neste evento. |
| Ação | Texto | Sim | O que aconteceu. O primeiro evento usa "criacao". |
| Descrição | Texto | Sim | Texto do evento. O evento 0 é "Registro inicial da atividade". |

### 10. Insumo

Descrição: Representa um item do estoque da propriedade, com saldo e custo médio. O insumo existe mesmo com saldo zero e sem movimentação posterior à entrada inicial.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia o insumo. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda. |
| Nome | Texto | Sim | Até 100 caracteres. |
| Categoria | Lista lateral e estática de valores | Sim | "Herbicida", "Fertilizante", "Semente", "Fungicida" ou "Inseticida". Apenas um valor. |
| Unidade | Texto | Sim | Até 12 caracteres. Exemplo: "kg". |
| Estoque mínimo | Decimal | Sim | De 0 a 1 bilhão, com até 6 casas decimais. |
| Custo unitário | Decimal | Não | Custo médio por unidade, em reais, com até 6 casas. Se ficar vazio, vale 0. Não é armazenado em centavos. |
| Limiar crítico | Decimal | Não | Não pode ser maior que o estoque mínimo. |
| Saldo | Decimal | Sim | Quantidade atual. No cadastro pode ser informada. Depois disso só muda por movimentação. |
| Excluído | Lógico | Não | Exclusão lógica. Só é permitida quando o saldo é zero. |
| Criado em | Data e hora | Sim | Momento do cadastro. |
| Atualizado em | Data e hora | Sim | Momento da última alteração. |

### 11. Movimentação de insumo

Descrição: Representa uma entrada, uma saída ou um ajuste no saldo de um insumo. O cadastro de um insumo que já nasce com saldo também gera um ajuste, com a observação "Saldo inicial do cadastro".

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia a movimentação. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda. |
| Insumo | Texto | Sim | Código do insumo movimentado. |
| Tipo | Lista lateral e estática de valores | Sim | "entrada", "saida" ou "ajuste". Apenas um valor. |
| Quantidade | Decimal | Sim | Positiva na entrada e na saída. No ajuste, a quantidade registrada é a diferença até o novo saldo. |
| Unidade | Texto | Sim | Copiada do insumo no momento da movimentação. |
| Saldo anterior | Decimal | Sim | Saldo antes da operação. Preenchido pelo sistema. |
| Saldo posterior | Decimal | Sim | Saldo depois da operação. Preenchido pelo sistema. A saída que deixaria o saldo negativo é recusada. |
| Observação | Texto | Não | Até 240 caracteres. Obrigatória no ajuste. |
| Fornecedor | Texto | Não | Até 120 caracteres. |
| Atividade | Texto | Não | Só na saída. Código de uma atividade da mesma propriedade. |
| Nome da atividade | Texto | Não | Título da atividade vinculada, copiado pelo sistema. Vazio quando não há vínculo. |
| Custo unitário | Decimal | Não | Custo desta movimentação, em reais. Na saída ligada a uma atividade, usa o custo médio do insumo. |
| Criada em | Data e hora | Sim | Momento da movimentação. |

### 12. Ocorrência

Descrição: Representa um problema ou evento de campo, como praga, doença ou clima. A ocorrência existe desde a abertura, mesmo sem acompanhamento e sem foto.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia a ocorrência. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda. |
| Usuário | Texto | Sim | Código de quem registrou. |
| Título | Texto | Sim | Até 160 caracteres. |
| Tipo | Lista lateral e estática de valores | Sim | "Praga", "Doença", "Clima", "Equipamento" ou "Outro". Apenas um valor. |
| Prioridade | Lista lateral e estática de valores | Sim | "Baixa", "Média" ou "Alta". Apenas um valor. |
| Data | Data | Sim | Dia da ocorrência, AAAA-MM-DD. |
| Descrição | Texto | Não | Até 6000 caracteres. |
| Talhão | Texto | Não | Código de um talhão da propriedade. |
| Nome do talhão | Texto | Não | Cópia do nome do talhão. Preenchida pelo sistema. |
| Safra do talhão | Texto | Não | Período AAAA/AA copiado do talhão. |
| Localização | Par chave-valor | Não | Ponto marcado no mapa. Só é aceito dentro do polígono do talhão informado. |
| Situação | Lista lateral e estática de valores | Sim | "ABERTA", "EM_ACOMPANHAMENTO" ou "RESOLVIDA". Nasce "ABERTA". |
| Situação inicial | Texto | Sim | Sempre "ABERTA". Guardada para a linha do tempo. |
| Foto de evidência | Par chave-valor | Não | Mesma estrutura da foto da atividade: conteúdo, tipo, nome, tamanho e momento do envio. Anexada depois do cadastro. |
| Criada em | Data e hora | Sim | Momento da abertura. |
| Atualizada em | Data e hora | Sim | Momento da última versão. |
| Resolvida em | Data e hora | Não | Preenchido ao resolver. |
| Excluída | Lógico | Sim | Exclusão lógica. Nasce falso. |
| Excluída em | Data e hora | Não | Momento da exclusão lógica. |
| Versão | Inteiro | Sim | Começa em 1 e aumenta a cada alteração. |

### 13. Acompanhamento da ocorrência

Descrição: Representa uma versão da ocorrência, para que o histórico do que foi escrito e da situação não se perca. Cada alteração da ocorrência gera um acompanhamento.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia o acompanhamento. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda. |
| Usuário | Texto | Sim | Código do dono do registro. |
| Ocorrência | Texto | Sim | Código da ocorrência de origem. |
| Ordem | Inteiro | Sim | Igual à versão da ocorrência naquele momento. Única dentro da ocorrência. |
| Data | Data e hora | Sim | Momento da versão. |
| Ação | Lista lateral e estática de valores | Sim | "acompanhamento", "atualizacao" ou "resolucao". |
| Situação | Texto | Sim | Situação da ocorrência naquela versão. |
| Descrição | Texto | Sim | Texto do evento, até 6000 caracteres. |
| Dados | Par chave-valor | Sim | Cópia dos campos da ocorrência naquela versão: título, tipo, data, prioridade, descrição, talhão, safra e localização. |

### 14. Custo

Descrição: Representa um lançamento de dinheiro gasto na propriedade. Pode ser digitado pela pessoa ou nascer de uma saída de estoque ou de uma manutenção de equipamento. O valor fica em reais, com duas casas, e não em centavos.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia o custo. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda. |
| Descrição | Texto | Sim | Até 120 caracteres. |
| Categoria | Lista lateral e estática de valores | Sim | "Insumos", "Mão de Obra", "Maquinário", "Combustível" ou "Outros". Apenas um valor. |
| Valor | Decimal | Sim | Em reais, de 0,01 até 1.000.000.000,00, arredondado a duas casas. Exemplo: 350,50. |
| Data | Data | Sim | Dia do gasto, AAAA-MM-DD. |
| Talhão | Texto | Não | Código de um talhão da propriedade. |
| Safra | Texto | Não | Código de uma safra da propriedade. Se o talhão também for informado, ele precisa participar dessa safra. |
| Origem | Par chave-valor | Não | Presente só quando o custo foi gerado sozinho. A chave "tipo" vale "consumo" ou "manutencao". No consumo, o valor traz a movimentação, o insumo, a atividade, a quantidade, a unidade e o custo unitário. Na manutenção, traz a intervenção e o equipamento. |
| Criado em | Data e hora | Sim | Momento do lançamento. |
| Atualizado em | Data e hora | Sim | Momento da última alteração. |

Custo com origem não é alterado nem apagado direto. Ele só muda pela operação que o gerou. Custo de safra finalizada não pode ser alterado. Há no máximo um custo por movimentação de consumo e um por intervenção.

### 15. Orçamento

Descrição: Representa o valor planejado para uma safra. Existe no máximo um orçamento por safra. O quanto já foi gasto, o desvio e o percentual não são gravados. Eles são calculados a partir dos custos da safra.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia o orçamento. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda. |
| Safra | Texto | Sim | Código da safra. Único na propriedade: uma safra, um orçamento. |
| Valor | Decimal | Sim | Planejado, em reais, maior que zero e até 1.000.000.000,00. |
| Alerta | Inteiro | Não | Percentual do planejado que dispara o aviso, de 50 a 100. Se ficar vazio, vale 80. |
| Criado em | Data e hora | Sim | Momento do cadastro. |
| Atualizado em | Data e hora | Sim | Momento da última alteração. |

### 16. Colheita

Descrição: Representa a produção colhida em um talhão de uma safra, em um dia. A colheita só existe depois que a safra já começou e o talhão faz parte dela.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia a colheita. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda. |
| Safra | Texto | Sim | Código da safra, a partir da etapa de plantio. |
| Talhão | Texto | Sim | Código de um talhão que pertence a essa safra. |
| Quantidade | Decimal | Sim | Na unidade escolhida. Mínimo 0,001, com 3 casas. |
| Unidade | Lista lateral e estática de valores | Sim | "kg", "t" (1.000 kg) ou "sc" (saca de 60 kg). Apenas um valor. |
| Quantidade em quilos | Decimal | Sim | A mesma quantidade convertida para kg. Preenchida pelo sistema. |
| Data | Data | Sim | Dia da colheita. Não pode ser futuro nem anterior ao início da safra. |
| Observação | Texto | Não | Até 200 caracteres. |
| Criada em | Data e hora | Sim | Momento do registro. |
| Atualizada em | Data e hora | Sim | Momento da última alteração. |

Safra finalizada não aceita inclusão, alteração nem exclusão de colheita.

### 17. Equipamento

Descrição: Representa uma máquina ou implemento da propriedade. O equipamento existe mesmo sem preventiva e sem intervenção.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia o equipamento. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda. |
| Nome | Texto | Sim | Até 80 caracteres. Único na propriedade, sem diferenciar maiúsculas. |
| Tipo | Lista lateral e estática de valores | Sim | "Trator", "Colheitadeira", "Pulverizador", "Plantadeira", "Implemento", "Veículo" ou "Outro". Apenas um valor. |
| Marca | Texto | Não | Texto livre, com limite de tamanho. Exemplo: "John Deere". |
| Modelo | Texto | Não | Texto livre, com limite de tamanho. |
| Identificação | Texto | Não | Placa ou outro identificador livre. |
| Ano | Inteiro | Não | De 1950 até o ano corrente. |
| Horímetro | Decimal | Não | Horas de uso, de 0 a 1.000.000. |
| Situação | Lista lateral e estática de valores | Não | "Ativo", "Em manutenção" ou "Inativo". Se ficar vazio, vale "Ativo". |
| Criado em | Data e hora | Sim | Momento do cadastro. |
| Atualizado em | Data e hora | Sim | Momento da última alteração. |

### 18. Preventiva

Descrição: Representa uma manutenção prevista para um equipamento, em uma data. A preventiva existe mesmo que ainda não tenha sido realizada.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia a preventiva. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda. |
| Equipamento | Texto | Sim | Código de um equipamento da propriedade. |
| Descrição | Texto | Sim | Até 120 caracteres. Exemplo: "Troca de óleo". |
| Data prevista | Data | Sim | Próximo dia previsto, AAAA-MM-DD. |
| Intervalo em dias | Inteiro | Não | De 1 a 3650. Quando existe, permite gerar a preventiva seguinte ao realizar esta. |
| Situação | Lista lateral e estática de valores | Sim | "programada", "realizada" ou "cancelada". Nasce "programada". |
| Intervenção | Texto | Não | Código da intervenção que realizou esta preventiva. |
| Realizada em | Data | Não | Dia em que foi realizada. Vazio enquanto estiver programada. |
| Criada em | Data e hora | Sim | Momento do cadastro. |
| Atualizada em | Data e hora | Sim | Momento da última alteração. |

### 19. Intervenção

Descrição: Representa uma manutenção já feita em um equipamento, preventiva ou corretiva. Pode gerar um custo e, quando a preventiva de origem tem intervalo, a próxima data.

Atributos:

| Nome | Tipo | Obrigatoriedade | Exemplos/Comentários |
| ---- | ---- | --------------- | -------------------- |
| Código | Texto único | Sim | Diferencia a intervenção. Gerado pelo sistema. |
| Propriedade | Texto | Sim | Código da fazenda. |
| Equipamento | Texto | Sim | Código do equipamento atendido. |
| Preventiva | Texto | Não | Código da preventiva que esta intervenção encerrou. |
| Tipo | Lista lateral e estática de valores | Sim | "preventiva" ou "corretiva". Apenas um valor. |
| Data | Data | Sim | Dia do serviço. Não pode ser futura. |
| Descrição | Texto | Sim | Até 240 caracteres. |
| Responsável | Texto | Não | Texto livre, com limite de tamanho. |
| Horímetro | Decimal | Não | Horas do equipamento na data do serviço. |
| Valor | Decimal | Não | Custo em reais, de 0,01 a 1.000.000.000,00. Quando informado, gera um custo na categoria Maquinário. |
| Talhão | Texto | Não | Código do talhão em que a máquina atuou. |
| Safra | Texto | Não | Código da safra vinculada. |
| Custo | Texto | Não | Código do custo gerado a partir do valor. Preenchido pelo sistema. |
| Próxima preventiva | Texto | Não | Código da preventiva criada a partir do intervalo. Preenchido pelo sistema. |
| Criada em | Data e hora | Sim | Momento do registro. |
| Atualizada em | Data e hora | Sim | Momento da última alteração. |
