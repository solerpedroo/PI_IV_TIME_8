#!/bin/sh
set -eu

cd "$(dirname "$0")/.."
: "${MONGODB_URI:?Defina MONGODB_URI antes de testar o schema}"
: "${MONGODB_DATABASE:?Defina MONGODB_DATABASE antes de testar o schema}"

case "$MONGODB_DATABASE" in
  *_teste) : ;;
  *) echo "Recusado: use um banco terminado em _teste para esta operação" >&2; exit 2 ;;
esac

command -v mongosh >/dev/null 2>&1 || {
  echo "mongosh não encontrado; instale o shell do MongoDB para executar este script" >&2
  exit 127
}

mongosh "$MONGODB_URI" --quiet --eval 'db = db.getSiblingDB(process.env.MONGODB_DATABASE)' --file mongo/schema-validation.js --eval '
  const nome = "schema_smoke_" + new Date().getTime();
  db.createCollection(nome, { validator: { $jsonSchema: { bsonType: "object", required: ["valor"], properties: { valor: { bsonType: "decimal" } } } }, validationAction: "error" });
  db.getCollection(nome).insertOne({ valor: Decimal128("1.00") });
  let rejeitado = false;
  try { db.getCollection(nome).insertOne({ valor: "1.00" }); } catch (e) { rejeitado = e.codeName === "Document failed validation" || e.code === 121; }
  db.getCollection(nome).drop();
  if (!rejeitado) throw new Error("o smoke test não rejeitou valor com tipo inválido");
  print("schema smoke: OK");
'
