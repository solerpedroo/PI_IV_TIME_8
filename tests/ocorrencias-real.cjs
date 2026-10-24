/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
// Fluxo completo contra o servidor Java. --mongo inclui reinício do processo.
const assert = require('node:assert/strict');
const path = require('node:path');
const {spawn, spawnSync} = require('node:child_process');
const {once} = require('node:events');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const {launchTestBrowser} = require('./browser-launch.cjs');
const base = 'http://127.0.0.1:8080';
const cwd = path.join(__dirname, '../backend/servidor');
const mongo = process.argv.includes('--mongo');
const sep = process.platform === 'win32' ? ';' : ':';
const classpath = 'bin' + sep + 'lib/*';
const suffix = Date.now().toString(36) + '-' + process.pid;
const email = 'ocorrencias-e2e-' + suffix + '@agro.test';
const password = 'Senha@123';
let server, browser, log = '';

async function start() {
  server = spawn('java', ['-cp', classpath, 'Servidor', '8080', ...(mongo ? [] : ['--memoria'])], {
    cwd, env: {...process.env, SMTP_HOST: '', SMTP_USERNAME: '', SMTP_PASSWORD: '', SMTP_FROM: '', SMTP_PORT: ''},
  });
  server.stdout.on('data', c => { log += c; }); server.stderr.on('data', c => { log += c; });
  for (let i = 0; i < 80; i++) {
    try { if ((await fetch(base + '/health')).ok) return; } catch {}
    if (server.exitCode !== null) break;
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error('Servidor de teste não iniciou.');
}
async function stop() {
  if (!server || server.exitCode !== null) return;
  const ended = once(server, 'exit'); server.stdin.end('desativar\n'); await ended;
}
async function api(endpoint, method = 'GET', body, token, expected = 200) {
  const response = await fetch(base + endpoint, {method, headers: {'Content-Type': 'application/json', ...(token ? {Authorization: 'Bearer ' + token} : {})}, body: body ? JSON.stringify(body) : undefined});
  const text = await response.text();
  assert.equal(response.status, expected, endpoint + ': ' + text);
  return text ? JSON.parse(text) : null;
}
const closeDetail = page => page.locator('[data-modal="occ-detail"] [data-close-modal]').click();
const waitLoaded = page => page.waitForFunction(() => document.querySelector('[data-occ-message]').textContent.includes('contexto consultado'));
const waitIdle = page => page.waitForFunction(() => !document.querySelector('[data-open-modal="occ-form"]').disabled);
const openRow = async (page, id) => {
  // Links do Dashboard reabrem a ficha pelo ID também depois do reload.
  if (await page.locator('[data-modal="occ-detail"].is-open').isVisible()) await closeDetail(page);
  await page.locator('tr[data-id="' + id + '"] [data-row-action="detail"]').click();
  await page.locator('[data-modal="occ-detail"].is-open').waitFor();
};

(async () => {
  if (mongo && !/\/[a-zA-Z0-9_-]+_teste\?/.test(process.env.MONGODB_URI || '')) throw new Error('Use um MONGODB_URI dedicado terminado em _teste.');
  await assert.rejects(fetch(base + '/health'), 'a porta 8080 deve estar livre');
  await start();
  try {
    await api('/api/auth/register', 'POST', {name: 'Pedro Teste Ocorrências', email, phone: '(19) 99999-1234', password, confirm: password, farm: 'Fazenda de Teste', area: '50 ha', terms: true}, null, 201);
    const confirmation = log.match(/token: ([a-zA-Z0-9-]+)/); assert.ok(confirmation);
    await api('/api/auth/confirm-email', 'POST', {token: confirmation[1]});
    let session = await api('/api/auth/login', 'POST', {email, password});
    const plot = await api('/api/talhoes', 'POST', {nome: 'Norte Real', cultura: 'Soja', hectares: 12, safra: '2025/26'}, session.token, 201);
    const south = await api('/api/talhoes', 'POST', {nome: 'Sul Real', cultura: 'Milho', hectares: 8, safra: '2026/27'}, session.token, 201);
    browser = await launchTestBrowser(chromium);
    const context = await browser.newContext({viewport: {width: 1366, height: 900}, reducedMotion: 'reduce'});
    await context.addInitScript(s => {
      if (!sessionStorage.getItem('agro.sessionToken')) {
        sessionStorage.setItem('agro.sessionToken', s.token); sessionStorage.setItem('agro.sessionUser', JSON.stringify(s.usuario));
      }
    }, session);
    const page = await context.newPage(), errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.goto(base + '/ocorrencias.html'); await waitLoaded(page);
    assert.equal(await page.locator('[data-occ-table] tbody tr').count(), 0);
    assert.equal(await page.locator('[data-metric="resolvidas"]').textContent(), '0');
    // Cache legado é ignorado e preservado para eventual consulta do usuário.
    await page.evaluate(() => localStorage.setItem('agro.operational.v1.occurrences', 'legado-preservado'));
    await page.locator('[data-open-modal="occ-form"]').click();
    await page.locator('[data-occ-form]').evaluate(f => f.requestSubmit());
    await page.locator('[data-error-for="titulo"]').filter({hasText: 'Informe'}).waitFor();
    const description = 'Descrição única <img src=x onerror="window.__executou=true">\nSegunda linha.';
    await page.locator('[name=titulo]').fill('Lagarta <literal>');
    await page.locator('[name=tipo]').selectOption('Praga');
    await page.locator('[name=talhaoId]').selectOption(plot.id);
    await page.locator('[name=data]').fill('2026-01-20');
    await page.locator('[name=descricao]').fill(description);
    await page.locator('[data-priority="Alta"]').click();
    await page.locator('[data-occ-form]').evaluate(f => f.requestSubmit());
    await page.locator('[data-modal="occ-detail"].is-open').waitFor(); await waitIdle(page);
    let record = (await api('/api/ocorrencias', 'GET', null, session.token))[0]; const id = record.id;
    assert.equal(record.descricao, description); assert.equal(record.talhaoId, plot.id);
    assert.equal(await page.locator('[data-detail-description]').textContent(), description);
    assert.match(await page.locator('[data-detail-meta]').textContent(), /20\/01\/2026/);
    assert.equal(await page.locator('[data-detail-description] img').count(), 0);
    assert.equal(await page.evaluate(() => window.__executou), undefined);
    await closeDetail(page);
    await page.goto(base + '/dashboard.html');
    await page.waitForFunction(() => document.querySelector('[data-alerts-count]').textContent === '1');
    await page.locator('.alerts-list a[href="ocorrencias.html?ocorrenciaId=' + id + '"]').click();
    await page.locator('[data-modal="occ-detail"].is-open').waitFor(); await waitIdle(page);
    console.log('OK cadastro, validação, data, vínculo e descrição literal');

    await page.locator('[data-detail-edit]').click();
    const edited = description + '\nDescrição editada.';
    await page.locator('[name=descricao]').fill(edited);
    // Erro do servidor não pode produzir sucesso nem alterar o registro exibido.
    const failurePath = '**/api/ocorrencias/' + id;
    await page.route(failurePath, route => route.request().method() === 'PUT' ? route.fulfill({status: 500, contentType: 'application/json', body: '{"error":"Falha controlada"}'}) : route.continue());
    await page.locator('[data-occ-form]').evaluate(f => f.requestSubmit());
    await page.locator('[data-occ-form-error]').filter({hasText: 'Falha controlada'}).waitFor();
    assert.equal((await api('/api/ocorrencias/' + id, 'GET', null, session.token)).descricao, description);
    await page.unroute(failurePath);
    await page.locator('[data-occ-form]').evaluate(f => f.requestSubmit());
    await page.locator('[data-modal="occ-detail"].is-open').waitFor(); await waitIdle(page);
    assert.equal(await page.locator('[data-detail-description]').textContent(), edited);
    assert.match(await page.locator('[data-detail-timeline]').textContent(), /Segunda linha/);
    await page.locator('[data-update-text]').fill('Inspeção iniciada <texto>');
    await page.locator('[data-detail-follow]').click();
    await page.locator('[data-detail-publish]').waitFor({state: 'visible'}); await waitIdle(page);
    await page.locator('[data-update-text]').fill('Tratamento aplicado e monitorado.');
    await page.locator('[data-detail-publish]').click();
    await page.waitForFunction(() => document.querySelector('[data-detail-timeline]').textContent.includes('Tratamento aplicado')); await waitIdle(page);
    await page.locator('[data-detail-resolve]').click(); await page.locator('[data-confirm-resolve]').click();
    await page.locator('[data-resolve-error]').filter({hasText: 'Descreva'}).waitFor();
    await page.locator('[data-resolution-text]').fill('Problema controlado após a inspeção final.');
    await page.locator('[data-confirm-resolve]').click();
    await page.waitForFunction(() => document.querySelector('[data-detail-badges]').textContent.includes('Resolvida')); await waitIdle(page);
    const events = await api('/api/ocorrencias/' + id + '/timeline', 'GET', null, session.token);
    assert.equal(events.length, 5); assert.equal(events[0].descricao, description);
    assert.equal(events[1].dados.descricao, edited); assert.equal(events[4].acao, 'resolucao');
    record = await api('/api/ocorrencias/' + id, 'GET', null, session.token);
    assert.ok(record.resolvidaEm); assert.equal(record.talhaoId, plot.id);
    console.log('OK edição, falha de escrita, acompanhamento, publicação e resolução');

    await page.reload(); await waitLoaded(page); await openRow(page, id);
    assert.equal(await page.locator('[data-detail-description]').textContent(), edited);
    assert.match(await page.locator('[data-detail-timeline]').textContent(), /Tratamento aplicado/);
    assert.equal(await page.locator('[data-detail-follow]').isVisible(), false);
    await page.screenshot({path: '/tmp/agro-ocorrencias-verificado.png', fullPage: true});
    await closeDetail(page);
    for (const [width, height] of [[1024, 768], [1366, 900]]) {
      await page.setViewportSize({width, height});
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, 'ocorrências sem overflow da página');
    }
    const currentMonth = await page.locator('[data-occ-month]').inputValue();
    assert.equal(await page.locator('[data-metric="resolvidas"]').textContent(), '1');
    await page.locator('[data-occ-month]').fill('2020-01'); await waitLoaded(page);
    await page.waitForFunction(() => document.querySelector('[data-metric="resolvidas"]').textContent === '0');
    await page.locator('[data-occ-month]').fill(currentMonth);
    await page.waitForFunction(() => document.querySelector('[data-metric="resolvidas"]').textContent === '1');
    await page.locator('[data-occ-plot-filter]').selectOption(south.id);
    await page.waitForFunction(() => document.querySelector('[data-metric="resolvidas"]').textContent === '0');
    await page.locator('[data-occ-plot-filter]').selectOption(plot.id);
    await page.waitForFunction(() => document.querySelector('[data-metric="resolvidas"]').textContent === '1');
    await page.goto(base + '/ocorrencias.html?talhao=' + encodeURIComponent(plot.nome)); await waitLoaded(page);
    assert.equal(await page.locator('[data-occ-plot-filter]').inputValue(), plot.id);
    await page.goto(base + '/dashboard.html');
    await page.waitForFunction(() => document.querySelector('[data-alerts-count]').textContent === '0');
    await page.goto(base + '/ocorrencias.html'); await waitLoaded(page);
    console.log('OK recarga, histórico, mês real e contexto por talhão');

    const other = await api('/api/propriedades', 'POST', {nome: 'Outra propriedade', localizacao: 'Rodovia', municipio: 'Campinas', safraAtual: '2026/27', areaHectares: 20}, session.token, 201);
    await api('/api/propriedades/' + other.id + '/selecionar', 'POST', {}, session.token);
    await api('/api/ocorrencias/' + id, 'GET', null, session.token, 404);
    await page.locator('[data-open-modal="occ-form"]').click();
    await page.locator('[name=titulo]').fill('Contexto antigo'); await page.locator('[name=tipo]').selectOption('Outro');
    await page.locator('[data-occ-form]').evaluate(f => f.requestSubmit());
    await page.locator('[data-occ-form-error]').filter({hasText: 'propriedade ativa mudou'}).waitFor();
    await page.goto(base + '/ocorrencias.html'); await waitLoaded(page);
    assert.equal(await page.locator('[data-occ-table] tbody tr').count(), 0);
    await api('/api/propriedades/' + session.usuario.propriedadeId + '/selecionar', 'POST', {}, session.token);

    if (mongo) {
      await stop(); await start();
      await api('/api/ocorrencias', 'GET', null, session.token, 401);
      session = await api('/api/auth/login', 'POST', {email, password});
      await page.evaluate(s => { sessionStorage.setItem('agro.sessionToken', s.token); sessionStorage.setItem('agro.sessionUser', JSON.stringify(s.usuario)); }, session);
      const recovered = await api('/api/ocorrencias/' + id, 'GET', null, session.token);
      assert.equal(recovered.descricao, edited); assert.equal(recovered.resolvidaEm, record.resolvidaEm);
      assert.equal((await api('/api/ocorrencias/' + id + '/timeline', 'GET', null, session.token)).length, 5);
      console.log('OK reinício real do Java com dados Mongo preservados e novo login');
    }
    await page.goto(base + '/ocorrencias.html'); await waitLoaded(page); await openRow(page, id);
    await page.locator('[data-detail-delete]').click(); await page.locator('[data-confirm-delete]').click();
    await waitLoaded(page); await waitIdle(page);
    assert.equal(await page.locator('[data-occ-table] tbody tr').count(), 0);
    await page.locator('[data-occ-archives]').check();
    await page.locator('tr[data-id="' + id + '"]').waitFor(); await openRow(page, id);
    assert.match(await page.locator('[data-detail-timeline]').textContent(), /Problema controlado/);
    assert.equal((await api('/api/ocorrencias/' + id + '/timeline', 'GET', null, session.token)).length, 6);
    await closeDetail(page);
    // GET indisponível mostra falha e métricas sem valor, nunca restaura exemplos.
    await page.route('**/api/ocorrencias?*', route => route.fulfill({status: 500, contentType: 'application/json', body: '{"error":"Indisponível"}'}));
    await page.locator('[data-occ-search]').fill('falha');
    await page.locator('[data-occ-retry]').waitFor({state: 'visible'});
    assert.equal(await page.locator('[data-occ-table] tbody tr').count(), 0);
    assert.equal(await page.locator('[data-metric="abertas"]').textContent(), '—');
    await page.unroute('**/api/ocorrencias?*'); await page.locator('[data-occ-search]').fill('');
    await page.locator('[data-occ-retry]').click(); await waitLoaded(page);
    assert.equal(await page.evaluate(() => localStorage.getItem('agro.operational.v1.occurrences')), 'legado-preservado');
    assert.deepEqual(errors, []);
    const anonymous = await browser.newContext(), guest = await anonymous.newPage();
    await guest.goto(base + '/ocorrencias.html'); await guest.waitForURL('**/login.html'); await anonymous.close();
    console.log('OK exclusão com histórico, isolamento, falhas de API e proteção de sessão');
    console.log('Ocorrências E2E: OK (' + (mongo ? 'Mongo e reinício' : 'memória') + ')');
  } finally {
    if (browser) await browser.close(); await stop();
    if (mongo) {
      const cleanup = spawnSync('java', ['-cp', classpath, 'TesteOcorrencias', '--limpar-e2e', email], {cwd, env: process.env, encoding: 'utf8'});
      if (cleanup.status !== 0) throw new Error('Não foi possível limpar a conta fictícia da suíte.');
    }
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
