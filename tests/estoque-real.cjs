/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
// Teste ponta a ponta: navegador, HTTP Java real, MongoDB e reinício do servidor.
const assert = require('node:assert/strict');
const path = require('node:path');
const {spawn, spawnSync} = require('node:child_process');
const {once} = require('node:events');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const {launchTestBrowser} = require('./browser-launch.cjs');
const base = 'http://127.0.0.1:8080';
let server, browser, log = '';

async function start() {
  server = spawn('java', ['-cp', 'bin:lib/*', 'CenarioEstoqueTeste'], {
    cwd: path.join(__dirname, '../backend/servidor'), env: {...process.env, SMTP_HOST: ''},
  });
  server.stdout.on('data', chunk => { log += chunk.toString(); });
  server.stderr.on('data', chunk => { log += chunk.toString(); });
  for (let i = 0; i < 100; i++) {
    try { if ((await fetch(base + '/health')).ok) return; } catch {}
    if (server.exitCode !== null) throw new Error('Servidor de teste encerrou: ' + log);
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error('Servidor não iniciou. ' + log);
}
async function stop() {
  if (server && server.exitCode === null) { const ended = once(server, 'exit'); server.stdin.write('desativar\n'); await ended; }
}
async function request(endpoint, token, method = 'GET', body, expected = 200) {
  const response = await fetch(base + endpoint, {method, headers: {'Content-Type': 'application/json',
    ...(token ? {Authorization: 'Bearer ' + token} : {})}, body: body === undefined ? undefined : JSON.stringify(body)});
  const data = response.status === 204 ? null : await response.json();
  assert.equal(response.status, expected, endpoint + ': ' + JSON.stringify(data));
  return data;
}
const login = email => request('/api/auth/login', null, 'POST', {email, password: 'Senha@123'});

async function openPage(session) {
  const context = await browser.newContext({viewport: {width: 1440, height: 1000}});
  await context.addInitScript(session => {
    sessionStorage.setItem('agro.sessionToken', session.token);
    sessionStorage.setItem('agro.sessionUser', JSON.stringify(session.usuario));
  }, session);
  const page = await context.newPage();
  page.setDefaultTimeout(10000);
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto(base + '/insumos.html');
  await page.waitForFunction(() => document.querySelector('[data-stock-message]')?.textContent === 'Estoque atualizado.');
  return {context, page, errors};
}
async function action(page, key) {
  const row = page.locator('[data-stock-table] tbody tr').first();
  await row.locator('[data-popover-trigger]').click();
  await row.locator(`[data-operation=${key}]`).click();
}
async function submit(page, selector, endpoint, status = 201) {
  const response = page.waitForResponse(r => r.url().endsWith(endpoint) && r.request().method() !== 'GET');
  await page.locator(selector).evaluate(form => form.requestSubmit());
  const result = await response;
  assert.equal(result.status(), status, await result.text());
  if (status < 300) await page.locator(selector).waitFor({state: 'hidden'});
  return result;
}

(async () => {
  assert.ok(process.env.MONGODB_URI, 'MONGODB_URI de teste é obrigatória');
  await assert.rejects(fetch(base + '/health'), 'porta 8080 deve estar livre');
  await start();
  const session = await login('estoque-e2e@agro.test');
  const other = await login('outro-estoque@agro.test');
  browser = await launchTestBrowser(chromium);
  let {context, page, errors} = await openPage(session);
  assert.equal(await page.locator('[data-stock-table] tbody tr').count(), 0, 'banco de teste novo deve estar vazio');
  assert.equal(await page.locator('[data-metric=total-products]').textContent(), '0');
  await page.locator('[data-open-produto]').click();
  const form = page.locator('[data-produto-novo-form]');
  for (const [field, value] of Object.entries({nome: 'Ureia E2E', unidade: 'kg', saldo: '10', minimo: '12', custoUnitario: '2', limiarCritico: '3'}))
    await form.locator(`[name=${field}]`).fill(value);
  await form.locator('[name=categoria]').selectOption('Fertilizante');
  await submit(page, '[data-produto-novo-form]', '/api/insumos');
  await page.waitForFunction(() => document.querySelector('[data-metric=total-products]').textContent === '1');
  let [product] = await request('/api/insumos', session.token);
  const endpoint = '/api/insumos/' + product.id + '/movimentacoes';
  assert.equal(product.status, 'Baixo');

  // Cadastro reaparece após reload; edição mantém nome e mínimo persistidos.
  await page.reload();
  await page.locator('[data-stock-table] tbody tr').waitFor();
  await action(page, 'editar');
  const edit = page.locator('[data-produto-editar-form]');
  await edit.locator('[name=nome]').fill('Ureia Editada E2E');
  await edit.locator('[name=minimo]').fill('15');
  await submit(page, '[data-produto-editar-form]', '/api/insumos/' + product.id, 200);
  await page.waitForFunction(() => document.querySelector('[data-stock-table] tbody')?.textContent.includes('Ureia Editada E2E'));

  await action(page, 'entrada');
  const entrada = page.locator('[data-entrada-form]');
  await entrada.locator('[name=quantidade]').fill('10');
  await entrada.locator('[name=custoUnitario]').fill('4');
  await entrada.locator('[name=fornecedor]').fill('Cooperativa E2E');
  // Dois submits enquanto a gravação está em voo devem gerar apenas uma entrada.
  const received = page.waitForResponse(r => r.url().endsWith(endpoint) && r.request().method() === 'POST');
  await entrada.evaluate(f => { f.requestSubmit(); f.requestSubmit(); });
  assert.equal((await received).status(), 201);
  await entrada.waitFor({state: 'hidden'});
  await page.waitForFunction(() => document.querySelector('[data-metric=entries]').textContent === '1');
  let summary = await request('/api/insumos/resumo', session.token);
  assert.equal(summary.valorEmEstoque, 60);
  assert.equal(summary.entradasNoMes, 1);
  assert.match(await page.locator('[data-metric=value]').textContent(), /60,00/);
  await page.screenshot({path: process.env.STOCK_SCREENSHOT || '/tmp/agro-estoque-verificado.png', fullPage: true});

  await action(page, 'saida');
  const saida = page.locator('[data-saida-form]');
  await saida.locator('[name=quantidade]').fill('99');
  await submit(page, '[data-saida-form]', endpoint, 409);
  assert.match(await saida.locator('[data-error-for=quantidade]').textContent(), /supera/);
  const activities = await request('/api/insumos/atividades', session.token);
  const foreignActivities = await request('/api/insumos/atividades', other.token);
  assert.equal(await saida.locator('[name=atividadeId] option').count(), 2, 'somente uma atividade real do titular');
  await request(endpoint, session.token, 'POST', {tipo: 'saida', quantidade: 1, atividadeId: foreignActivities[0].id}, 400);
  await request(endpoint, session.token, 'POST', {tipo: 'saida', quantidade: 1, atividadeId: '000000000000000000000001'}, 400);
  // Outra propriedade do mesmo titular também não pode referenciar este item/atividade.
  const property = await request('/api/propriedades', session.token, 'POST', {nome: 'Outra propriedade E2E',
    localizacao: 'Local de teste', municipio: 'Campinas', areaHectares: 10, safraAtual: '2026/27'}, 201);
  await request('/api/propriedades/' + property.id + '/selecionar', session.token, 'POST', {});
  await request(endpoint, session.token, 'GET', undefined, 404);
  assert.equal((await request('/api/insumos/atividades', session.token)).length, 0);
  await request('/api/propriedades/' + session.usuario.propriedadeId + '/selecionar', session.token, 'POST', {});
  await request('/api/propriedades/' + property.id + '/selecionar', session.token, 'POST', {});
  await request('/api/propriedades/' + session.usuario.propriedadeId, session.token, 'DELETE', undefined, 409);
  await request('/api/propriedades/' + session.usuario.propriedadeId + '/selecionar', session.token, 'POST', {});
  await saida.locator('[name=quantidade]').fill('18');
  await saida.locator('[name=atividadeId]').selectOption(activities[0].id);

  // Falha de comunicação não anuncia sucesso e não altera saldo no navegador.
  await page.route('**' + endpoint, route => route.request().method() === 'POST' ? route.abort() : route.continue());
  await saida.evaluate(f => f.requestSubmit());
  await page.waitForFunction(() => document.querySelector('[data-saida-form]')?.getAttribute('aria-busy') === 'false');
  assert.equal((await request('/api/insumos/' + product.id, session.token)).saldo, 20);
  await page.unroute('**' + endpoint);
  await submit(page, '[data-saida-form]', endpoint);
  await page.waitForFunction(() => document.querySelector('[data-metric=critical]').textContent === '1');
  assert.equal((await request('/api/insumos/' + product.id, session.token)).saldo, 2);
  await action(page, 'detail');
  await page.locator('[data-movement-history] ul').waitFor();
  const text = await page.locator('[data-movement-history]').textContent();
  assert.match(text, /Aplicação persistida/); assert.match(text, /Cooperativa E2E/);
  assert.match(text, new RegExp(activities[0].id));
  await page.locator('[data-modal=produto-detalhe] [data-close-modal]').click();
  assert.deepEqual(errors, []);
  await context.close();

  // Encerra o processo Java inteiro. Nova sessão consulta os mesmos documentos.
  await stop(); await start();
  const afterRestart = await login('estoque-e2e@agro.test');
  ({context, page, errors} = await openPage(afterRestart));
  assert.equal((await request('/api/insumos/' + product.id, afterRestart.token)).saldo, 2);
  assert.equal((await request(endpoint, afterRestart.token)).length, 3);
  await action(page, 'ajuste');
  const ajuste = page.locator('[data-ajuste-form]');
  await ajuste.locator('[name=quantidade]').fill('0');
  await ajuste.locator('[name=observacao]').fill('Contagem final E2E');
  await submit(page, '[data-ajuste-form]', endpoint);
  await action(page, 'excluir');
  const removed = page.waitForResponse(r => r.url().endsWith('/api/insumos/' + product.id) && r.request().method() === 'DELETE');
  await page.locator('[data-confirm-excluir]').click(); assert.equal((await removed).status(), 204);
  await page.waitForFunction(() => document.querySelector('[data-metric=total-products]').textContent === '0');
  assert.equal((await request(endpoint, afterRestart.token)).length, 4, 'histórico preservado após arquivar');
  assert.equal((await request('/api/insumos/resumo', afterRestart.token)).entradasNoMes, 1, 'entradas de produto arquivado permanecem no indicador');

  // API fora do ar: tabela e métricas não exibem catálogo mock nem zeros fictícios.
  await page.route('**/api/insumos', route => route.fulfill({status: 500, contentType: 'application/json', body: '{"error":"Falha simulada"}'}));
  await page.reload();
  await page.locator('[data-stock-retry]').waitFor();
  assert.equal(await page.locator('[data-stock-table] tbody tr').count(), 0);
  assert.equal(await page.locator('[data-metric=total-products]').textContent(), '—');
  assert.equal(await page.locator('[data-open-produto]').isDisabled(), true);
  assert.equal((await page.content()).includes('Glifosato 480 SL'), false);
  await page.unroute('**/api/insumos');
  await page.locator('[data-stock-retry]').click();
  await page.waitForFunction(() => document.querySelector('[data-stock-message]')?.textContent === 'Estoque atualizado.');
  assert.deepEqual(errors, []);
  await context.close();
  const anonymous = await browser.newContext();
  const loginPage = await anonymous.newPage(); await loginPage.goto(base + '/insumos.html');
  await loginPage.waitForURL('**/login.html'); await anonymous.close();
  console.log('OK estoque E2E Mongo: CRUD, entrada, saída, ajuste zero, atividade real, falhas, indicadores, reload, reinício e histórico preservado.');
})().catch(e => { console.error(e); process.exitCode = 1; }).finally(async () => {
  if (browser) await browser.close(); await stop();
  if (process.env.MONGODB_URI) {
    const cleanup = spawnSync('java', ['-cp', 'bin:lib/*', 'CenarioEstoqueTeste', '--limpar'], {
      cwd: path.join(__dirname, '../backend/servidor'), env: process.env, encoding: 'utf8',
    });
    if (cleanup.status !== 0) { console.error('Falha na limpeza dos fixtures:', cleanup.stderr); process.exitCode = 1; }
  }
});
