/* Miguel — RA: 25003110 */
/* Autor Secundário: Pedro Henrique Contardi Soler - 25005592 */
// Validação de persistência em navegador real, sem servidor ou backend.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const { launchTestBrowser } = require("./browser-launch.cjs");
const { mockApi } = require("./mock-api.cjs");

const root = process.cwd();
const origin = "http://agro.local";
const prefix = "agro.operational.v1.e2e-local.e2e-property.";
const mime = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".woff2": "font/woff2",
};

async function context(browser, storageFailure) {
  const browserContext = await browser.newContext();
  await browserContext.route("**/*", async (route) => {
    const url = new URL(route.request().url());
    if (url.origin !== origin) return route.abort();
    const file = path.resolve(root, "." + decodeURIComponent(url.pathname));
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
      return route.fulfill({status: 404, body: "Arquivo não encontrado"});
    }
    return route.fulfill({status: 200, contentType: mime[path.extname(file)] || "application/octet-stream", body: fs.readFileSync(file)});
  });
  await browserContext.addInitScript(() => {
    sessionStorage.setItem('agro.sessionToken', 'e2e-local');
    sessionStorage.setItem('agro.sessionUser', JSON.stringify({id: 'e2e-local', propriedadeId: 'e2e-property'}));
  });
  await browserContext.route('http://127.0.0.1:8080/api/me', async (route) => {
    const headers = {'Access-Control-Allow-Origin': origin, 'Access-Control-Allow-Headers': 'Authorization',
      'Access-Control-Allow-Methods': 'GET, OPTIONS'};
    if (route.request().method() === 'OPTIONS') return route.fulfill({status: 204, headers});
    return route.fulfill({status: 200, headers, contentType: 'application/json', body: '{"usuario":{"id":"e2e-local","propriedadeId":"e2e-property"}}'});
  });
  await mockApi(browserContext, origin);
  if (storageFailure) {
    await browserContext.addInitScript((mode) => {
      const get = Storage.prototype.getItem;
      const set = Storage.prototype.setItem;
      Storage.prototype.getItem = function (key) {
        if (mode === "read" && key.startsWith("agro.operational.v1.")) throw new DOMException("Bloqueado", "SecurityError");
        return get.call(this, key);
      };
      Storage.prototype.setItem = function (key, value) {
        if (mode === "write" && !window.__allowStorageWrites && key.startsWith("agro.operational.v1.")) throw new DOMException("Sem espaço", "QuotaExceededError");
        return set.call(this, key, value);
      };
    }, storageFailure);
  }
  return browserContext;
}

async function open(browserContext, name) {
  const page = await browserContext.newPage();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(origin + "/" + name + ".html", {waitUntil: "load"});
  if (name === "ocorrencias") await page.waitForFunction(() => /contexto consultado|possível consultar/.test(document.querySelector("[data-occ-message]").textContent));
  else await page.waitForFunction(() => Boolean(window.AgroLocal));
  assert.deepEqual(errors, [], name + " apresentou erro JavaScript");
  return page;
}

async function stored(page, key) {
  return page.evaluate(({name, prefix}) => JSON.parse(localStorage.getItem(prefix + name)), {name: key, prefix});
}

async function submit(page, selector) {
  await page.locator(selector).evaluate((form) => form.requestSubmit());
}

async function test(name, run) {
  try {
    await run();
    console.log("OK " + name);
  } catch (error) {
    console.error("FALHOU " + name + ": " + error.stack);
    process.exitCode = 1;
  }
}

(async () => {
  const browser = await launchTestBrowser(chromium);
  try {
    await test("inicialização das sete telas", async () => {
      const ctx = await context(browser);
      try {
        const failures = [];
        for (const name of ["dashboard", "areas-cultivo", "atividades", "insumos", "ocorrencias", "custos", "configuracoes"]) {
          try {
            const page = await open(ctx, name);
            await page.close();
          } catch (error) {
            failures.push(name + ": " + error.message);
          }
        }
        assert.deepEqual(failures, []);
      } finally { await ctx.close(); }
    });

    await test("insumos: entrada, saída, histórico, métricas e recarga", async () => {
      const ctx = await context(browser);
      try {
        const page = await open(ctx, "insumos");
        const row = () => page.locator("[data-stock-table] tbody tr").first();
        assert.equal(await row().getAttribute("data-qty"), "85");
        assert.equal(await page.locator("[data-metric='total-products']").textContent(), "7");
        await row().locator("[data-row-action='entrada']").evaluate((button) => button.click());
        await page.locator("[data-entrada-form] [name='quantidade']").fill("20");
        await page.locator("[data-entrada-form] [name='fornecedor']").fill("Fornecedor teste");
        await submit(page, "[data-entrada-form]");
        assert.equal(await row().getAttribute("data-qty"), "105");
        assert.equal((await stored(page, "stock")).data[0].movements.length, 1);
        assert.equal(await page.locator("[data-metric='entries']").textContent(), "1");
        await row().locator("[data-row-action='saida']").evaluate((button) => button.click());
        await page.locator("[data-saida-form] [name='quantidade']").fill("10");
        await page.locator("[data-saida-form] [name='destino']").fill("Talhão teste");
        await submit(page, "[data-saida-form]");
        assert.equal(await row().getAttribute("data-qty"), "95");
        await page.reload();
        assert.equal(await row().getAttribute("data-qty"), "95");
        assert.equal(await page.locator("[data-metric='entries']").textContent(), "1");
        await row().locator("[data-row-action='detail']").evaluate((button) => button.click());
        const history = await page.locator("[data-movement-history]").textContent();
        assert.match(history, /Fornecedor teste/);
        assert.match(history, /Talhão teste/);
        assert.equal((await stored(page, "stock")).data[0].movements.length, 2);
        await row().locator("[data-row-action='edit']").evaluate((button) => button.click());
        await page.locator("[data-produto-editar-form] [name='minimo']").fill("80");
        await submit(page, "[data-produto-editar-form]");
        await page.reload();
        assert.equal(await row().getAttribute("data-min"), "80");
        assert.equal(await row().locator("[data-status-badge]").textContent(), "Normal");
      } finally { await ctx.close(); }
    });

    // O fluxo de ocorrências agora usa servidor real em tests/ocorrencias-real.cjs.
    await test("ocorrências ignoram o cache local legado", async () => {
      const ctx = await context(browser);
      try {
        const page = await open(ctx, "ocorrencias");
        await page.evaluate((p) => localStorage.setItem(p + "occurrences", JSON.stringify({version: 1, data: [{occ: "Legada"}]})), prefix);
        await page.reload();
        await page.waitForFunction(() => document.querySelector("[data-occ-message]").textContent.includes("contexto consultado"));
        assert.equal(await page.locator("[data-occ-table] tbody tr").count(), 0);
        assert.equal(await page.locator("[data-metric='abertas']").textContent(), "0");
      } finally { await ctx.close(); }
    });

    await test("insumos: exclusão com saldo zero sobrevive à recarga", async () => {
      const ctx = await context(browser);
      try {
        const page = await open(ctx, "insumos");
        await page.evaluate((p) => {
          const data = window.AgroLocal.read("stock");
          data[0].qty = 0;
          localStorage.setItem(p + "stock", JSON.stringify({version: 1, data}));
        }, prefix);
        await page.reload();
        await page.locator("[data-stock-table] tbody tr").first().locator("[data-row-action='delete']").evaluate((button) => button.click());
        await page.locator("[data-confirm-excluir]").click();
        await page.reload();
        assert.equal(await page.locator("[data-stock-table] tbody tr").count(), 6);
        assert.equal(await page.locator("[data-metric='total-products']").textContent(), "6");
      } finally { await ctx.close(); }
    });

    await test("coleções vazias persistidas não recuperam dados de exemplo", async () => {
      const ctx = await context(browser);
      try {
        const page = await ctx.newPage();
        await page.goto(origin + "/insumos.html");
        await page.evaluate((p) => {
          localStorage.setItem(p + "stock", JSON.stringify({version: 1, data: []}));
          localStorage.setItem(p + "occurrences", JSON.stringify({version: 1, data: []}));
        }, prefix);
        await page.reload();
        assert.equal(await page.locator("[data-stock-table] tbody tr").count(), 0);
        assert.equal(await page.locator("[data-metric='total-products']").textContent(), "0");
        await page.goto(origin + "/ocorrencias.html");
        await page.waitForFunction(() => document.querySelector("[data-occ-message]").textContent.includes("contexto consultado"));
        assert.equal(await page.locator("[data-occ-table] tbody tr").count(), 0);
        assert.equal(await page.locator("[data-metric='abertas']").textContent(), "0");
      } finally { await ctx.close(); }
    });

    for (const value of ["{quebrado", JSON.stringify({version: 1, data: [{id: "sem campos"}]})]) {
      await test("armazenamento inválido preservado: " + value.slice(0, 18), async () => {
        const ctx = await context(browser);
        try {
          const page = await ctx.newPage();
          await page.goto(origin + "/insumos.html");
          await page.evaluate(([key, raw]) => localStorage.setItem(key, raw), [prefix + "stock", value]);
          await page.reload();
          assert.equal(await page.locator("[data-stock-table] tbody tr").count(), 0);
          assert.equal(await page.locator("[data-storage-error]").count(), 1);
          assert.equal(await page.evaluate((key) => localStorage.getItem(key), prefix + "stock"), value);
        } finally { await ctx.close(); }
      });
    }

    for (const qty of [-1, Infinity]) {
      await test("saldo inválido rejeitado: " + String(qty), async () => {
        const ctx = await context(browser);
        try {
          const page = await open(ctx, "insumos");
          const raw = await page.evaluate(([p, value]) => {
            const data = window.AgroLocal.read("stock");
            data[0].qty = value;
            const envelope = JSON.stringify({version: 1, data});
            localStorage.setItem(p + "stock", envelope);
            return envelope;
          }, [prefix, qty]);
          await page.reload();
          assert.equal(await page.locator("[data-stock-table] tbody tr").count(), 0);
          assert.equal(await page.locator("[data-storage-error]").count(), 1);
          assert.equal(await page.evaluate((key) => localStorage.getItem(key), prefix + "stock"), raw);
        } finally { await ctx.close(); }
      });
    }

    await test("abas concorrentes preservam a gravação mais recente", async () => {
      const ctx = await context(browser);
      try {
        const fresh = await open(ctx, "insumos");
        const stale = await open(ctx, "insumos");
        async function entry(page, amount) {
          await page.locator("[data-stock-table] tbody tr").first().locator("[data-row-action='entrada']").evaluate((button) => button.click());
          await page.locator("[data-entrada-form] [name='quantidade']").fill(String(amount));
          await submit(page, "[data-entrada-form]");
        }
        await entry(fresh, 10);
        await entry(stale, 20);
        assert.equal((await stored(fresh, "stock")).data[0].qty, 95);
        assert.equal(await stale.locator("[data-stock-table] tbody tr").first().getAttribute("data-qty"), "85");
        assert.equal(await stale.locator("[data-storage-error]").count(), 1);
        assert.equal(await stale.locator(".toast-title:has-text('Entrada registrada')").count(), 0);
        await stale.reload();
        assert.equal(await stale.locator("[data-stock-table] tbody tr").first().getAttribute("data-qty"), "95");
      } finally { await ctx.close(); }
    });

    await test("falha de escrita desfaz operação sem sucesso fictício", async () => {
      const ctx = await context(browser, "write");
      try {
        const page = await open(ctx, "insumos");
        const row = page.locator("[data-stock-table] tbody tr").first();
        await row.locator("[data-row-action='entrada']").evaluate((button) => button.click());
        await page.locator("[data-entrada-form] [name='quantidade']").fill("10");
        await submit(page, "[data-entrada-form]");
        assert.equal(await page.locator("[data-stock-table] tbody tr").first().getAttribute("data-qty"), "85");
        assert.equal(await page.locator("[data-metric='entries']").textContent(), "0");
        assert.equal(await page.locator(".toast-title:has-text('Entrada registrada')").count(), 0);
        assert.equal(await page.locator("[data-storage-error]").count(), 1);
        // Uma segunda tentativa deve usar a linha reconstruída no rollback.
        await page.evaluate(() => { window.__allowStorageWrites = true; });
        await submit(page, "[data-entrada-form]");
        assert.equal(await page.locator("[data-stock-table] tbody tr").first().getAttribute("data-qty"), "95");
        assert.equal((await stored(page, "stock")).data[0].qty, 95);
      } finally { await ctx.close(); }
    });

    await test("falha de leitura não inventa dados recuperados", async () => {
      const ctx = await context(browser, "read");
      try {
        const page = await open(ctx, "insumos");
        assert.equal(await page.locator("[data-stock-table] tbody tr").count(), 0);
        assert.equal(await page.locator("[data-storage-error]").count(), 1);
      } finally { await ctx.close(); }
    });
  } finally {
    await browser.close();
  }
})().catch((error) => { console.error(error); process.exitCode = 1; });
