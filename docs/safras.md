# Safras — API (I10) e UI (I11)

## Separação de frentes

| Frente | Issues | Onde vive | Papel |
|---|---|---|---|
| **API HTTP** | #14, #129–#131 | `backend/servidor` (`SafrasHandler`) | CRUD autenticado (Bearer), avanço de etapa, histórico, isolamento por propriedade |
| **UI** | #15, #132–#134 | `frontend/safras.html` + `frontend/scripts/safras.js` | Criar ciclo, acompanhar etapas, consultar registros/custos |
| **Modelo de dados** | #100–#105 (core) | `backend/core` | Domínio, índices, regras de vínculo/overlap/`historicoEtapas`; só `/health` e `/ready` na HTTP |

A tela **não** chama o `core` na porta 8081. Toda persistência de produto passa pelo servidor na 8080.

## Catálogo de culturas (UI)

O select de cultura é montado em runtime a partir de `TalhoesHandler.CULTURAS`
(Soja, Milho, Trigo, Café, Algodão), enriquecido com culturas já presentes nos
talhões/safras do usuário. Não há lista fixa no HTML.

## Referências

- Contrato HTTP: [backend/servidor/README.md](../backend/servidor/README.md) (seção Safras)
- Modelo acadêmico: [modelo-dados.md](modelo-dados.md) (#101)
- Visão do servidor: [servidor-java.md](servidor-java.md) §7.3
