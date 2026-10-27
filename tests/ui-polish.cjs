/* Miguel — RA: 25003110 */
/* Regressões de layout e interações reais, sem depender do backend.
 * Usa o Chromium do Playwright. BROWSER_PATH aponta outro executável, como o Edge. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const root = path.resolve(__dirname, '..');
const origin = 'http://agro.local';
const screens = ['dashboard', 'areas-cultivo', 'atividades', 'insumos', 'ocorrencias', 'custos', 'configuracoes', 'login', 'criar-conta', 'recuperar-senha', 'redefinir-senha', 'confirmar-email'];
const sizes = [[1024, 768], [1100, 700], [1200, 768], [1280, 720], [1366, 768], [1440, 900], [1920, 1080]];
const mime = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'application/javascript; charset=utf-8', '.jpg': 'image/jpeg', '.png': 'image/png', '.svg': 'image/svg+xml' };
const shots = process.env.UI_SCREENSHOTS;
let browser;

async function screenshot(page, name) {
  if (!shots) return;
  fs.mkdirSync(shots, { recursive: true });
  await page.screenshot({ path: path.join(shots, name + '.png') });
}

async function waitForMotion(page) {
  await page.evaluate(() => Promise.all([...document.getAnimations()]
    .filter((animation) => animation.playState === 'running' && animation.effect?.getTiming?.().iterations !== Infinity)
    .map((animation) => animation.finished.catch(() => {}))));
}

async function go(page, screen) {
  await page.goto(`${origin}/${screen}.html`, { waitUntil: 'load' });
  await waitForMotion(page);
}

async function reachable(locator, scroll = true) {
  if (scroll) await locator.scrollIntoViewIfNeeded();
  assert.equal(await locator.evaluate((element) => {
    const rect = element.getBoundingClientRect();
    const hit = document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2);
    return element === hit || element.contains(hit);
  }), true, 'controle cortado ou encoberto');
}

async function menu(page, trigger) {
  // As notificações são dispensáveis; fecha pelo botão real antes de usar o menu.
  const notices = page.locator('.toast:not(.is-leaving) .toast-close');
  while (await notices.count()) await notices.first().click();
  await waitForMotion(page);
  await trigger.click();
  const id = await trigger.getAttribute('aria-controls');
  const panel = page.locator(`[id="${id}"]`);
  await panel.waitFor({ state: 'visible' });
  await panel.evaluate((element) => Promise.all([...element.getAnimations()]
    .filter((animation) => animation.playState === 'running' && animation.effect?.getTiming?.().iterations !== Infinity)
    .map((animation) => animation.finished.catch(() => {}))));
  const items = panel.locator('[role="menuitem"]');
  await reachable(await items.count() ? items.last() : panel.locator('button').last(), false);
  assert.equal(await panel.evaluate((element) => {
    const rect = element.getBoundingClientRect();
    return rect.top >= 0 && rect.left >= 0 && rect.bottom <= innerHeight && rect.right <= innerWidth;
  }), true, 'menu fora da janela');
  return panel;
}

(async () => {
  const launchOptions = { headless: true };
  if (process.env.BROWSER_PATH) launchOptions.executablePath = process.env.BROWSER_PATH;
  browser = await chromium.launch(launchOptions);
  const context = await browser.newContext();
  await context.route('**/*', async (route) => {
    const url = new URL(route.request().url());
    // A fonte pode ser fornecida de um cache temporário; nada é versionado.
    if (url.hostname === 'cdn.jsdelivr.net') {
      if (process.env.UI_FONT) return route.fulfill({ contentType: 'font/woff2', body: fs.readFileSync(process.env.UI_FONT) });
      return route.continue();
    }
    if (url.origin !== origin) throw new Error('Requisição externa inesperada: ' + url.href);
    const target = path.resolve(root, '.' + decodeURIComponent(url.pathname));
    if (!target.startsWith(root + path.sep) || !fs.existsSync(target) || !fs.statSync(target).isFile()) return route.fulfill({ status: 404, body: '' });
    return route.fulfill({ status: 200, contentType: mime[path.extname(target)] || 'application/octet-stream', body: fs.readFileSync(target) });
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => { if (message.type() === 'error') errors.push(message.text()); });
  page.on('response', (response) => { if (response.status() >= 400) errors.push(`${response.status()} ${response.url()}`); });

  for (const screen of process.argv.includes('--flows-only') ? [] : screens) {
    await page.setViewportSize({ width: sizes[0][0], height: sizes[0][1] });
    await go(page, screen);
    // A mesma tela responde aos redimensionamentos, sem recarregar seus dados.
    for (const [width, height] of sizes) {
      await page.setViewportSize({ width, height });
      await page.evaluate(() => {
        window.scrollTo(0, 0);
        document.querySelectorAll('.app-main,.table-wrap,.kanban-board').forEach((element) => {
          element.scrollTop = 0;
          element.scrollLeft = 0;
        });
      });
      await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
      const issues = await page.evaluate(() => {
        const problems = [];
        if (document.documentElement.scrollWidth > innerWidth + 1) problems.push('overflow horizontal da página');
        const ids = [...document.querySelectorAll('[id]')].map((element) => element.id);
        if (new Set(ids).size !== ids.length) problems.push('ids duplicados');
        for (const wrap of document.querySelectorAll('.table-wrap')) {
          if (wrap.getClientRects().length && wrap.clientHeight < 160) problems.push('tabela sem altura útil: ' + wrap.className);
        }
        const cards = [...document.querySelectorAll('.cost-chart-card,.transactions-card,.budget-card,.crop-cost-card')];
        for (let i = 0; i < cards.length; i++) {
          const a = cards[i].getBoundingClientRect();
          for (const card of cards.slice(i + 1)) {
            const b = card.getBoundingClientRect();
            if (Math.min(a.right, b.right) - Math.max(a.left, b.left) > 1 && Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top) > 1) problems.push('cards de Custos sobrepostos');
          }
        }
        for (const trigger of document.querySelectorAll('.select-trigger')) {
          if (!document.getElementById(trigger.getAttribute('aria-controls'))) problems.push('select sem lista associada');
          if (!trigger.getAttribute('aria-label')) problems.push('select sem nome');
        }
        for (const trigger of document.querySelectorAll('[aria-controls]')) {
          if (!document.getElementById(trigger.getAttribute('aria-controls'))) problems.push('aria-controls sem destino: ' + trigger.getAttribute('aria-controls'));
        }
        return problems;
      });
      assert.deepEqual(issues, [], `${screen} em ${width}x${height}`);
      if (width === 1024) await screenshot(page, screen + '-topo');
      const actions = page.locator('main [data-popover-trigger]:visible');
      if (await actions.count()) {
        const trigger = actions.last();
        await menu(page, trigger);
        await screenshot(page, `${screen}-${width}-menu`);
        await page.keyboard.press('Escape');
        assert.equal(await trigger.getAttribute('aria-expanded'), 'false');
      }
      if (screen === 'custos') {
        await reachable(page.locator('[data-cost-table] tbody tr').last().locator('[data-popover-trigger]'));
        await screenshot(page, `custos-${width}-tabela`);
        await reachable(page.locator('.crop-cost-item').last());
        await screenshot(page, `custos-${width}-resumo`);
      }
    }
    console.log(`OK ${screen} nas sete dimensões desktop`);
  }

  // O fluxo usa cliques e preenchimento reais: não dispara submit pelo DOM.
  await page.setViewportSize({ width: 1024, height: 600 });
  await go(page, 'custos');
  await page.locator('[data-open-cost-modal]').click();
  const form = page.locator('[data-cost-form]');
  await form.locator('[type="submit"]').click();
  assert.equal(await form.locator('[name="description"]').getAttribute('aria-invalid'), 'true');
  await screenshot(page, 'custos-formulario-erros');
  await page.locator('#cost-category-trigger').click();
  await page.locator('#cost-category-listbox [data-value="Insumos"]').click();
  await page.locator('#cost-plot-trigger').click();
  await reachable(page.locator('#cost-plot-listbox [data-value="Propriedade"]'));
  await screenshot(page, 'custos-select-no-formulario');
  await page.keyboard.press('Escape');
  assert.equal(await page.locator('[data-modal="cost-form"]').getAttribute('aria-hidden'), 'false');
  await page.locator('[data-open-date-modal]').click();
  await page.keyboard.press('Escape');
  assert.equal(await page.locator('[data-modal="date-form"]').getAttribute('aria-hidden'), 'true');
  assert.equal(await page.locator('[data-modal="cost-form"]').getAttribute('aria-hidden'), 'false');
  await page.locator('[data-open-date-modal]').click();
  await page.locator('[data-calendar-today]').click();
  await form.locator('[name="description"]').fill('Lançamento de revisão visual com descrição longa para conferir quebra de texto na tabela');
  await form.locator('[name="value"]').fill('123.45');
  const save = form.locator('[type="submit"]');
  await save.focus();
  await page.keyboard.press('Tab');
  assert.equal(await form.locator('[name="description"]').evaluate((element) => element === document.activeElement), true, 'foco saiu do modal');
  await save.click();
  const row = page.locator('[data-cost-table] tbody tr').filter({ hasText: 'Lançamento de revisão visual' });
  const panel = await menu(page, row.locator('[data-popover-trigger]'));
  await panel.locator('[data-edit-cost]').click();
  await form.locator('[name="value"]').fill('234.56');
  await save.click();
  await page.reload({ waitUntil: 'load' });
  await waitForMotion(page);
  assert.match(await row.textContent(), /234,56/);
  await page.locator('[data-cost-search]').fill('sem-resultado-visual');
  await reachable(page.locator('[data-cost-empty] h3'));
  await screenshot(page, 'custos-estado-vazio');
  await page.locator('[data-cost-search]').fill('');
  const deleting = await menu(page, row.locator('[data-popover-trigger]'));
  await deleting.locator('[data-delete-cost]').click();
  assert.equal(await row.count(), 0);
  assert.match(await page.locator('[data-cost-total]').locator('..').locator('.metric-card-desc').textContent(), /4%/);
  const cropSum = await page.locator('.crop-cost-item > strong').evaluateAll((items) => items.reduce((sum, item) => sum + Number(item.textContent.replace(/[^\d,]/g, '').replace(',', '.')), 0));
  assert.equal(cropSum, 20190, 'resumo por cultura diverge do total de lançamentos');
  await page.locator('[data-budget-edit]').click();
  await page.locator('[name="budget"]').fill('1000');
  await page.locator('[data-budget-form] [type="submit"]').click();
  assert.match(await page.locator('[data-budget-remaining]').textContent(), /19.190,00 acima do orçamento/);
  assert.equal(await page.locator('[data-budget-percent]').textContent(), '2019% utilizado');

  // O menu não pode continuar flutuando depois de rolar a página ou redimensionar.
  const trigger = page.locator('[data-cost-table] [data-popover-trigger]').first();
  await menu(page, trigger);
  await page.locator('.app-main').evaluate((element) => { element.scrollTop = element.scrollHeight; });
  await page.waitForFunction(() => document.querySelector('[data-cost-table] [data-popover-trigger]')?.getAttribute('aria-expanded') === 'false');
  await menu(page, trigger);
  await page.setViewportSize({ width: 1100, height: 700 });
  await page.waitForFunction(() => document.querySelector('[data-cost-table] [data-popover-trigger]')?.getAttribute('aria-expanded') === 'false');
  await go(page, 'dashboard');
  assert.match(await page.locator('[data-metric="cost"]').textContent(), /20,2/);
  await reachable(page.locator('.alert-item a').first());
  assert.ok(await page.locator('.alert-item a').first().evaluate((element) => element.clientWidth > 100), 'alerta comprimido na coluna do ícone');
  await screenshot(page, 'dashboard-alertas-ajustados');
  await go(page, 'configuracoes');
  await page.locator('button[data-settings-tab="property"]').click();
  await page.locator('[data-open-harvest-modal]').click();
  assert.equal(await page.locator('[data-modal="harvest-form"] .calendar-modal-header').evaluate((header) => {
    return Math.abs(header.querySelector('button').getBoundingClientRect().top - header.getBoundingClientRect().top) < 1;
  }), true, 'fechamento da safra desalinhado do título');
  await reachable(page.locator('[data-harvest-option="2026/27"]'));
  await screenshot(page, 'configuracoes-seletor-safra');
  await page.keyboard.press('Escape');
  assert.equal(await page.locator('[data-open-harvest-modal]').evaluate((element) => element === document.activeElement), true);
  for (const screen of ['login', 'criar-conta', 'recuperar-senha', 'redefinir-senha']) {
    await go(page, screen);
    await page.locator('[type="submit"]').click();
    assert.ok(await page.locator('form [aria-invalid="true"]').count(), 'formulário vazio aceito: ' + screen);
    await screenshot(page, screen + '-validacao');
  }
  // Um custo ilegível não pode virar um total zero aparentemente confirmado.
  await page.evaluate(() => localStorage.setItem('agro.operational.v1.costs', '{invalido'));
  await go(page, 'dashboard');
  assert.equal(await page.locator('[data-metric="cost"]').textContent(), '—');
  assert.match(await page.locator('[data-storage-error]').textContent(), /recuperar os custos/);
  await screenshot(page, 'dashboard-custos-indisponiveis');
  assert.deepEqual(errors, [], 'console ou recurso com erro');
  console.log('OK formulários, erros, selects, calendário, foco, CRUD, orçamento, resumos, safra, recarga, vazio, menus e custo ilegível; console sem erros');
  await browser.close();
})().catch(async (error) => {
  console.error(error);
  if (shots && browser) {
    const current = browser.contexts()[0]?.pages()[0];
    if (current) await screenshot(current, 'falha');
  }
  if (browser) await browser.close();
  process.exitCode = 1;
});
