/*
 * Smoke das validações reais (não reaplica schema).
 * Pré-requisito: ./aplicar-schema.sh já rodou neste banco *_teste.
 *
 *   mongosh "$MONGODB_URI" --quiet --file mongo/schema-smoke.js
 */

const databaseName = process.env.MONGODB_DATABASE || db.getName();
if (!databaseName || databaseName === "test") {
  throw new Error("Defina MONGODB_DATABASE explicitamente antes do smoke");
}
if (!/_teste$/.test(databaseName) && process.env.ALLOW_NON_TEST_SCHEMA !== "1") {
  throw new Error(`Recusado: smoke só em banco *_teste (atual: ${databaseName})`);
}

db = db.getSiblingDB(databaseName);

function assertRejects(label, collection, doc) {
  let rejeitado = false;
  try {
    db.getCollection(collection).insertOne(doc);
  } catch (e) {
    rejeitado = e.codeName === "Document failed validation" || e.code === 121;
    if (!rejeitado) throw e;
  }
  if (!rejeitado) {
    throw new Error(`smoke falhou: ${label} deveria ser rejeitado em ${collection}`);
  }
}

function assertAccepts(label, collection, doc) {
  const result = db.getCollection(collection).insertOne(doc);
  if (!result.insertedId) throw new Error(`smoke falhou: ${label} não inseriu em ${collection}`);
  db.getCollection(collection).deleteOne({ _id: result.insertedId });
}

const marker = "schema_smoke_" + Date.now();

// Cadastro real usa farm (sem nome).
assertAccepts(
  "propriedade com farm",
  "propriedades",
  { usuarioId: marker, farm: "Fazenda Smoke", area: "50 ha", criadaEm: new Date().toISOString() }
);

// API de propriedades usa nome.
assertAccepts(
  "propriedade com nome",
  "propriedades",
  {
    usuarioId: marker,
    nome: "Propriedade Smoke",
    localizacao: "Estrada",
    municipio: "Campinas",
    safraAtual: "2026/27",
    areaHectares: 12.5
  }
);

assertRejects(
  "propriedade sem nome e sem farm",
  "propriedades",
  { usuarioId: marker, area: "10 ha" }
);

assertRejects(
  "propriedade sem usuarioId",
  "propriedades",
  { farm: "Sem dono" }
);

assertAccepts(
  "custo válido",
  "custos",
  {
    propriedadeId: marker,
    description: "Adubo smoke",
    category: "Insumos",
    value: 10.5,
    date: "2026-03-01"
  }
);

assertRejects(
  "custo com value string",
  "custos",
  {
    propriedadeId: marker,
    description: "Inválido",
    category: "Insumos",
    value: "10.5",
    date: "2026-03-01"
  }
);

assertAccepts(
  "safra com cultura",
  "safras",
  {
    propriedadeId: marker,
    nome: "Safra Smoke",
    cultura: "Soja",
    dataInicio: "2026-01-01",
    dataFim: "2026-12-31",
    talhoes: [],
    etapa: "Planejamento"
  }
);

assertRejects(
  "safra sem cultura",
  "safras",
  {
    propriedadeId: marker,
    nome: "Safra sem cultura",
    dataInicio: "2026-01-01",
    dataFim: "2026-12-31",
    talhoes: [],
    etapa: "Planejamento"
  }
);

assertAccepts(
  "equipamento válido",
  "equipamentos",
  {
    propriedadeId: marker,
    nome: "Trator Smoke",
    status: "Ativo",
    tipo: "Trator"
  }
);

assertRejects(
  "equipamento sem status",
  "equipamentos",
  { propriedadeId: marker, nome: "Sem status" }
);

print("schema smoke: OK");
