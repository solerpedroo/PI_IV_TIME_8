/*
 * Validação estrutural das coleções do servidor AgroGestão.
 *
 * Uso (preferir o wrapper):
 *   ./backend/servidor/mongo/aplicar-schema.sh
 *
 * Ou diretamente:
 *   MONGODB_DATABASE=agrogestao_ci_teste mongosh "$MONGODB_URI" --quiet \
 *     --file mongo/schema-validation.js
 *
 * Por padrão só bancos terminados em `_teste` são aceitos. Para outro nome
 * (ex.: agrogestao_dev após revisão do grupo), exporte ALLOW_NON_TEST_SCHEMA=1.
 *
 * validationLevel: moderate — documentos legados inválidos podem continuar;
 * inserts e updates em documentos válidos precisam respeitar os tipos.
 * Campos extras continuam permitidos (additionalProperties implícito).
 */

const databaseName = process.env.MONGODB_DATABASE || db.getName();
if (!databaseName || databaseName === "test") {
  throw new Error("Defina MONGODB_DATABASE explicitamente antes de aplicar o schema");
}

const allowNonTest = process.env.ALLOW_NON_TEST_SCHEMA === "1";
if (!allowNonTest && !/_teste$/.test(databaseName)) {
  throw new Error(
    `Recusado: use um banco terminado em _teste (atual: ${databaseName}). ` +
    "Para outro banco, defina ALLOW_NON_TEST_SCHEMA=1 após revisão do grupo."
  );
}

db = db.getSiblingDB(databaseName);

const string = { bsonType: "string" };
const number = { bsonType: ["int", "long", "double", "decimal"] };
const bool = { bsonType: "bool" };
const date = { bsonType: ["date", "string"] };
const object = { bsonType: "object" };
const array = { bsonType: "array" };

const schemas = {
  usuarios: {
    required: ["email", "senhaHash", "emailConfirmado"],
    properties: { email: string, senhaHash: string, emailConfirmado: bool }
  },
  // Cadastro grava farm/area; API de propriedades grava nome/areaHectares.
  propriedades: {
    required: ["usuarioId"],
    properties: {
      usuarioId: string,
      nome: string,
      farm: string,
      area: string,
      areaHectares: number,
      localizacao: string,
      municipio: string,
      safraAtual: string
    },
    anyOf: [{ required: ["nome"] }, { required: ["farm"] }]
  },
  talhoes: {
    required: ["propriedadeId", "nome", "cultura"],
    properties: {
      propriedadeId: string, nome: string, cultura: string,
      hectares: number, areaHectares: number, status: string, safra: string
    }
  },
  culturas: {
    required: ["propriedadeId", "nome"],
    properties: {
      propriedadeId: string, proprietarioId: string, nome: string,
      nomeNormalizado: string, criadoEm: date
    }
  },
  historicos_talhoes: {
    required: ["propriedadeId", "talhaoId"],
    properties: { propriedadeId: string, talhaoId: string, criadoEm: date, descricao: string, tipo: string }
  },
  atividades: {
    required: ["propriedadeId", "nome", "tipo", "data", "status"],
    properties: {
      propriedadeId: string, nome: string, tipo: string, data: string,
      horario: string, status: string, criadaEm: date
    }
  },
  atividades_historico: {
    required: ["propriedadeId", "atividadeId", "ordem", "status"],
    properties: {
      propriedadeId: string, atividadeId: string, ordem: number,
      status: string, registradoEm: date, descricao: string, acao: string
    }
  },
  safras: {
    required: ["propriedadeId", "nome", "cultura", "dataInicio", "dataFim", "talhoes", "etapa"],
    properties: {
      propriedadeId: string, nome: string, cultura: string,
      dataInicio: string, dataFim: string, talhoes: array, etapa: string,
      historico: array, historicoEtapas: array
    }
  },
  insumos: {
    required: ["propriedadeId", "nome", "categoria", "unidade"],
    properties: {
      propriedadeId: string, nome: string, categoria: string, unidade: string,
      estoqueMinimo: number, saldo: number, excluido: bool
    }
  },
  movimentacoes_insumos: {
    required: ["propriedadeId", "insumoId", "tipo", "quantidade", "unidade"],
    properties: {
      propriedadeId: string, insumoId: string, tipo: string, quantidade: number,
      unidade: string, atividadeId: string, ocorridaEm: date, criadoEm: date
    }
  },
  ocorrencias: {
    required: ["usuarioId", "propriedadeId", "titulo", "tipo", "data", "status"],
    properties: {
      usuarioId: string, propriedadeId: string, titulo: string, tipo: string,
      data: string, status: string, descricao: string, criadaEm: date, excluida: bool
    }
  },
  ocorrencias_timeline: {
    required: ["usuarioId", "propriedadeId", "ocorrenciaId", "ordem", "status"],
    properties: {
      usuarioId: string, propriedadeId: string, ocorrenciaId: string,
      ordem: number, status: string, data: date, descricao: string
    }
  },
  custos: {
    required: ["propriedadeId", "description", "category", "value", "date"],
    properties: {
      propriedadeId: string, description: string, category: string, value: number,
      date: string, talhaoId: string, safraId: string, origem: object, criadoEm: date
    }
  },
  colheitas: {
    required: ["propriedadeId", "safraId", "quantidade", "unidade", "data"],
    properties: {
      propriedadeId: string, safraId: string, talhaoId: string,
      quantidade: number, unidade: string, data: string, criadoEm: date
    }
  },
  orcamentos: {
    required: ["propriedadeId", "safraId", "valor"],
    properties: {
      propriedadeId: string, safraId: string, valor: number,
      alerta: number, criadoEm: date, atualizadoEm: date
    }
  },
  equipamentos: {
    required: ["propriedadeId", "nome", "status"],
    properties: {
      propriedadeId: string, nome: string, nomeNormalizado: string, tipo: string,
      marca: string, modelo: string, identificacao: string, ano: number,
      horimetro: number, status: string, criadoEm: date, atualizadoEm: date
    }
  },
  preventivas: {
    required: ["propriedadeId", "equipamentoId", "descricao", "dataPrevista", "status"],
    properties: {
      propriedadeId: string, equipamentoId: string, descricao: string,
      dataPrevista: string, intervaloDias: number, status: string,
      intervencaoId: string, realizadaEm: date, geradaPorIntervencaoId: string,
      criadoEm: date, atualizadoEm: date
    }
  },
  intervencoes: {
    required: ["propriedadeId", "equipamentoId", "tipo", "data", "descricao"],
    properties: {
      propriedadeId: string, equipamentoId: string, preventivaId: string,
      proximaPreventivaId: string, tipo: string, data: string, descricao: string,
      responsavel: string, horimetro: number, valor: number,
      talhaoId: string, safraId: string, custoId: string,
      criadoEm: date, atualizadoEm: date
    }
  }
};

for (const [name, schema] of Object.entries(schemas)) {
  const validator = { $jsonSchema: { bsonType: "object", ...schema } };
  const exists = db.getCollectionInfos({ name }).length > 0;
  if (!exists) {
    db.createCollection(name, { validator, validationLevel: "moderate", validationAction: "error" });
  } else {
    db.runCommand({ collMod: name, validator, validationLevel: "moderate", validationAction: "error" });
  }
  print(`schema aplicado: ${name}`);
}

print(`schema validation concluída: ${databaseName}`);
