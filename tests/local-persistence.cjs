/* Miguel — RA: 25003110 */
/* Autor secundário: Pedro Henrique Contardi Soler - 25005592 */
// Validação de persistência em navegador real, sem servidor ou backend.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const { launchTestBrowser } = require("./browser-launch.cjs");
const { mockApi } = require("./mock-api.cjs");

const root = path.resolve(__dirname, '../frontend');
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
  if (name === "insumos") await page.locator("[data-stock-retry]").waitFor();
  else if (name === "ocorrencias") await page.waitForFunction(() => /contexto consultado|possível consultar/.test(document.querySelector("[data-occ-message]").textContent));
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

    // O CRUD/concorrência de estoque migrou para estoque-real.cjs com MongoDB real.
    await test("insumos: API indisponível não recupera mock nem legado local", async () => {
      const ctx = await context(browser);
      try {
        const page = await open(ctx, "insumos");
        await page.evaluate(p => localStorage.setItem(p + "stock", JSON.stringify({version: 1, data: [{product: "Legado fictício"}]})), prefix);
        await page.reload();
        await page.locator("[data-stock-retry]").waitFor();
        assert.equal(await page.locator("[data-stock-table] tbody tr").count(), 0);
        assert.equal(await page.locator("[data-metric=total-products]").textContent(), "—");
        assert.equal(await page.locator("[data-open-produto]").isDisabled(), true);
        assert.equal(await page.evaluate(() => Boolean(window.AgroLocal)), false);
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

    await test("coleção local vazia não recupera dados de exemplo", async () => {
      const ctx = await context(browser);
      try {
        const page = await open(ctx, "ocorrencias");
        await page.evaluate(p => localStorage.setItem(p + "occurrences", JSON.stringify({version: 1, data: []})), prefix);
        await page.reload();
        assert.equal(await page.locator("[data-occ-table] tbody tr").count(), 0);
        assert.equal(await page.locator("[data-metric=abertas]").textContent(), "0");
      } finally { await ctx.close(); }
    });

    for (const value of ["{quebrado", JSON.stringify({version: 1, data: [{id: "sem campos"}]})]) {
      await test("armazenamento inválido preservado: " + value.slice(0, 18), async () => {
        const ctx = await context(browser);
        try {
          const page = await ctx.newPage();
          await page.goto(origin + "/atividades.html");
          await page.evaluate(([key, raw]) => localStorage.setItem(key, raw), [prefix + "activities", value]);
          await page.reload();
          assert.equal(await page.locator(".kanban-card").count(), 0);
          assert.equal(await page.locator("[data-storage-error]").count(), 1);
          assert.equal(await page.evaluate((key) => localStorage.getItem(key), prefix + "activities"), value);
        } finally { await ctx.close(); }
      });
    }

    await test("abas locais concorrentes preservam a gravação mais recente", async () => {
      const ctx = await context(browser);
      try {
        const fresh = await open(ctx, "atividades");
        const stale = await open(ctx, "atividades");
        for (const page of [fresh, stale]) await page.evaluate(() => window.AgroLocal.read("notifications", []));
        assert.equal(await fresh.evaluate(() => window.AgroLocal.save("notifications", ["Atualização da primeira aba"])), true);
        assert.equal(await stale.evaluate(() => window.AgroLocal.save("notifications", ["Alteração desatualizada"])), false);
        assert.deepEqual((await stored(fresh, "notifications")).data, ["Atualização da primeira aba"]);
        assert.equal(await stale.locator("[data-storage-error]").count(), 1);
      } finally { await ctx.close(); }
    });

    await test("falha de escrita local não confirma gravação", async () => {
      const ctx = await context(browser, "write");
      try {
        const page = await open(ctx, "atividades");
        assert.equal(await page.evaluate(() => window.AgroLocal.save("notifications", ["Não salvo"])), false);
        assert.equal(await page.locator("[data-storage-error]").count(), 1);
        assert.equal(await stored(page, "notifications"), null);
        await page.evaluate(() => { window.__allowStorageWrites = true; });
        assert.equal(await page.evaluate(() => window.AgroLocal.save("notifications", ["Salvo"])), true);
        assert.deepEqual((await stored(page, "notifications")).data, ["Salvo"]);
      } finally { await ctx.close(); }
    });

    await test("falha de leitura não inventa dados recuperados", async () => {
      const ctx = await context(browser, "read");
      try {
        const page = await open(ctx, "atividades");
        assert.equal(await page.locator(".kanban-card").count(), 0);
        assert.equal(await page.locator("[data-storage-error]").count(), 1);
      } finally { await ctx.close(); }
    });
  } finally {
    await browser.close();
  }
})().catch((error) => { console.error(error); process.exitCode = 1; });
