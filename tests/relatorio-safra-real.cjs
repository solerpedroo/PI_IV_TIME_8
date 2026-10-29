/* Miguel — RA: 25003110 */
// Servidor Java + Mongo real + navegador: dados isolados e reinício do processo.
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const { randomBytes } = require('node:crypto');
const path = require('node:path');
const fs = require('node:fs/promises');
const net = require('node:net');
const { MongoClient, ObjectId } = require('mongodb');
const { chromium } = require('playwright');
const { launchTestBrowser } = require('./browser-launch.cjs');
const root = path.resolve(__dirname, '..');
const base = 'http://127.0.0.1:8080';
const database = `agro_i172_${randomBytes(12).toString('hex')}_teste`;
const uri = new URL(process.env.TEST_MONGO_URI || 'mongodb://127.0.0.1:27172/?replicaSet=rs172');
if (!['127.0.0.1', 'localhost'].includes(uri.hostname)) throw new Error('Use somente Mongo local de testes.');
uri.pathname = '/' + database;
const mongo = new MongoClient(uri.toString());
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
let server, browser, log = '', checks = 0;
function check(name, predicate) { assert.ok(predicate, name); checks++; console.log('ok ' + name); }
async function api(url, token, body, method, status = 200) {
  const response = await fetch(base + url, {method: method || (body ? 'POST' : 'GET'), headers: {
    'Content-Type': 'application/json', ...(token ? {Authorization: 'Bearer ' + token} : {})
  }, body: body ? JSON.stringify(body) : undefined});
  const data = await response.json().catch(() => ({}));
  assert.equal(response.status, status, `${url}: ${JSON.stringify(data)}`);
  return data;
}
async function start() {
  log = '';
  server = spawn(process.env.JAVA_BIN || 'java', ['-cp', process.platform === 'win32' ? 'bin;lib/*' : 'bin:lib/*', 'Servidor', '8080'], {
    cwd: path.join(root, 'backend/servidor'), env: {...process.env, MONGODB_URI: uri.toString(), SERVIDOR_RAIZ: path.join(root, 'frontend'),
      SMTP_HOST: '', SMTP_USERNAME: '', SMTP_PASSWORD: '', SMTP_FROM: ''}
  });
  server.on('error', error => { log += error.message; });
  server.stdout.on('data', chunk => { log += chunk; });
  server.stderr.on('data', chunk => { log += chunk; });
  for (let i = 0; i < 150; i++) {
    if (server.exitCode !== null) throw new Error('Servidor terminou: ' + log);
    try { if ((await fetch(base + '/ready')).ok) return; } catch {}
    await pause(100);
  }
  throw new Error('Servidor não iniciou: ' + log);
}
async function stop() {
  if (!server || server.exitCode !== null) return;
  const finished = new Promise(resolve => server.once('exit', resolve));
  server.stdin.write('desativar\n');
  await finished;
}
async function account(email) {
  await api('/api/auth/register', null, {name:'Operador Teste', email, phone:'(19) 98888-1111', password:'Senha@123', confirm:'Senha@123', farm:'Fazenda relatório', area:'120 ha', terms:true}, 'POST', 201);
  const user = await mongo.db(database).collection('usuarios').findOne({email});
  await api('/api/auth/confirm-email', null, {token:user.tokenConfirmacao});
  return api('/api/auth/login', null, {email,password:'Senha@123'});
}
(async () => {
  let created = false;
  try {
    const probe = net.createServer();
    await new Promise((resolve,reject) => probe.once('error',reject).listen(8080,'127.0.0.1',resolve));
    await new Promise(resolve => probe.close(resolve));
    await mongo.connect();
    assert.equal((await mongo.db().admin().listDatabases()).databases.some(item => item.name === database), false);
    created = true;
    await start();
    let session = await account('i172@agro.test');
    const other = await account('outro-i172@agro.test');
    let token = session.token;
    const property = session.usuario.propriedadeId;
    const plot = await api('/api/talhoes', token, {nome:'Norte',cultura:'Soja',hectares:12.5,status:'Ativo'}, 'POST', 201);
    const crop = await api('/api/safras', token, {nome:'Ciclo <Soja> 2026', cultura:'Soja', dataInicio:'2026-01-01',dataFim:'2026-12-31',talhoes:[plot.id]}, 'POST', 201);
    const next = await api('/api/safras', token, {nome:'Ciclo 2027', cultura:'Soja',dataInicio:'2027-01-01',dataFim:'2027-12-31',talhoes:[plot.id]}, 'POST', 201);
    const activity = {nome:'Plantio idêntico',tipo:'Manejo',talhao:'Norte',data:'2026-01-01',safraId:crop.id};
    await api('/api/atividades', token, activity, 'POST', 201);
    await api('/api/atividades', token, {...activity,data:'2026-12-31'}, 'POST', 201);
    await api('/api/atividades', token, {...activity,data:'2027-01-01',safraId:next.id}, 'POST', 201);
    await api('/api/atividades', token, {...activity,data:'2026-02-30'}, 'POST', 400);
    await api('/api/atividades', token, {...activity,safraId:new ObjectId().toHexString()}, 'POST', 400);
    // Legado de data sem ano: simula registro antigo sem mudar o cadastro atual.
    await mongo.db(database).collection('atividades').insertOne({...activity,_id:new ObjectId(),propriedadeId:property,usuarioId:session.usuario.id,data:'12 ago',status:'Pendente'});
    for (const value of [0.1,0.2]) await api('/api/custos', token, {description:'=SOMA(1;2) "ação"\nNova linha',category:'Outros',date:'2026-06-01',value,safraId:crop.id}, 'POST', 201);
    await api('/api/custos', token, {description:'Geral sem safra',category:'Outros',date:'2026-06-01',value:999}, 'POST', 201);
    await api('/api/custos', token, {description:'Fora do ciclo',category:'Outros',date:'2027-01-01',value:100,safraId:crop.id}, 'POST', 201);
    const occurrence = {titulo:'Praga <literal>',tipo:'Praga',data:'2026-06-01',prioridade:'Alta',descricao:'Inspeção real',talhaoId:plot.id};
    const occ = await api('/api/ocorrencias', token, occurrence, 'POST', 201);
    // Mesmo talhão/propriedade com outro usuário: relatório não pode expor o registro.
    await mongo.db(database).collection('ocorrencias').insertOne({...occ,_id:new ObjectId(),usuarioId:other.usuario.id,propriedadeId:property,excluida:false});
    const reportUrl = '/api/relatorios/safras/' + crop.id;
    let report = await api(reportUrl, token);
    check('conjunto conhecido por IDs, com limites inclusivos', report.contagens.atividade === 2 && report.contagens.ocorrencia === 1 && report.contagens.custo === 2);
    check('centavos exatos e área real', report.totalCentavos === 30 && report.areaHectares === 12.5);
    check('pendências preservadas sem afetar total', report.pendencias.length === 2);
    check('safra seguinte não mistura registros', (await api('/api/relatorios/safras/'+next.id,token)).contagens.atividade === 1);
    const emptyPlot = await api('/api/talhoes', token, {nome:'Vazio',cultura:'Soja',hectares:5,status:'Ativo'}, 'POST', 201);
    const empty = await api('/api/safras', token, {nome:'Sem registros',cultura:'Soja',dataInicio:'2026-01-01',dataFim:'2026-12-31',talhoes:[emptyPlot.id]}, 'POST', 201);
    const emptyReport = await api('/api/relatorios/safras/'+empty.id,token);
    check('conjunto vazio real', emptyReport.registros.length === 0 && emptyReport.totalCentavos === 0);
    await api(reportUrl, null, null, 'GET', 401);
    await api(reportUrl, other.token, null, 'GET', 404);
    await api('/api/relatorios/safras/invalido', token, null, 'GET', 400);
    await api('/api/relatorios/safras/'+new ObjectId(),token,null,'GET',404);
    check('API rejeita sessão ausente, safra alheia e IDs inválidos', true);
    browser = await launchTestBrowser(chromium);
    const page = await browser.newPage({acceptDownloads:true});
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.addInitScript(session => {
      sessionStorage.setItem('agro.sessionToken',session.token);
      sessionStorage.setItem('agro.sessionUser',JSON.stringify(session.usuario));
    }, session);
    await page.goto(base + '/safras.html');
    await page.locator(`[data-report="${crop.id}"]`).click();
    await page.waitForFunction(() => !document.querySelector('[data-export-report]').disabled);
    check('tela apresenta total do servidor e pendências', (await page.locator('[data-report-total]').innerText()).includes('0,30') && (await page.locator('[data-report-content]').innerText()).includes('12 ago'));
    check('HTML dos registros permanece literal', await page.locator('[data-report-content] script').count() === 0 && (await page.locator('[data-report-content]').innerText()).includes('Praga <literal>'));
    const downloadPromise = page.waitForEvent('download');
    await page.locator('[data-export-report]').click();
    const download = await downloadPromise;
    const csv = await fs.readFile(await download.path(), 'utf8');
    check('CSV exporta contexto, centavos e pendências', csv.includes(crop.id) && csv.includes('"0.30"') && csv.includes('"Pendência"') && csv.includes('12 ago'));
    check('CSV protege fórmula, aspas e acentos', csv.includes('"\'=SOMA(1;2) ""ação""\nNova linha"'));
    for (const item of [...report.registros,...report.pendencias]) assert.ok(csv.includes(item.id));
    check('CSV contém todos os mesmos IDs da tela/API', true);
    await page.reload();
    await page.waitForFunction(() => !document.querySelector('[data-export-report]').disabled);
    check('relatório reaberto após reload', (await page.locator('[data-report-total]').innerText()).includes('0,30'));
    check('relatório usa largura legível no desktop', await page.locator('[data-modal="safra-report"] .modal').evaluate(node => node.getBoundingClientRect().width) >= 600);
    await page.setViewportSize({width:390,height:844});
    check('relatório cabe no celular sem rolagem horizontal', await page.locator('[data-modal="safra-report"] .modal').evaluate(node => node.scrollWidth <= node.clientWidth));
    await page.setViewportSize({width:1280,height:720});
    if (process.env.TEST_ARTIFACT_DIR) {
      await fs.mkdir(process.env.TEST_ARTIFACT_DIR,{recursive:true});
      await page.screenshot({path:path.join(process.env.TEST_ARTIFACT_DIR,'relatorio-safra.png'),fullPage:true});
    }
    await page.keyboard.press('Escape');
    check('Escape fecha diálogo', await page.locator('[data-modal="safra-report"]').getAttribute('aria-hidden') === 'true');
    // Falha HTTP e resposta atrasada são injetadas só para validar estados da interface.
    await page.route('**/api/relatorios/safras/*', route => route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({error:'Fonte indisponível no teste'})}));
    await page.locator(`[data-report="${crop.id}"]`).click();
    await page.waitForFunction(() => document.querySelector('[data-report-message]').textContent.includes('Fonte indisponível'));
    check('falha não anuncia vazio nem permite exportação', await page.locator('[data-export-report]').isDisabled() && await page.locator('[data-report-content]').innerText() === '');
    await page.locator('[data-close-report]').click();
    await page.unroute('**/api/relatorios/safras/*');
    let release;
    const delayed = new Promise(resolve => {release=resolve;});
    await page.route('**/api/relatorios/safras/*', async route => {await delayed; await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(report)});});
    await page.locator(`[data-report="${crop.id}"]`).click();
    await page.locator('[data-close-report]').click();
    release();
    await page.waitForTimeout(150);
    check('fechar descarta resposta atrasada', await page.locator('[data-export-report]').isDisabled());
    await page.unroute('**/api/relatorios/safras/*');
    await page.locator(`[data-report="${crop.id}"]`).click();
    await page.waitForFunction(() => !document.querySelector('[data-export-report]').disabled);
    await page.evaluate(() => {const u=JSON.parse(sessionStorage.getItem('agro.sessionUser'));u.propriedadeId='outra';sessionStorage.setItem('agro.sessionUser',JSON.stringify(u));});
    await page.locator('[data-export-report]').click();
    check('troca de contexto invalida exportação', await page.locator('[data-export-report]').isDisabled() && (await page.locator('[data-report-message]').innerText()).includes('mudou'));
    check('sem exceções JavaScript', errors.length === 0);
    await browser.close(); browser = null;
    await stop(); await start();
    session = await api('/api/auth/login',null,{email:'i172@agro.test',password:'Senha@123'});
    const afterRestart = await api(reportUrl,session.token);
    assert.deepEqual(afterRestart,report);
    check('mesmos registros, campos e totais após reinício Java com persistência em Mongo',true);
    console.log(`Relatório safra real: ${checks} verificações aprovadas.`);
  } finally {
    await browser?.close();
    await stop();
    if (created) await mongo.db(database).dropDatabase();
    await mongo.close();
  }
})().catch(error => {console.error(error);process.exitCode=1;});
