# AgroGestão
## Plataforma Integrada de Gestão Agrícola

> **Uma plataforma integrada de gestão agrícola que centraliza propriedades, talhões, safras, atividades, insumos, ocorrências e custos, conectando essas informações para facilitar o acompanhamento da operação e a tomada de decisões.**

---

## Sumário

- [1. Visão geral](#1-visão-geral)
- [2. Problema](#2-problema)
- [3. Solução proposta](#3-solução-proposta)
- [4. Público-alvo](#4-público-alvo)
- [5. Objetivos](#5-objetivos)
- [6. Conceito central do sistema](#6-conceito-central-do-sistema)
- [7. Estrutura funcional](#7-estrutura-funcional)
- [8. Módulos](#8-módulos)
  - [8.1 Dashboard](#81-dashboard)
  - [8.2 Propriedades](#82-propriedades)
  - [8.3 Talhões](#83-talhões)
  - [8.4 Safras e culturas](#84-safras-e-culturas)
  - [8.5 Atividades](#85-atividades)
  - [8.6 Estoque](#86-estoque)
  - [8.7 Ocorrências](#87-ocorrências)
  - [8.8 Custos](#88-custos)
  - [8.9 Relatórios](#89-relatórios)
  - [8.10 Notificações](#810-notificações)
  - [8.11 Perfil e configurações](#811-perfil-e-configurações)
- [9. Integração entre módulos](#9-integração-entre-módulos)
- [10. Fluxos principais](#10-fluxos-principais)
- [11. Modelo conceitual](#11-modelo-conceitual)
- [12. Exemplo de utilização](#12-exemplo-de-utilização)
- [13. Diferencial](#13-diferencial)
- [14. Escopo do MVP](#14-escopo-do-mvp)
- [15. Fora do escopo](#15-fora-do-escopo)
- [16. Evoluções futuras](#16-evoluções-futuras)
- [17. Critérios de sucesso](#17-critérios-de-sucesso)
- [18. Resumo para apresentação](#18-resumo-para-apresentação)

---

# 1. Visão geral

O **AgroGestão** é uma plataforma integrada de gestão agrícola voltada principalmente para **pequenos e médios produtores rurais**.

A proposta é centralizar, em um único sistema, informações que normalmente ficam distribuídas entre:

- cadernos;
- planilhas;
- WhatsApp;
- anotações;
- diferentes sistemas;
- registros manuais.

Em vez de tratar cada informação como um cadastro isolado, o sistema representa a operação da propriedade de forma integrada.

O produtor poderá:

- cadastrar propriedades;
- organizar talhões;
- acompanhar culturas e safras;
- planejar e executar atividades;
- controlar insumos;
- registrar ocorrências;
- acompanhar custos;
- visualizar indicadores;
- consultar históricos;
- gerar relatórios.

O principal conceito do produto é simples:

> **Uma informação registrada em uma área do sistema deve, quando aplicável, produzir reflexos nas demais áreas relacionadas.**

Por exemplo:

```text
Aplicação de fertilizante
        ↓
Atividade concluída
        ↓
Consumo de insumo registrado
        ↓
Estoque atualizado
        ↓
Custo registrado
        ↓
Talhão atualizado
        ↓
Safra atualizada
        ↓
Dashboard atualizado
```

Isso faz com que o AgroGestão deixe de ser apenas um conjunto de telas de cadastro e passe a representar a **operação real da propriedade rural**.

---

# 2. Problema

## 2.1 Problema central

Pequenos e médios produtores podem ter dificuldade para centralizar e acompanhar informações **operacionais, produtivas e financeiras** de suas propriedades.

Quando os dados ficam espalhados em diferentes meios, torna-se mais difícil compreender o estado atual da operação e manter um histórico confiável.

## 2.2 Problemas secundários

Essa falta de centralização pode resultar em:

- atividades esquecidas;
- dificuldade para acompanhar o que já foi realizado;
- perda do histórico dos talhões;
- falta de controle dos insumos;
- dificuldade para identificar gastos;
- informações espalhadas;
- dificuldade para acompanhar ocorrências;
- pouca visão geral da situação da propriedade.

## 2.3 Oportunidade

O sistema busca transformar dados dispersos em uma **visão estruturada, integrada e rastreável da operação agrícola**.

---

# 3. Solução proposta

O AgroGestão concentra a gestão da propriedade em uma única plataforma.

A estrutura básica do produto é:

```text
PROPRIEDADE
    │
    ├── TALHÕES
    │     │
    │     ├── CULTURAS
    │     │     │
    │     │     └── SAFRAS
    │     │
    │     └── HISTÓRICO
    │
    ├── ATIVIDADES
    │
    ├── ESTOQUE
    │
    ├── OCORRÊNCIAS
    │
    └── CUSTOS
            │
            ↓
       DASHBOARD
            │
            ↓
        RELATÓRIOS
```

O sistema deve permitir que o produtor acompanhe a propriedade sem precisar consultar diversas fontes diferentes.

---

# 4. Público-alvo

## Público principal

- Pequenos produtores rurais;
- Médios produtores rurais;
- Produtores que ainda utilizam métodos predominantemente manuais de gestão.

## Usuários secundários

O sistema também pode ser utilizado por:

- administradores de propriedades;
- técnicos agrícolas;
- responsáveis pela produção;
- gestores de fazendas;
- funcionários responsáveis pelo manejo.

### Direcionamento do projeto

Para o MVP acadêmico, o foco será em **pequenos e médios produtores**.

Não será objetivo inicial atender grandes operações agrícolas corporativas, pois isso aumentaria significativamente a complexidade e o escopo do produto.

---

# 5. Objetivos

## 5.1 Objetivo geral

Desenvolver uma plataforma integrada capaz de centralizar e relacionar informações relevantes da gestão agrícola, permitindo ao produtor acompanhar a operação da propriedade de forma organizada.

## 5.2 Objetivos específicos

- Centralizar informações da propriedade;
- Organizar talhões e culturas;
- Acompanhar safras;
- Planejar atividades;
- Registrar a execução das atividades;
- Controlar entradas e saídas de estoque;
- Registrar ocorrências;
- Acompanhar custos;
- Manter históricos;
- Disponibilizar indicadores;
- Emitir relatórios;
- Facilitar a identificação de pendências e situações críticas.

---

# 6. Conceito central do sistema

O principal diferencial conceitual do AgroGestão é a **integração entre os módulos**.

As informações possuem relacionamentos entre si.

Uma atividade, por exemplo, pode estar relacionada simultaneamente a:

- uma propriedade;
- um talhão;
- uma cultura;
- uma safra;
- um responsável;
- um ou mais insumos;
- um custo;
- uma ocorrência ou histórico.

## Exemplo

Imagine a atividade:

> **Aplicação de fertilizante no Talhão A**

O produtor registra:

```text
Atividade:
Aplicação de fertilizante

Talhão:
Talhão A

Insumo:
Fertilizante NPK

Quantidade:
50 kg
```

Ao executar a atividade:

```text
┌─────────────────────────────┐
│ Atividade concluída         │
└──────────────┬──────────────┘
               ↓
┌─────────────────────────────┐
│ 50 kg retirados do estoque  │
└──────────────┬──────────────┘
               ↓
┌─────────────────────────────┐
│ Custo da operação registrado│
└──────────────┬──────────────┘
               ↓
┌─────────────────────────────┐
│ Histórico do talhão alterado│
└──────────────┬──────────────┘
               ↓
┌─────────────────────────────┐
│ Dashboard atualizado        │
└─────────────────────────────┘
```

Esse encadeamento é o coração do produto.

---

# 7. Estrutura funcional

O sistema será dividido em módulos relacionados:

```text
                         AGROGESTÃO
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
  PROPRIEDADES            SAFRAS              DASHBOARD
        │                    │                    │
     TALHÕES              CULTURAS           INDICADORES
        │                    │                    │
        └────────────────────┼────────────────────┘
                             │
                     GESTÃO OPERACIONAL
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
   ATIVIDADES             ESTOQUE           OCORRÊNCIAS
        │                    │                    │
        └────────────────────┼────────────────────┘
                             │
                           CUSTOS
                             │
                         RELATÓRIOS
```

---

# 8. Módulos

## 8.1 Dashboard

O Dashboard será a **central de controle da propriedade**.

A pergunta que ele deve responder rapidamente é:

> **"Como está minha propriedade hoje?"**

### Operação

Exibir:

- atividades do dia;
- atividades pendentes;
- atividades atrasadas;
- próximas atividades.

### Estoque

Exibir:

- quantidade de insumos;
- itens com estoque baixo;
- itens críticos.

### Ocorrências

Exibir:

- problemas abertos;
- problemas em acompanhamento;
- ocorrências recentes.

### Financeiro

Exibir:

- custo da safra;
- custo por hectare;
- evolução dos gastos.

### Produção

Exibir:

- propriedades;
- talhões;
- culturas;
- safras ativas.

### Princípio de UX

O Dashboard deve funcionar principalmente como uma **central de acompanhamento e direcionamento**.

Não deve concentrar todas as operações de edição do sistema.

Cada indicador deve permitir, quando aplicável, navegar para o módulo correspondente.

---

## 8.2 Propriedades

Responsável por representar a estrutura física da operação.

### Cadastro da propriedade

Campos principais:

- nome;
- localização;
- área;
- informações gerais.

### Estrutura

```text
Propriedade
    ↓
Talhões
```

Uma propriedade poderá possuir diversos talhões.

---

## 8.3 Talhões

O talhão representa uma unidade de organização da produção dentro da propriedade.

### Informações

Cada talhão possuirá:

- nome;
- área;
- cultura;
- safra;
- status.

### Relação

```text
Propriedade
    ↓
Talhão
    ↓
Cultura
    ↓
Safra
```

Essa hierarquia será fundamental para o restante do sistema.

---

## 8.4 Safras e culturas

Este módulo permitirá acompanhar o ciclo produtivo.

### Uma safra possuirá

- cultura;
- talhões associados;
- período;
- área;
- status;
- atividades;
- ocorrências;
- custos.

### Ciclo visual

```text
Planejamento
      ↓
Preparação
      ↓
Plantio
      ↓
Manejo
      ↓
Colheita
      ↓
Finalização
```

O objetivo não é criar um sistema agronômico complexo.

O objetivo é permitir que o produtor saiba:

- qual produção está em andamento;
- em qual estágio ela está;
- quais atividades já foram realizadas;
- quais ocorrências aconteceram;
- quanto foi gasto.

---

## 8.5 Atividades

As atividades serão um dos principais módulos do sistema.

### Exemplos

- plantio;
- irrigação;
- adubação;
- aplicação de fertilizante;
- pulverização;
- inspeção;
- manutenção;
- colheita.

### Dados da atividade

Cada atividade poderá estar vinculada a:

- propriedade;
- talhão;
- cultura/safra;
- responsável;
- data;
- horário;
- prioridade;
- status.

### Status

```text
Agendada
    ↓
Em andamento
    ↓
Concluída
```

Também será possível:

- editar;
- cancelar;
- concluir;
- visualizar histórico.

---

## 8.6 Estoque

O módulo de estoque permitirá controlar os insumos utilizados na propriedade.

### Exemplos

- sementes;
- fertilizantes;
- defensivos;
- combustível;
- outros materiais.

### Informações do item

- quantidade disponível;
- unidade;
- estoque mínimo;
- categoria;
- histórico de movimentações.

### Tipos de movimentação

#### Entrada

Exemplo:

> Compra de 500 kg de fertilizante.

#### Saída

Exemplo:

> 50 kg utilizados na atividade do Talhão A.

#### Ajuste

Utilizado para corrigir diferenças identificadas no estoque.

---

## 8.7 Ocorrências

O módulo de ocorrências será utilizado para registrar problemas encontrados na propriedade.

### Exemplos

- pragas;
- doenças;
- problemas de irrigação;
- problemas em equipamentos;
- eventos climáticos;
- outros problemas operacionais.

### Informações

Cada ocorrência terá:

- título;
- tipo;
- propriedade;
- talhão;
- data;
- prioridade;
- descrição;
- foto, se desejado;
- status.

### Status

```text
Aberta
   ↓
Em acompanhamento
   ↓
Resolvida
```

### Timeline

Cada ocorrência poderá possuir uma linha do tempo para registrar seu acompanhamento.

Exemplo:

```text
15/08 — Ocorrência criada
        ↓
16/08 — Inspeção realizada
        ↓
17/08 — Medida de controle registrada
        ↓
20/08 — Problema resolvido
```

---

## 8.8 Custos

O módulo de custos permitirá acompanhar os gastos relacionados à operação agrícola.

### Categorias

- insumos;
- combustível;
- manutenção;
- mão de obra;
- outros.

### Indicadores

O usuário poderá visualizar:

- custo total;
- custo por safra;
- custo por cultura;
- custo por talhão;
- custo por categoria;
- evolução dos gastos.

### Objetivo

O módulo não pretende substituir um sistema contábil.

Seu objetivo é fornecer **controle gerencial**.

A principal pergunta respondida será:

> **"Onde estou gastando meu dinheiro?"**

---

## 8.9 Relatórios

Os dados registrados poderão ser organizados em relatórios.

No MVP, a quantidade de relatórios deverá ser controlada para evitar aumento desnecessário de escopo.

### Relatório da propriedade

Resumo geral da operação.

### Relatório da safra

Informações como:

- cultura;
- área;
- atividades;
- ocorrências;
- custos.

### Relatório de estoque

- entradas;
- saídas;
- estoque atual.

### Relatório de atividades

- atividades concluídas;
- atividades pendentes;
- atividades atrasadas.

---

## 8.10 Notificações

As notificações terão foco em **alertas operacionais**.

### Exemplos

> A atividade de irrigação do Talhão B está atrasada.

> O estoque de NPK está abaixo do mínimo.

> Existe uma ocorrência em acompanhamento no Talhão C.

> Você possui 3 atividades programadas para hoje.

### Comportamento

As notificações deverão ser interativas.

Ao clicar em uma notificação, o usuário deverá ser direcionado diretamente para o registro relacionado.

Exemplo:

```text
Notificação
"Estoque de NPK abaixo do mínimo"
        ↓ clique
Tela do item NPK
        ↓
Histórico de estoque
```

---

## 8.11 Perfil e configurações

Área destinada às informações do usuário e preferências do sistema.

### Perfil

- nome;
- e-mail;
- telefone;
- foto.

### Configurações

- preferências;
- notificações;
- propriedade padrão;
- segurança;
- sair da conta.

---

# 9. Integração entre módulos

A integração é o principal elemento que diferencia o sistema.

## Exemplo completo

```text
PROPRIEDADE
    │
    └── Talhão A
          │
          └── Safra 2026 — Soja
                    │
                    └── Atividade
                         "Aplicação de fertilizante"
                              │
                              ├── 50 kg de NPK
                              │
                              ├── Estoque
                              │     └── -50 kg
                              │
                              ├── Custos
                              │     └── + custo da aplicação
                              │
                              └── Histórico
                                    └── Talhão A atualizado
```

Depois:

```text
Ocorrência
"Praga no Talhão A"
        ↓
Em acompanhamento
        ↓
Tratamento registrado
        ↓
Resolvida
        ↓
Histórico atualizado
        ↓
Dashboard atualizado
```

---

# 10. Fluxos principais

## 10.1 Fluxo operacional

```text
LOGIN
  ↓
DASHBOARD
  ↓
PROPRIEDADE
  ↓
TALHÃO
  ↓
SAFRA
  ↓
ATIVIDADE
  ↓
EXECUÇÃO
  ↓
ESTOQUE / CUSTO
  ↓
HISTÓRICO
  ↓
DASHBOARD
```

## 10.2 Fluxo de ocorrência

```text
PROPRIEDADE
      ↓
TALHÃO
      ↓
OCORRÊNCIA
      ↓
ACOMPANHAMENTO
      ↓
RESOLUÇÃO
      ↓
HISTÓRICO
```

## 10.3 Fluxo de estoque

```text
COMPRA
  ↓
ENTRADA NO ESTOQUE
  ↓
INSUMO DISPONÍVEL
  ↓
UTILIZAÇÃO EM ATIVIDADE
  ↓
SAÍDA DO ESTOQUE
  ↓
ESTOQUE ATUALIZADO
```

## 10.4 Fluxo de alerta

```text
EVENTO
  ↓
REGRA IDENTIFICA SITUAÇÃO
  ↓
NOTIFICAÇÃO
  ↓
USUÁRIO CLICA
  ↓
REGISTRO RELACIONADO
```

---

# 11. Modelo conceitual

A estrutura conceitual pode ser representada inicialmente da seguinte forma:

```text
USUÁRIO
   │
   └── PROPRIEDADE
          │
          ├── TALHÃO
          │      │
          │      └── SAFRA
          │             │
          │             ├── ATIVIDADES
          │             ├── OCORRÊNCIAS
          │             └── CUSTOS
          │
          ├── ESTOQUE
          │      └── MOVIMENTAÇÕES
          │
          └── RELATÓRIOS
```

Uma atividade pode consumir recursos do estoque e gerar custos.

Uma ocorrência pode estar associada a um talhão e fazer parte do histórico da safra.

Dessa forma, o sistema cria uma rede de informações em vez de manter módulos independentes.

---

# 12. Exemplo de utilização

Para visualizar o funcionamento completo, considere o produtor **João**.

## 12.1 Cadastro

João cadastra:

```text
Fazenda Primavera
Área: 320 hectares
```

Depois cadastra:

```text
Talhão A
Área: 120 hectares
Cultura: Soja
```

Em seguida cria:

```text
Safra 2026
Cultura: Soja
```

---

## 12.2 Planejamento

João agenda:

```text
Atividade:
Aplicação de fertilizante

Data:
15/08

Talhão:
A

Insumo:
NPK

Quantidade:
50 kg
```

---

## 12.3 Execução

Ao executar a atividade:

- a atividade é concluída;
- 50 kg são retirados do estoque;
- o custo é registrado;
- o Talhão A recebe um novo registro no histórico;
- o dashboard é atualizado.

---

## 12.4 Nova ocorrência

Alguns dias depois, João identifica uma praga.

Ele registra:

```text
Ataque de lagarta
Talhão: A
Prioridade: Alta
Status: Em acompanhamento
```

João também adiciona uma foto.

A ocorrência passa a aparecer no Dashboard.

---

## 12.5 Resolução

Depois do acompanhamento:

```text
Aberta
  ↓
Em acompanhamento
  ↓
Resolvida
```

O histórico permanece registrado.

---

## 12.6 Final da safra

Ao final da safra, João consegue consultar:

- quanto gastou;
- quais atividades realizou;
- quais atividades ficaram pendentes;
- quais ocorrências foram registradas;
- quais talhões foram acompanhados;
- como o estoque foi movimentado.

Esse ciclo completo representa a proposta central do AgroGestão.

---

# 13. Diferencial

O sistema não deve ser apresentado simplesmente como:

> **"Um aplicativo para cadastrar fazendas."**

Isso reduziria a percepção de valor do projeto.

A proposta é:

> **Uma plataforma que conecta a gestão operacional, o controle de recursos e o acompanhamento da produção agrícola.**

## O verdadeiro diferencial

Uma ação realizada na propriedade pode refletir em diferentes áreas do sistema.

```text
AÇÃO NA PROPRIEDADE
        ↓
┌─────────────────────┐
│ Atividade           │
└──────────┬──────────┘
           ├────────→ Estoque
           ├────────→ Custos
           ├────────→ Histórico
           ├────────→ Safra
           └────────→ Dashboard
```

Isso proporciona uma visão mais coerente da operação.

---

# 14. Escopo do MVP

Considerando uma equipe de cinco integrantes e aproximadamente dois meses de desenvolvimento, o MVP deverá priorizar as funcionalidades essenciais.

## 14.1 Essencial

- [x] Login
- [x] Dashboard
- [x] Propriedades
- [x] Talhões
- [x] Safras
- [x] Atividades
- [x] Estoque
- [x] Ocorrências
- [x] Custos

## 14.2 Complementar

- [ ] Notificações
- [ ] Calendário
- [ ] Relatórios
- [ ] Upload de fotos
- [ ] Perfil

## 14.3 Futuro

- [ ] Inteligência artificial
- [ ] IoT
- [ ] Integração climática
- [ ] Sensores
- [ ] Mapas avançados
- [ ] Previsão de produtividade

### Regra do MVP

O objetivo não é resolver todos os problemas do agronegócio.

O objetivo é entregar um produto **completo, coerente e funcional dentro do tempo disponível**.

---

# 15. Fora do escopo

Para manter o projeto viável, as seguintes funcionalidades não farão parte do MVP:

- diagnóstico automático de doenças;
- IA para identificação de pragas;
- previsão de produtividade por IA;
- automação de máquinas agrícolas;
- integração com tratores;
- sensores IoT;
- drones;
- imagens de satélite;
- sistema contábil completo;
- marketplace de produtos agrícolas;
- compra e venda de insumos;
- integração bancária;
- folha de pagamento;
- gestão fiscal.

Essas funcionalidades podem ser consideradas em versões futuras, mas não devem comprometer o escopo inicial.

---

# 16. Evoluções futuras

O AgroGestão poderá evoluir gradualmente para uma plataforma mais inteligente.

## Inteligência artificial

Possibilidades:

- identificação de pragas por imagem;
- análise de doenças;
- recomendações;
- previsão de produtividade;
- análise de custos;
- identificação de padrões.

## Internet das Coisas

Possibilidades:

- sensores de umidade;
- sensores de temperatura;
- sensores de solo;
- monitoramento automático;
- integração com equipamentos.

## Clima

Possibilidades:

- previsão meteorológica;
- alertas climáticos;
- histórico climático;
- relação entre clima e atividades.

## Georreferenciamento

Possibilidades:

- mapas dos talhões;
- visualização da propriedade;
- localização de ocorrências;
- áreas de produção;
- mapas avançados.

---

# 17. Critérios de sucesso

O MVP poderá ser considerado bem-sucedido se permitir que um produtor consiga realizar, de ponta a ponta, o seguinte ciclo:

```text
Cadastrar propriedade
        ↓
Cadastrar talhão
        ↓
Cadastrar safra
        ↓
Planejar atividade
        ↓
Executar atividade
        ↓
Registrar consumo de insumo
        ↓
Atualizar estoque
        ↓
Registrar custo
        ↓
Atualizar histórico
        ↓
Visualizar informação no Dashboard
```

E também:

```text
Identificar problema
        ↓
Registrar ocorrência
        ↓
Acompanhar ocorrência
        ↓
Resolver problema
        ↓
Manter histórico
```

Se esses fluxos funcionarem de maneira integrada, o sistema terá cumprido sua proposta central.

---

# 18. Resumo para apresentação

## Em uma frase

> **O AgroGestão é uma plataforma integrada de gestão agrícola que permite ao produtor centralizar propriedades, talhões, safras, atividades, insumos, ocorrências e custos, conectando essas informações para facilitar o acompanhamento da operação e a tomada de decisões.**

## Em 30 segundos

O AgroGestão foi pensado para pequenos e médios produtores rurais que ainda dependem de cadernos, planilhas, WhatsApp e outros meios para administrar suas propriedades. A plataforma centraliza as informações da operação e conecta propriedades, talhões, safras, atividades, estoque, ocorrências e custos. O diferencial está na integração: quando uma atividade é executada, por exemplo, o consumo de insumos pode atualizar o estoque, gerar um custo, registrar o histórico do talhão e refletir no Dashboard. Assim, o sistema não é apenas um conjunto de cadastros, mas uma representação integrada da operação agrícola.

---

# Conclusão

O AgroGestão propõe uma abordagem simples e objetiva para um problema real: **a dispersão das informações utilizadas na gestão de pequenas e médias propriedades rurais**.

Em vez de tentar criar uma solução que englobe todo o agronegócio, o projeto concentra-se em um núcleo de funcionalidades que gera valor por meio da integração dos dados.

O sistema começa pela estrutura física da propriedade:

```text
Propriedade → Talhão → Cultura → Safra
```

e conecta essa estrutura à operação:

```text
Atividades → Estoque → Custos → Ocorrências → Histórico
```

Tudo converge para uma visão central:

```text
                    ┌──────────────┐
                    │  DASHBOARD   │
                    └──────┬───────┘
                           │
             ┌─────────────┼─────────────┐
             ↓             ↓             ↓
        Operação       Estoque       Ocorrências
             │             │             │
             └─────────────┼─────────────┘
                           ↓
                         Custos
                           ↓
                       Relatórios
```

O resultado esperado é uma plataforma capaz de transformar registros dispersos em **informação organizada, integrada e útil para o acompanhamento da propriedade rural**.

> **AgroGestão: da atividade realizada à visão completa da propriedade.**
