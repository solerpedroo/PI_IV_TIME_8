/* Miguel — RA: 25003110 */
/* Fluxos operacionais no Edge, com arquivos servidos pela rota local do teste. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const { launchTestBrowser } = require('./browser-launch.cjs');

const root = path.resolve(__dirname, '..');
const origin = 'http://agro.local/';
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.woff2': 'font/woff2' };

async function run(name, callback) {
  const context = await browser.newContext();
  await context.route('**/*', async (route) => {
    const url = new URL(route.request().url());
    if (url.origin !== origin.slice(0, -1)) return route.abort();
    const target = path.resolve(root, '.' + decodeURIComponent(url.pathname));
    if (!target.startsWith(root + path.sep) || !fs.existsSync(target) || !fs.statSync(target).isFile()) return route.fulfill({ status: 404, body: '' });
    return route.fulfill({ status: 200, contentType: mime[path.extname(target)] || 'application/octet-stream', body: fs.readFileSync(target) });
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  try {
    await callback(page, context);
    assert.deepEqual(errors, [], 'erro JavaScript na página');
    console.log('OK', name);
  } catch (error) {
    console.error('FALHOU', name, error.stack || error);
    failures++;
  } finally { await context.close(); }
}

async function go(page, file) {
  await page.goto(origin + file);
  await page.waitForLoadState('load');
}
async function submit(page, selector, values) {
  await page.evaluate(({ selector, values }) => {
    const form = document.querySelector(selector);
    for (const [name, value] of Object.entries(values)) {
      const control = form.elements[name];
      control.value = value;
      control.dispatchEvent(new Event('change', { bubbles: true }));
    }
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  }, { selector, values });
}
async function data(page, key) {
  return page.evaluate((key) => JSON.parse(localStorage.getItem('agro.operational.v1.' + key))?.data, key);
}
async function clickCardAction(page, name, action) {
  await page.evaluate(({ name, action }) => {
    const card = [...document.querySelectorAll('.kanban-card')].find((item) => item.dataset.activity === name);
    assertCard(card, name);
    card.querySelector(`[data-card-action="${action}"]`).click();
    function assertCard(value, label) { if (!value) throw new Error('Card ausente: ' + label); }
  }, { name, action });
}

let browser;
let failures = 0;
(async () => {
  browser = await launchTestBrowser(chromium);

  await run('áreas: criação, edição, filtro e recarga offline', async (page, context) => {
    await go(page, 'areas-cultivo.html');
    await page.locator('[data-open-modal="talhao-form"]').click();
    await submit(page, '[data-talhao-form]', { nome: 'Talhão Teste', cultura: 'Soja', hectares: '20', status: 'Ativo' });
    assert.equal((await data(page, 'plots')).at(-1).plot, 'Talhão Teste');
    await page.locator('[data-plots-search]').fill('Talhão Teste');
    assert.equal(await page.locator('[data-plots-table] tbody tr:visible').count(), 1);
    await page.evaluate(() => [...document.querySelectorAll('[data-plots-table] tbody tr')].find((row) => row.dataset.plot === 'Talhão Teste').querySelector('[data-edit-plot]').click());
    await submit(page, '[data-talhao-form]', { nome: 'Talhão Revisado', cultura: 'Milho', hectares: '25', status: 'Em preparo' });
    assert.equal((await data(page, 'plots')).at(-1).status, 'Em preparo');
    await context.setOffline(true);
    await page.reload();
    assert.equal(await page.locator('[data-plots-table] tbody tr[data-plot="Talhão Revisado"]').count(), 1);
    assert.equal(await page.locator('[data-metric="hectares"]').textContent(), '585 ha');
  });

  await run('atividades: criar, editar, lista, arraste, concluir, duplicar e excluir', async (page) => {
    await go(page, 'atividades.html');
    await page.locator('[data-open-modal="activity-form"]').first().click();
    await submit(page, '[data-activity-form]', { nome: 'Teste campo', tipo: 'Manutenção', talhao: 'Talhão A3', data: '14 out' });
    await clickCardAction(page, 'Teste campo', 'edit');
    await submit(page, '[data-activity-form]', { nome: 'Teste editado', tipo: 'Manutenção', talhao: 'Talhão A3', data: '15 out' });
    await page.reload();
    assert.equal((await data(page, 'activities')).some((item) => item.activity === 'Teste editado'), true);
    await page.locator('[data-view-option="lista"]').count().then(async (count) => {
      if (count) await page.locator('[data-view-option="lista"]').click();
      else await page.locator('[data-view-option="list"]').click();
    });
    await page.evaluate(() => {
      const row = [...document.querySelectorAll('[data-list-body] tr')].find((item) => item.cells[0].textContent === 'Teste editado');
      const select = row.querySelector('select'); select.value = 'Agendada'; select.dispatchEvent(new Event('change', { bubbles: true }));
    });
    assert.equal((await data(page, 'activities')).find((item) => item.activity === 'Teste editado').status, 'Agendada');
    await page.evaluate(() => {
      const card = [...document.querySelectorAll('.kanban-card')].find((item) => item.dataset.activity === 'Teste editado');
      const column = document.querySelector('[data-column="Em andamento"]');
      const dataTransfer = new DataTransfer();
      card.dispatchEvent(new DragEvent('dragstart', { bubbles: true, dataTransfer }));
      column.dispatchEvent(new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer }));
      card.dispatchEvent(new DragEvent('dragend', { bubbles: true, dataTransfer }));
    });
    assert.equal((await data(page, 'activities')).find((item) => item.activity === 'Teste editado').status, 'Em andamento');
    await clickCardAction(page, 'Teste editado', 'complete');
    await page.locator('[data-confirm-complete]').click();
    assert.equal((await data(page, 'activities')).find((item) => item.activity === 'Teste editado').status, 'Concluída');
    await clickCardAction(page, 'Teste editado', 'duplicate');
    await submit(page, '[data-activity-form]', { nome: 'Teste cópia', tipo: 'Manutenção', talhao: 'Talhão A3', data: '16 out' });
    await clickCardAction(page, 'Teste cópia', 'delete');
    await page.locator('[data-confirm-delete]').click();
    await page.reload();
    assert.equal((await data(page, 'activities')).some((item) => item.activity === 'Teste cópia'), false);
    assert.equal((await data(page, 'activities')).find((item) => item.activity === 'Teste editado').status, 'Concluída');
  });

  await run('custos: orçamento, período, lançamento, edição, exclusão e métricas', async (page) => {
    await go(page, 'custos.html');
    await page.locator('[data-budget-edit]').click();
    await submit(page, '[data-budget-form]', { budget: '100000' });
    assert.equal((await data(page, 'costs')).budget, 100000);
    await page.locator('[data-open-period-modal]').click();
    await page.locator('[data-period-option="Safra 2025/26"]').click();
    await page.locator('[data-confirm-period]').click();
    assert.equal((await data(page, 'costs')).period, 'Safra 2025/26');
    await page.locator('[data-open-cost-modal]').click();
    await submit(page, '[data-cost-form]', { description: 'Custo teste', category: 'Insumos', plot: 'T-01 Norte', date: '2026-10-14', value: '50' });
    const created = (await data(page, 'costs')).entries.find((entry) => entry.description === 'Custo teste');
    assert.ok(created);
    await page.evaluate((id) => document.querySelector(`[data-edit-cost="${id}"]`).click(), created.id);
    await submit(page, '[data-cost-form]', { description: 'Custo revisado', category: 'Insumos', plot: 'T-01 Norte', date: '2026-10-14', value: '80' });
    await page.reload();
    assert.equal((await data(page, 'costs')).entries.find((entry) => entry.id === created.id).value, 80);
    assert.match(await page.locator('[data-budget-spent]').textContent(), /20.270,00/);
    await page.evaluate((id) => document.querySelector(`[data-delete-cost="${id}"]`).click(), created.id);
    await page.reload();
    assert.equal((await data(page, 'costs')).entries.some((entry) => entry.id === created.id), false);
  });

  await run('configurações: perfil, propriedade, preferências e safra sem credenciais', async (page) => {
    await go(page, 'configuracoes.html');
    await submit(page, '[data-settings-form="profile"]', { name: 'Maria Teste', email: 'maria@example.test', phone: '1234' });
    await submit(page, '[data-settings-form="property"]', { farm: 'Fazenda Teste', area: '1500', city: 'Campinas', harvest: '2025/26' });
    await page.locator('[data-settings-tab="notifications"]').filter({ hasText: 'Notificações' }).click();
    await page.locator('[data-preference="weather"]').uncheck({ force: true });
    await page.locator('[data-save-notifications]').click();
    await page.locator('button[data-settings-tab="property"]').click();
    await page.locator('[data-open-harvest-modal]').click();
    await page.locator('[data-harvest-option="2026/27"]').click();
    await page.locator('[data-confirm-harvest]').click();
    await page.reload();
    assert.equal(await page.locator('[name="name"]').inputValue(), 'Maria Teste');
    assert.equal(await page.locator('[name="area"]').inputValue(), '1500');
    assert.equal(await page.locator('[data-preference="weather"]').isChecked(), false);
    assert.equal((await data(page, 'settings')).property.harvest, '2026/27');
    assert.doesNotMatch(JSON.stringify(await data(page, 'settings')), /password|current|token/i);
  });

  await run('dashboard: criar, concluir e compartilhar atividade', async (page) => {
    await go(page, 'dashboard.html');
    await page.locator('[data-open-new-activity]').click();
    await submit(page, '[data-new-activity-form]', { 'activity-name': 'Atividade painel', 'activity-plot': 'T-01 Norte' });
    assert.equal((await data(page, 'activities')).find((item) => item.activity === 'Atividade painel').talhao, 'T-01 Norte');
    await page.reload();
    await page.evaluate(() => [...document.querySelectorAll('[data-activities-table] tr')].find((row) => row.textContent.includes('Atividade painel')).querySelector('[data-complete-activity]').click());
    await go(page, 'atividades.html');
    assert.equal((await data(page, 'activities')).find((item) => item.activity === 'Atividade painel').status, 'Concluída');
    assert.equal(await page.locator('.kanban-card[data-activity="Atividade painel"]').count(), 1);
  });

  await run('dashboard continua utilizável com custos locais inválidos', async (page) => {
    await page.addInitScript(() => localStorage.setItem('agro.operational.v1.costs', '{invalido'));
    await go(page, 'dashboard.html');
    assert.equal(await page.locator('[data-metric="cost"]').textContent(), '—');
    assert.ok(Number(await page.locator('[data-metric="pending"]').textContent()) >= 0);
  });

  await run('notificações: lidas após recarga', async (page) => {
    await go(page, 'dashboard.html');
    await page.locator('[data-notif-mark-all]').evaluate((element) => element.click());
    assert.equal(await page.locator('.notif-item.is-unread').count(), 0);
    await page.reload();
    assert.equal(await page.locator('.notif-item.is-unread').count(), 0);
  });

  await browser.close();
  if (failures) process.exitCode = 1;
})().catch((error) => { console.error(error); process.exitCode = 1; browser?.close(); });
