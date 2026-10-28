/* Miguel — RA: 25003110 */
// Testa a API real após reiniciar o servidor, em um banco local isolado.
const assert = require('node:assert/strict');
const { spawn, execFileSync } = require('node:child_process');
const { randomBytes } = require('node:crypto');
const path = require('node:path');
const banco = `agro_i135_${randomBytes(16).toString('hex')}_teste`;
const base = 'http://127.0.0.1:18081';
const cwd = path.join(__dirname, '../backend/servidor');
const env = { ...process.env, MONGODB_URI: `mongodb://127.0.0.1:27017/${banco}?replicaSet=rs0`, SMTP_HOST: '', SMTP_USERNAME: '', SMTP_PASSWORD: '', SMTP_FROM: '' };
let processo, log = '';
const pausa = ms => new Promise(resolve => setTimeout(resolve, ms));
async function iniciar() {
  processo = spawn('java', ['-cp', process.platform === 'win32' ? 'bin;lib/*' : 'bin:lib/*', 'Servidor', '18081'], { cwd, env });
  processo.stdout.on('data', chunk => { log += chunk; });
  processo.stderr.on('data', chunk => { log += chunk; });
  for (let i = 0; i < 100; i++) {
    try { if ((await fetch(base + '/ready')).ok) return; } catch {}
    if (processo.exitCode !== null) throw new Error('Servidor terminou antes de iniciar: ' + log);
    await pausa(100);
  }
  throw new Error('Servidor não iniciou: ' + log);
}
async function parar() {
  if (!processo || processo.exitCode !== null) return;
  const terminou = new Promise(resolve => processo.once('exit', resolve));
  processo.stdin.write('desativar\n');
  await terminou;
}
async function api(url, body, token, method) {
  const res = await fetch(base + url, { method: method || (body ? 'POST' : 'GET'), headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  return { status: res.status, data: await res.json().catch(() => ({})) };
}
(async () => {
  let criado = false;
  try {
    try { await fetch(base + '/health'); throw new Error('Porta de teste ocupada'); } catch (err) { if (err.message === 'Porta de teste ocupada') throw err; }
    const bancos = JSON.parse(execFileSync('docker', ['exec', 'frontend_pi_iv-mongo-1', 'mongosh', '--quiet', '--eval', 'JSON.stringify(db.adminCommand({listDatabases:1,nameOnly:true}).databases.map(x=>x.name))'], { encoding: 'utf8' }));
    assert.ok(!bancos.includes(banco), 'Banco aleatório já existe');
    await iniciar(); criado = true;
    const email = 'i135@agro.test', password = 'Senha@123';
    assert.equal((await api('/api/auth/register', {name:'Operador Teste',email,phone:'(19) 98888-1111',password,confirm:password,farm:'Fazenda Teste',area:'120 ha',terms:true})).status, 201);
    for (let i = 0; i < 50 && !/token: ([\w-]+)/.test(log); i++) await pausa(100);
    const tokenEmail = log.match(/token: ([\w-]+)/)?.[1];
    assert.ok(tokenEmail, 'Token de confirmação não encontrado');
    assert.equal((await api('/api/auth/confirm-email', {token: tokenEmail})).status, 200);
    let login = await api('/api/auth/login', {email,password});
    assert.equal(login.status, 200); let token = login.data.token;
    const talhao = await api('/api/talhoes', {nome:'Norte',cultura:'Soja',hectares:10,status:'Ativo'}, token);
    assert.equal(talhao.status, 201);
    const outro = await api('/api/talhoes', {nome:'Sul',cultura:'Soja',hectares:10,status:'Ativo'}, token);
    assert.equal(outro.status, 201);
    const safra = await api('/api/safras', {nome:'Safra teste',cultura:'Soja',dataInicio:'2026-01-01',dataFim:'2026-12-31',talhoes:[talhao.data.id]}, token);
    assert.equal(safra.status, 201);
    const valores = {nome:'Plantio',tipo:'Manejo',talhao:'Norte',data:'2026-08-01',horario:'08:30',responsavel:'Equipe',prioridade:'Alta',safraId:safra.data.id};
    const atividade = await api('/api/atividades', valores, token);
    assert.equal(atividade.status, 201);
    const id = atividade.data.id;
    assert.equal((await api('/api/atividades/' + id, {...valores,talhao:'Sul'}, token, 'PUT')).status, 400);
    assert.equal((await api('/api/atividades', {...valores,safraId:'inexistente'}, token)).status, 400);
    assert.equal((await api('/api/atividades/' + id, {...valores,nome:'Plantio revisado',horario:'09:15'}, token, 'PUT')).status, 200);
    await parar(); log = ''; await iniciar();
    login = await api('/api/auth/login', {email,password}); assert.equal(login.status, 200); token = login.data.token;
    const consulta = await api('/api/atividades/' + id, null, token);
    assert.equal(consulta.status, 200);
    for (const [campo, esperado] of Object.entries({...valores,nome:'Plantio revisado',horario:'09:15'})) assert.equal(consulta.data[campo], esperado, campo);
    assert.equal(consulta.data.cultura, 'Soja');
    assert.equal((await api('/api/talhoes/' + talhao.data.id, {nome:'Renomeado',cultura:'Soja',hectares:10,status:'Ativo'}, token, 'PUT')).status, 409);
    assert.equal((await api('/api/safras/' + safra.data.id, null, token, 'DELETE')).status, 409);
    console.log('OK: criação, edição, vínculos, casos inválidos e consulta após reinício do servidor com Mongo.');
  } finally {
    await parar();
    // Somente o banco de nome aleatório criado nesta execução é removido.
    if (criado) execFileSync('docker', ['exec','frontend_pi_iv-mongo-1','mongosh','--quiet',banco,'--eval','db.dropDatabase()'], { stdio:'ignore' });
  }
})().catch(err => { console.error(err); process.exitCode = 1; });
