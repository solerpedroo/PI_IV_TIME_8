/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
// Formulários reais → HTTP Java → repositório → reload/novo login.
const assert = require('node:assert/strict');
const path = require('node:path');
const {spawn, spawnSync} = require('node:child_process');
const {once} = require('node:events');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const {launchTestBrowser} = require('./browser-launch.cjs');
const base = 'http://127.0.0.1:8080';
const mongo = process.env.PROFILE_MONGO === '1';
let server, browser, logs = '';
const ids = [];

async function start() {
  server = spawn('java', ['-cp', 'bin:lib/*', 'Servidor', '8080', ...(mongo ? [] : ['--memoria'])], {
    cwd: path.join(__dirname, '../backend/servidor'),
    env: {...process.env, SMTP_HOST: '', SMTP_USERNAME: '', SMTP_PASSWORD: '', SMTP_FROM: '', SMTP_PORT: ''},
  });
  server.stdout.on('data', chunk => { logs += chunk.toString(); });
  server.stderr.on('data', chunk => { logs += chunk.toString(); });
  for (let i = 0; i < 80; i++) {
    try { if ((await fetch(base + '/health')).ok) return; } catch {}
    if (server.exitCode !== null) throw new Error('Servidor de teste encerrou antes de ficar pronto.');
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error('Servidor não iniciou.');
}
async function stop() {
  if (server?.exitCode === null) { const done = once(server, 'exit'); server.stdin.write('desativar\n'); await done; }
}
async function api(path, token, method = 'GET', data, expected = 200) {
  const response = await fetch(base + path, {method, headers: {'Content-Type': 'application/json',
    ...(token ? {Authorization: 'Bearer ' + token} : {})}, body: data === undefined ? undefined : JSON.stringify(data)});
  assert.equal(response.status, expected, path);
  return response.status === 204 ? null : response.json();
}
const login = (email, password = 'Senha@123') => api('/api/auth/login', null, 'POST', {email, password});
async function register(email) {
  const registered = await api('/api/auth/register', null, 'POST', {name: 'Perfil E2E', email,
    phone: '(19) 99999-1234', password: 'Senha@123', confirm: 'Senha@123', farm: 'Fazenda Perfil E2E', area: '10 ha', terms: true}, 201);
  ids.push(registered.usuario.id);
  const code = [...logs.matchAll(/token: ([a-zA-Z0-9-]+)/g)].at(-1)?.[1];
  assert.ok(code, 'código local de confirmação deve existir');
  await api('/api/auth/confirm-email', null, 'POST', {token: code});
  return login(email);
}
async function contextFor(session) {
  const context = await browser.newContext({viewport: {width: 1440, height: 1000}, reducedMotion: 'reduce'});
  await context.addInitScript(session => {
    if (!sessionStorage.getItem('perfil.fixture.iniciada')) {
      sessionStorage.setItem('perfil.fixture.iniciada', 'true');
      sessionStorage.setItem('agro.sessionToken', session.token);
      sessionStorage.setItem('agro.sessionUser', JSON.stringify(session.usuario));
    }
  }, session);
  return context;
}
async function loaded(page) {
  await page.waitForFunction(() => document.querySelector('[data-profile-message]')?.textContent === 'Perfil carregado do servidor.');
}
async function submit(page, selector, endpoint, method = 'PUT', expected = 200) {
  const result = page.waitForResponse(r => r.url().endsWith(endpoint) && r.request().method() === method);
  await page.locator(selector).evaluate(form => form.requestSubmit());
  assert.equal((await result).status(), expected, endpoint);
  if (expected === 200 && !endpoint.endsWith('senha') && !endpoint.endsWith('confirmar')) {
    await page.waitForFunction(selector => document.querySelector(selector)?.getAttribute('aria-busy') === 'false', selector);
  }
}

(async () => {
  if (mongo) assert.match(process.env.MONGODB_URI || '', /_teste(?:\?|$)/, 'use banco de teste dedicado');
  await assert.rejects(fetch(base + '/health'), 'porta 8080 deve estar livre');
  await start();
  const email = `perfil-e2e-${Date.now()}@agro.test`, nextEmail = email.replace('@', '-novo@');
  let session = await register(email);
  const foreign = await register(email.replace('@', '-outro@'));
  browser = await launchTestBrowser(chromium);
  let context = await contextFor(session), page = await context.newPage();
  const errors = []; page.on('pageerror', e => errors.push(e.message));
  await page.goto(base + '/configuracoes.html'); await loaded(page);
  assert.equal(await page.locator('#profile-email').inputValue(), email);
  const profile = page.locator('#profile-form');
  await profile.locator('[name=name]').fill('Perfil E2E Alterado');
  await profile.locator('[name=phone]').fill('(11) 98888-1234');
  await submit(page, '#profile-form', '/api/perfil');
  await page.reload(); await loaded(page);
  assert.equal(await page.locator('#profile-name').inputValue(), 'Perfil E2E Alterado');
  assert.equal(await page.locator('#profile-phone').inputValue(), '(11) 98888-1234');
  await page.screenshot({path: '/tmp/agro-perfil-verificado.png', fullPage: true});
  assert.equal(await page.evaluate(() => localStorage.getItem('agro.operational.v1.' + JSON.parse(sessionStorage.getItem('agro.sessionUser')).id + '.' + JSON.parse(sessionStorage.getItem('agro.sessionUser')).propriedadeId + '.settings')), null);

  // Uma falha de escrita não anuncia sucesso nem altera o registro real.
  await page.route('**/api/perfil', route => route.request().method() === 'PUT'
    ? route.fulfill({status: 500, contentType: 'application/json', body: '{"error":"Falha de teste"}'}) : route.continue());
  await page.locator('#profile-name').fill('Não deve salvar');
  await submit(page, '#profile-form', '/api/perfil', 'PUT', 500);
  assert.equal((await api('/api/perfil', session.token)).usuario.name, 'Perfil E2E Alterado');
  await page.unroute('**/api/perfil'); await page.reload(); await loaded(page);
  await page.locator('[data-settings-tab=notifications]').filter({hasText: 'Notificações'}).click();
  await page.locator('[data-preference=stock]').uncheck();
  await page.locator('[data-preference=reports]').check();
  const prefsResponse = page.waitForResponse(r => r.url().endsWith('/api/perfil/preferencias') && r.request().method() === 'PUT');
  await page.locator('[data-save-notifications]').click(); assert.equal((await prefsResponse).status(), 200);
  await page.reload(); await loaded(page);
  assert.equal(await page.locator('[data-preference=stock]').isChecked(), false);
  assert.equal(await page.locator('[data-preference=reports]').isChecked(), true);

  const property = await api('/api/propriedades', session.token, 'POST', {nome: 'Fazenda Padrão E2E', localizacao: 'Local de teste', municipio: 'Campinas', areaHectares: 20, safraAtual: '2026/27'}, 201);
  await page.reload(); await loaded(page);
  await page.locator('button[data-settings-tab=property]').click();
  await page.locator('#default-property option[value="' + property.id + '"]').waitFor({state: 'attached'});
  await page.locator('#default-property').selectOption(property.id);
  await submit(page, '[data-default-property-form]', '/api/perfil/propriedade-padrao');
  assert.equal((await api('/api/me', session.token)).usuario.propriedadeId, session.usuario.propriedadeId);
  await api('/api/perfil/propriedade-padrao', session.token, 'PUT', {propriedadeId: foreign.usuario.propriedadeId}, 404);
  if (mongo) { await context.close(); await stop(); await start(); }
  else await context.close();
  session = await login(email);
  assert.equal(session.usuario.propriedadeId, property.id);
  context = await contextFor(session); page = await context.newPage(); page.on('pageerror', e => errors.push(e.message));
  await page.goto(base + '/configuracoes.html'); await loaded(page);
  assert.equal(await page.locator('[data-preference=stock]').isChecked(), false);
  assert.equal(await page.locator('#default-property').inputValue(), property.id);

  // Troca de e-mail usa senha atual e código real do fluxo local de e-mail.
  await page.locator('#profile-email').fill(nextEmail);
  await page.locator('#profile-current').fill('Senha@123');
  await submit(page, '#profile-form', '/api/perfil');
  await page.locator('[data-email-pending]').waitFor();
  assert.equal((await api('/api/perfil', session.token)).usuario.email, email);
  const code = [...logs.matchAll(/token: ([a-zA-Z0-9-]+)/g)].at(-1)[1];
  await page.locator('[name=codigo]').fill(code);
  await submit(page, '[data-email-confirm-form]', '/api/perfil/email/confirmar', 'POST');
  await page.waitForURL('**/login.html');
  await api('/api/perfil', session.token, 'GET', undefined, 401);
  await context.close(); session = await login(nextEmail);
  context = await contextFor(session); page = await context.newPage(); page.on('pageerror', e => errors.push(e.message));
  await page.goto(base + '/configuracoes.html#settings-security'); await loaded(page);
  const security = page.locator('[data-security-form]');
  for (const [name, value] of Object.entries({current: 'errada', new: 'Nova@1234', confirm: 'Nova@1234'})) await security.locator(`[name=${name}]`).fill(value);
  await submit(page, '[data-security-form]', '/api/perfil/senha', 'PUT', 400);
  assert.match(await security.locator('[name=current]').locator('..').locator('.field-error').textContent(), /atual/);
  await security.locator('[name=current]').fill('Senha@123');
  await submit(page, '[data-security-form]', '/api/perfil/senha'); await page.waitForURL('**/login.html');
  await api('/api/me', session.token, 'GET', undefined, 401);
  await context.close(); session = await login(nextEmail, 'Nova@1234');
  context = await contextFor(session); page = await context.newPage(); page.on('pageerror', e => errors.push(e.message));
  await page.goto(base + '/configuracoes.html'); await loaded(page);
  await page.route('**/api/perfil', route => route.fulfill({status: 500, contentType: 'application/json', body: '{"error":"Falha de consulta"}'}));
  await page.reload(); await page.locator('[data-profile-retry]').waitFor();
  assert.equal(await page.locator('#profile-name').inputValue(), '');
  assert.equal(await page.locator('button[form=profile-form]').isDisabled(), true);
  await page.unroute('**/api/perfil'); await page.locator('[data-profile-retry]').click(); await loaded(page);
  assert.equal(await page.evaluate(() => [...document.querySelectorAll('input[type=password]')].some(el => el.value)), false);

  // Logout real: o token perde acesso à API, não apenas ao navegador.
  await page.locator('[data-open-logout]').evaluate(el => el.click());
  await page.locator('[data-confirm-logout]').click(); await page.waitForURL('**/login.html');
  await api('/api/me', session.token, 'GET', undefined, 401);
  assert.equal(await page.evaluate(() => sessionStorage.getItem('agro.sessionToken')), null);
  assert.deepEqual(errors, []);
  await context.close();
  // Rede/HTTP indisponível no logout: saída local e aviso de revogação não confirmada.
  session = await login(nextEmail, 'Nova@1234');
  context = await contextFor(session); page = await context.newPage();
  await page.goto(base + '/configuracoes.html'); await loaded(page);
  await page.route('**/api/auth/logout', route => route.fulfill({status: 500, contentType: 'application/json', body: '{"error":"Falha de teste"}'}));
  await page.locator('[data-open-logout]').evaluate(el => el.click());
  await page.locator('[data-confirm-logout]').click(); await page.waitForURL('**/login.html');
  assert.equal(await page.evaluate(() => sessionStorage.getItem('agro.sessionToken')), null);
  assert.match(await page.locator('body').textContent(), /Não foi possível confirmar/);
  await api('/api/me', session.token); // Não finge que a revogação remota ocorreu.
  await api('/api/auth/logout', session.token, 'POST', undefined, 204);
  await context.close();
  console.log('OK perfil E2E: dados, preferências, padrão, falhas, confirmação de e-mail, senha e logout' + (mongo ? ' com MongoDB/reinício.' : ' com servidor em memória.'));
})().catch(e => { console.error(e); process.exitCode = 1; }).finally(async () => {
  if (browser) await browser.close(); await stop();
  if (mongo && ids.length) {
    const cleanup = spawnSync('java', ['-cp', 'bin:lib/*', 'TestePerfil', '--limpar-e2e', ...ids], {cwd: path.join(__dirname, '../backend/servidor'), env: process.env, encoding: 'utf8'});
    if (cleanup.status !== 0) { console.error('Falha na limpeza dos fixtures de perfil.'); process.exitCode = 1; }
  }
});
