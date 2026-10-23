const assert = require('node:assert/strict');
const path = require('node:path');
const {spawn} = require('node:child_process');
const {once} = require('node:events');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const {launchTestBrowser} = require('./browser-launch.cjs');
const base = 'http://127.0.0.1:8080';

(async () => {
  // Não usa serviços externos nem dados existentes: servidor próprio em memória.
  await assert.rejects(fetch(base + '/health'), 'porta 8080 deve estar livre para o teste');
  const server = spawn('java', ['-cp', 'bin:lib/*', 'Servidor', '8080', '--memoria'], {
    cwd: path.join(__dirname, '../backend/servidor'),
    env: {...process.env, SMTP_HOST: '', SMTP_USERNAME: '', SMTP_PASSWORD: '', SMTP_FROM: '', SMTP_PORT: ''},
  });
  let log = '';
  server.stdout.on('data', chunk => { log += chunk.toString(); });
  server.stderr.on('data', chunk => { log += chunk.toString(); });
  let browser;
  try {
    for (let attempt = 0; ; attempt++) {
      try { if ((await fetch(base + '/health')).ok) break; } catch {}
      if (attempt === 50 || server.exitCode !== null) throw new Error('Servidor de teste não iniciou');
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    const api = async (endpoint, body, token) => {
      const response = await fetch(base + endpoint, {method: body ? 'POST' : 'GET',
        headers: {'Content-Type': 'application/json', ...(token ? {Authorization: 'Bearer ' + token} : {})},
        body: body ? JSON.stringify(body) : undefined});
      assert.ok(response.ok, endpoint + ': HTTP ' + response.status);
      return response.json();
    };
    await api('/api/auth/register', {name: 'Teste Real', email: 'real@agro.test', phone: '(19) 99999-1234',
      password: 'Senha@123', confirm: 'Senha@123', farm: 'Fazenda Real', area: '50 ha', terms: true});
    const confirmation = log.match(/token: ([a-zA-Z0-9-]+)/);
    assert.ok(confirmation, 'modo local deve entregar código no console');
    await api('/api/auth/confirm-email', {token: confirmation[1]});
    const session = await api('/api/auth/login', {email: 'real@agro.test', password: 'Senha@123'});
    browser = await launchTestBrowser(chromium);
    const context = await browser.newContext();
    await context.addInitScript(session => {
      if (!sessionStorage.getItem('agro.sessionToken')) {
        sessionStorage.setItem('agro.sessionToken', session.token);
        sessionStorage.setItem('agro.sessionUser', JSON.stringify(session.usuario));
      }
    }, session);
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await Promise.all([
      page.waitForResponse(response => response.url().endsWith('/api/talhoes')),
      page.goto(base + '/areas-cultivo.html'),
    ]);
    await page.locator('[data-open-modal="talhao-form"]').click();
    await page.locator('[name=nome]').fill('Talhão Real');
    await page.locator('[name=cultura]').selectOption('Soja');
    await page.locator('[name=hectares]').fill('12');
    await page.locator('[data-talhao-form]').evaluate(form => form.requestSubmit());
    await page.locator('[data-plots-table] tr[data-plot="Talhão Real"]').waitFor();
    const plot = (await api('/api/talhoes', null, session.token))[0];
    await page.goto(base + '/dashboard.html');
    await page.waitForFunction(() => document.querySelector('[data-metric=areas]').textContent === '1');
    assert.equal(await page.locator('[name=activity-plot] option[value="Talhão Real"]').count(), 1);
    await page.goto(base + '/atividades.html');
    await page.waitForFunction(() => document.querySelector('[name=talhao] option[value="Talhão Real"]'));
    // O dropdown visual também deve receber a opção carregada depois do DOM.
    assert.equal(await page.locator('.select-option[data-value="Talhão Real"]').count(), 1);
    await page.locator('[data-open-modal="activity-form"]').first().click();
    await page.locator('[name=nome]').fill('Plantio Real');
    await page.locator('[name=tipo]').selectOption('Manutenção');
    await page.locator('[name=talhao]').selectOption('Talhão Real');
    await page.locator('[name=data]').fill('23 out');
    await page.evaluate(() => {
      window.__setItem = Storage.prototype.setItem;
      Storage.prototype.setItem = function(key, value) {
        if (key.startsWith('agro.operational.v1.')) throw new DOMException('Quota', 'QuotaExceededError');
        return window.__setItem.call(this, key, value);
      };
    });
    await page.locator('[data-activity-form]').evaluate(form => form.requestSubmit());
    assert.equal((await api('/api/talhoes/' + plot.id + '/historico', null, session.token)).length, 0);
    await page.evaluate(() => { Storage.prototype.setItem = window.__setItem; });
    const historyResponse = page.waitForResponse(response => response.url().endsWith('/historico') && response.request().method() === 'POST');
    await page.locator('[data-activity-form]').evaluate(form => form.requestSubmit());
    assert.equal((await historyResponse).status(), 201);
    assert.equal((await api('/api/talhoes/' + plot.id + '/historico', null, session.token)).length, 1);
    const properties = await fetch(base + '/api/propriedades', {headers: {Authorization: 'Bearer ' + session.token}});
    // O mesmo teste continua útil nas branches anteriores ao CRUD de propriedades.
    if (properties.ok) {
      const originalId = session.usuario.propriedadeId;
      await page.evaluate(() => window.AgroLocal.save('notifications', ['Somente propriedade original']));
      await page.goto(base + '/configuracoes.html#settings-property');
      await page.locator('.property-context-select option').first().waitFor({state: 'attached'});
      await page.locator('[data-new-property]').click();
      await page.locator('[name=farm]').fill('Fazenda Segunda');
      await page.locator('[name=area]').fill('25');
      await page.locator('[name=location]').fill('Rodovia 1');
      await page.locator('[name=city]').fill('Campinas');
      await page.locator('[name=harvest]').selectOption('2026/27');
      const createdResponse = page.waitForResponse(response => response.url().endsWith('/api/propriedades') && response.request().method() === 'POST');
      await page.locator('#property-form').evaluate(form => form.requestSubmit());
      const newProperty = await (await createdResponse).json();
      await page.locator('.property-context-select option[value="' + newProperty.id + '"]').waitFor({state: 'attached'});
      await page.locator('.property-context-select').selectOption(newProperty.id);
      await page.waitForFunction(id => JSON.parse(sessionStorage.getItem('agro.sessionUser')).propriedadeId === id
        && document.querySelector('[name=farm]')?.value === 'Fazenda Segunda', newProperty.id);
      const me = await api('/api/me', null, session.token);
      assert.equal(me.usuario.propriedadeId, newProperty.id);
      assert.equal(me.propriedade.farm, 'Fazenda Segunda');
      assert.equal(await page.evaluate(() => window.AgroLocal.read('notifications', []).includes('Somente propriedade original')), false);
      await Promise.all([
        page.waitForResponse(response => response.url().endsWith('/api/talhoes')),
        page.goto(base + '/areas-cultivo.html'),
      ]);
      assert.equal((await api('/api/talhoes', null, session.token)).length, 0);
      await page.goto(base + '/configuracoes.html#settings-property');
      await page.locator('.property-context-select option[value="' + originalId + '"]').waitFor({state: 'attached'});
      await page.locator('.property-context-select').selectOption(originalId);
      await page.waitForFunction(id => JSON.parse(sessionStorage.getItem('agro.sessionUser')).propriedadeId === id
        && document.querySelector('[name=farm]')?.value === 'Fazenda Real', originalId);
      assert.equal(await page.evaluate(() => window.AgroLocal.read('notifications', [])[0]), 'Somente propriedade original');
      assert.equal((await api('/api/talhoes', null, session.token))[0].nome, 'Talhão Real');
      console.log('OK propriedades reais: cadastro pela UI, seleção, me e isolamento de cache entre contextos');
    }
    assert.deepEqual(errors, []);
    console.log('OK API Java real: cadastro, confirmação, login, criação, painel, dropdown e histórico somente após salvar');
  } finally {
    if (browser) await browser.close();
    if (server.exitCode === null) { server.stdin.write('desativar\n'); await once(server, 'exit'); }
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
