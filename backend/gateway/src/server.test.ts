import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { after, before, test } from 'node:test';
import { createGateway } from './server.js';

const java = createServer((_, response) => {
  response.writeHead(200);
  response.end('{"status":"UP"}');
});
let gateway = createGateway('http://127.0.0.1:1');
let baseUrl = '';

before(async () => {
  await new Promise<void>((resolve) => java.listen(0, '127.0.0.1', resolve));
  const javaAddress = java.address();
  if (!javaAddress || typeof javaAddress === 'string') throw new Error('Endereço Java inválido');
  gateway = createGateway(`http://127.0.0.1:${javaAddress.port}`);
  await new Promise<void>((resolve) => gateway.listen(0, '127.0.0.1', resolve));
  const address = gateway.address();
  if (!address || typeof address === 'string') throw new Error('Endereço do gateway inválido');
  baseUrl = `http://127.0.0.1:${address.port}`;
});

after(async () => {
  await Promise.all([
    new Promise<void>((resolve, reject) => gateway.close((error) => error ? reject(error) : resolve())),
    new Promise<void>((resolve, reject) => java.close((error) => error ? reject(error) : resolve()))
  ]);
});

test('health responde sem consultar o Java', async () => {
  const response = await fetch(`${baseUrl}/health`);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { status: 'UP' });
});

test('ready responde quando o Java está saudável', async () => {
  const response = await fetch(`${baseUrl}/ready`);
  assert.equal(response.status, 200);
});

test('ready responde 503 quando o Java está indisponível', async () => {
  const unavailable = createGateway('http://127.0.0.1:1');
  await new Promise<void>((resolve) => unavailable.listen(0, '127.0.0.1', resolve));
  try {
    const address = unavailable.address();
    if (!address || typeof address === 'string') throw new Error('Endereço inválido');
    const response = await fetch(`http://127.0.0.1:${address.port}/ready`);
    assert.equal(response.status, 503);
  } finally {
    await new Promise<void>((resolve) => unavailable.close(() => resolve()));
  }
});

test('rotas desconhecidas retornam 404', async () => {
  const response = await fetch(`${baseUrl}/nao-existe`);
  assert.equal(response.status, 404);
});
