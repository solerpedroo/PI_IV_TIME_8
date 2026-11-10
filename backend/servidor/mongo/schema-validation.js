/*
 * Validação estrutural das coleções do servidor AgroGestão.
 *
 * Uso:
 *   MONGODB_DATABASE=agrogestao_teste mongosh "$MONGODB_URI" --quiet \
 *     --file mongo/schema-validation.js
 *
 * A validação é deliberadamente moderada e não bloqueia campos adicionais:
 * documentos antigos podem continuar existindo, mas novas escritas e updates
 * que toquem documentos válidos precisam respeitar os tipos essenciais.
 */

const databaseName = process.env.MONGODB_DATABASE || db.getName();
if (!databaseName || databaseName === "test") {
  throw new Error("Defina MONGODB_DATABASE explicitamente antes de aplicar o schema");
}

db = db.getSiblingDB(databaseName);

const string = { bsonType: "string" };
const number = { bsonType: ["int", "long", "double", "decimal"] };
const date = { bsonType: ["date", "string"] };
const object = { bsonType: "object" };
const array = { bsonType: "array" };

const schemas = {
  usuarios: {
    required: ["email", "senhaHash", "emailConfirmado"],
    properties: { email: string, senhaHash: string, emailConfirmado: { bsonType: "bool" } }
  },
  propriedades: {
    required: ["usuarioId", "nome"],
    properties: { usuarioId: string, nome: string }
  },
  talhoes: {
    required: ["propriedadeId", "nome", "cultura"],
    properties: { propriedadeId: string, nome: string, cultura: string, hectares: number, areaHectares: number, status: string }
  },
  historicos_talhoes: {
    required: ["propriedadeId", "talhaoId"],
    properties: { propriedadeId: string, talhaoId: string, criadoEm: date, descricao: string }
  },
  atividades: {
    required: ["propriedadeId", "nome", "tipo", "data", "status"],
    properties: { propriedadeId: string, nome: string, tipo: string, data: string, horario: string, status: string, criadaEm: date }
  },
  atividades_historico: {
    required: ["propriedadeId", "atividadeId", "ordem", "status"],
    properties: { propriedadeId: string, atividadeId: string, ordem: number, status: string, registradoEm: date, descricao: string }
  },
  safras: {
    required: ["propriedadeId", "nome", "dataInicio", "dataFim", "talhoes", "etapa"],
    properties: { propriedadeId: string, nome: string, dataInicio: string, dataFim: string, talhoes: array, etapa: string, historico: array, historicoEtapas: array }
  },
  insumos: {
    required: ["propriedadeId", "nome", "categoria", "unidade"],
    properties: { propriedadeId: string, nome: string, categoria: string, unidade: string, estoqueMinimo: number, saldo: number, excluido: { bsonType: "bool" } }
  },
  movimentacoes_insumos: {
    required: ["propriedadeId", "insumoId", "tipo", "quantidade", "unidade"],
    properties: { propriedadeId: string, insumoId: string, tipo: string, quantidade: number, unidade: string, atividadeId: string, ocorridaEm: date, criadoEm: date }
  },
  ocorrencias: {
    required: ["usuarioId", "propriedadeId", "titulo", "tipo", "data", "status"],
    properties: { usuarioId: string, propriedadeId: string, titulo: string, tipo: string, data: string, status: string, descricao: string, criadaEm: date, excluida: { bsonType: "bool" } }
  },
  ocorrencias_timeline: {
    required: ["usuarioId", "propriedadeId", "ocorrenciaId", "ordem", "status"],
    properties: { usuarioId: string, propriedadeId: string, ocorrenciaId: string, ordem: number, status: string, data: date, descricao: string }
  },
  custos: {
    required: ["propriedadeId", "description", "category", "value", "date"],
    properties: { propriedadeId: string, description: string, category: string, value: number, date: string, talhaoId: string, safraId: string, origem: object, criadoEm: date }
  },
  colheitas: {
    required: ["propriedadeId", "safraId", "quantidade", "unidade", "data"],
    properties: { propriedadeId: string, safraId: string, talhaoId: string, quantidade: number, unidade: string, data: string, criadoEm: date }
  },
  orcamentos: {
    required: ["propriedadeId", "safraId", "valor"],
    properties: { propriedadeId: string, safraId: string, valor: number, alerta: number, criadoEm: date, atualizadoEm: date }
  }
};

for (const [name, schema] of Object.entries(schemas)) {
  const validator = { $jsonSchema: { bsonType: "object", ...schema } };
  const exists = db.getCollectionInfos({ name }).length > 0;
  if (!exists) db.createCollection(name, { validator, validationLevel: "moderate", validationAction: "error" });
  else db.runCommand({ collMod: name, validator, validationLevel: "moderate", validationAction: "error" });
  print(`schema aplicado: ${name}`);
}

print(`schema validation concluída: ${databaseName}`);
