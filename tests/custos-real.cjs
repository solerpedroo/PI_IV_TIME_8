/* Autor: Tomas de Paula Michelon Toniato - RA: 25004211 */
/**
 * Custos com servidor real em memória e navegador (#157, #158, #159).
 * 1. Lançamento criado pela tela só aparece depois da confirmação da API.
 * 2. Totais, custo por hectare, cultura e evolução batem com um conjunto conhecido.
 * 3. Talhão, safra e período recortam lista, indicadores e CSV.
 * 4. Edição e exclusão confirmadas pela API; erro de vínculo volta no campo.
 * 5. Recarga mantém o período escolhido e os dados do servidor.
 * 6. Saída de estoque com atividade gera o custo de Insumos sem duplicidade (#156).
 */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { spawn } = require("node:child_process");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const { launchTestBrowser } = require("./browser-launch.cjs");

const base = "http://127.0.0.1:8080";
const classpath = `bin${process.platform === "win32" ? ";" : ":"}lib/*`;

async function submit(page, selector, values) {
  await page.evaluate(({ selector, values }) => {
    const form = document.querySelector(selector);
    for (const [name, value] of Object.entries(values)) {
      const control = form.elements[name];
      control.value = value;
      control.dispatchEvent(new Event("change", { bubbles: true }));
    }
    form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
  }, { selector, values });
}

// Os selects ficam escondidos pelo componente customizado; o nativo continua sendo a fonte.
async function choose(page, selector, value) {
  await page.evaluate(({ selector, value }) => {
    const select = document.querySelector(selector);
    select.value = value;
    select.dispatchEvent(new Event("change", { bubbles: true }));
  }, { selector, value });
}

const text = (page, selector) => page.locator(selector).textContent();
const rows = (page) => page.locator("[data-cost-table] tbody tr").count();

async function settled(page) {
  await page.waitForFunction(() => document.querySelector("[data-cost-message]").textContent === "");
}

(async () => {
  try {
    if ((await fetch(base + "/health")).ok) {
      console.error("A porta 8080 já está em uso por outro processo.");
      process.exit(1);
    }
  } catch {
    // porta livre
  }

  const server = spawn("java", ["-cp", classpath, "Servidor", "8080", "--memoria"], {
    cwd: path.join(__dirname, "../backend/servidor"),
    env: { ...process.env, SMTP_HOST: "", SMTP_USERNAME: "", SMTP_PASSWORD: "", SMTP_FROM: "", SMTP_PORT: "" },
  });
  let serverLog = "";
  server.stdout.on("data", (chunk) => { serverLog += chunk.toString(); });
  server.stderr.on("data", (chunk) => { serverLog += chunk.toString(); });

  let browser;
  try {
    for (let attempt = 0; ; attempt++) {
      try {
        if ((await fetch(base + "/health")).ok) break;
      } catch {}
      if (attempt === 60 || server.exitCode !== null) throw new Error("Servidor não iniciou a tempo. Log:\n" + serverLog);
      await new Promise((resolve) => setTimeout(resolve, 100));
    }

    const api = async (endpoint, body, token, method = null) => {
      const res = await fetch(base + endpoint, {
        method: method || (body ? "POST" : "GET"),
        headers: { "Content-Type": "application/json", ...(token ? { Authorization: "Bearer " + token } : {}) },
        body: body ? JSON.stringify(body) : undefined,
      });
      return { status: res.status, ok: res.ok, data: await res.json().catch(() => ({})) };
    };

    await api("/api/auth/register", {
      name: "Operador Custos", email: "custos@agro.test", phone: "(19) 98888-2222",
      password: "Senha@123", confirm: "Senha@123", farm: "Fazenda Custos", area: "90 ha", terms: true,
    });
    await api("/api/auth/confirm-email", { token: serverLog.match(/token: ([a-zA-Z0-9-]+)/)[1] });
    const login = await api("/api/auth/login", { email: "custos@agro.test", password: "Senha@123" });
    assert.ok(login.ok, "login");
    const token = login.data.token;

    const norte = (await api("/api/talhoes", { nome: "T-01 Norte", cultura: "Soja", hectares: 50, status: "Ativo", safra: "2025/26" }, token)).data;
    const sul = (await api("/api/talhoes", { nome: "T-02 Sul", cultura: "Milho", hectares: 40, status: "Ativo", safra: "2025/26" }, token)).data;
    const safra = (await api("/api/safras", { nome: "Soja 2026", cultura: "Soja", dataInicio: "2026-01-01", dataFim: "2026-12-31", talhoes: [norte.id] }, token)).data;
    assert.ok(norte.id && sul.id && safra.id, "talhões e safra de apoio");

    // Conjunto conhecido: Diesel e Salário pela API, Sementes pela tela.
    assert.equal((await api("/api/custos", { description: "Diesel", category: "Combustível", value: 300.5, date: "2026-04-05", talhaoId: sul.id }, token)).status, 201);
    assert.equal((await api("/api/custos", { description: "Salário", category: "Mão de Obra", value: 2000, date: "2026-04-20" }, token)).status, 201);

    browser = await launchTestBrowser(chromium);
    const context = await browser.newContext({ acceptDownloads: true });
    await context.addInitScript(({ token, user }) => {
      sessionStorage.setItem("agro.sessionToken", token);
      sessionStorage.setItem("agro.sessionUser", JSON.stringify(user));
    }, { token, user: login.data.usuario });
    const page = await context.newPage();
    const pageErrors = [];
    page.on("pageerror", (error) => pageErrors.push(error.message));

    await page.goto(base + "/custos.html");
    await page.waitForFunction((id) => document.querySelector(`[data-cost-filter-season] option[value="${id}"]`), safra.id);
    await settled(page);
    assert.equal(await rows(page), 2, "lista vem da API, sem exemplos locais");

    // 1. Criar pela tela: só aparece depois da confirmação.
    await page.locator("[data-open-cost-modal]").click();
    await submit(page, "[data-cost-form]", { description: "Sementes", category: "Insumos", value: "1000", date: "2026-03-10", talhaoId: norte.id, safraId: safra.id });
    await page.waitForFunction(() => document.querySelectorAll("[data-cost-table] tbody tr").length === 3);
    const created = (await api("/api/custos?q=sementes", null, token)).data;
    assert.equal(created.length, 1, "lançamento persistido no servidor");
    assert.equal(created[0].safraId, safra.id);
    console.log("OK criação confirmada pela API");

    // 2. Indicadores sobre o conjunto: 3300,50 em 90 ha.
    assert.match(await text(page, "[data-cost-total]"), /R\$\s3,3 mil/);
    assert.match(await text(page, "[data-cost-hectare]"), /36,67/);
    assert.match(await text(page, "[data-cost-hectare-desc]"), /90 ha/);
    assert.match(await text(page, '[data-cost-category="Insumos"]'), /R\$\s1 mil/);
    assert.match(await text(page, '[data-cost-category="Mão de Obra"]'), /R\$\s2 mil/);
    const crops = await page.locator("[data-crop-costs] .crop-cost-item").allTextContents();
    assert.ok(crops.some((item) => item.includes("Soja") && item.includes("1.000,00")), "cultura da safra");
    assert.ok(crops.some((item) => item.includes("Milho") && item.includes("300,50")), "cultura do talhão");
    assert.ok(crops.some((item) => item.includes("Sem vínculo") && item.includes("2.000,00")), "gasto sem cultura separado");
    assert.equal(await page.locator("[data-cost-evolution] .cost-bar-row").count(), 2, "evolução de março e abril");
    console.log("OK indicadores, cultura e evolução");

    // 3a. Talhão recorta a consulta e usa a área dele.
    await choose(page, "[data-cost-filter-plot]", norte.id);
    await page.waitForFunction(() => document.querySelectorAll("[data-cost-table] tbody tr").length === 1);
    await settled(page);
    assert.match(await text(page, "[data-cost-hectare]"), /20,00/);
    assert.match(await text(page, "[data-cost-context]"), /T-01 Norte/);
    await choose(page, "[data-cost-filter-plot]", "propriedade");
    await page.waitForFunction(() => document.querySelector("[data-cost-table] tbody tr")?.textContent.includes("Salário"));
    await page.locator("[data-cost-clear-filters]").click();
    await page.waitForFunction(() => document.querySelectorAll("[data-cost-table] tbody tr").length === 3);

    // 3b. Safra recorta e usa a área dos talhões dela.
    await choose(page, "[data-cost-filter-season]", safra.id);
    await page.waitForFunction(() => document.querySelectorAll("[data-cost-table] tbody tr").length === 1);
    await settled(page);
    assert.match(await text(page, "[data-cost-total]"), /R\$\s1 mil/);
    await page.locator("[data-cost-clear-filters]").click();
    await page.waitForFunction(() => document.querySelectorAll("[data-cost-table] tbody tr").length === 3);

    // 3c. Período personalizado: só abril.
    await page.locator("[data-open-period-modal]").click();
    await page.locator('[data-period-option="personalizado"]').click();
    await page.locator("[data-period-from]").fill("2026-04-30");
    await page.locator("[data-period-to]").fill("2026-04-01");
    await page.locator("[data-confirm-period]").click();
    assert.match(await text(page, "[data-modal='period-form'] [data-error-for='ate']"), /posterior/, "intervalo invertido é recusado");
    await page.locator("[data-period-from]").fill("2026-04-01");
    await page.locator("[data-period-to]").fill("2026-04-30");
    await page.locator("[data-confirm-period]").click();
    await page.waitForFunction(() => document.querySelectorAll("[data-cost-table] tbody tr").length === 2);
    await settled(page);
    assert.match(await text(page, "[data-period-label]"), /01\/04\/2026 a 30\/04\/2026/);
    assert.match(await text(page, "[data-cost-total]"), /R\$\s2,3 mil/);
    assert.equal(await page.locator("[data-cost-evolution] .cost-bar-row").count(), 1);

    // 3d. O CSV é o recorte exibido.
    const [download] = await Promise.all([page.waitForEvent("download"), page.locator("[data-export-costs]").click()]);
    const csv = fs.readFileSync(await download.path(), "utf8");
    assert.equal(download.suggestedFilename(), "agrogestao-custos-2026-04-01-a-2026-04-30.csv");
    assert.match(csv, /Diesel/);
    assert.match(csv, /Salário/);
    assert.doesNotMatch(csv, /Sementes/);
    assert.equal(csv.trim().split("\n").length, 3, "cabeçalho e dois lançamentos");
    console.log("OK filtros de talhão, safra, período e CSV");

    // 4a. Vínculo inválido: o erro do servidor volta no campo e nada é gravado.
    await page.locator("[data-open-cost-modal]").click();
    await submit(page, "[data-cost-form]", { description: "Inválido", category: "Outros", value: "10", date: "2026-04-10", talhaoId: sul.id, safraId: safra.id });
    await page.waitForFunction(() => document.querySelector("[data-cost-form] [data-error-for=talhaoId]").textContent !== "");
    assert.match(await text(page, "[data-cost-form] [data-error-for=talhaoId]"), /safra/);
    assert.equal((await api("/api/custos?q=inv", null, token)).data.length, 0);
    await page.locator("[data-modal='cost-form'] [data-close-modal]").click();

    // 4b. Edição e exclusão.
    const diesel = (await api("/api/custos?q=diesel", null, token)).data[0];
    await page.evaluate((id) => document.querySelector(`[data-edit-cost="${id}"]`).click(), diesel.id);
    await submit(page, "[data-cost-form]", { description: "Diesel", category: "Combustível", value: "400", date: "2026-04-05", talhaoId: sul.id, safraId: "" });
    await page.waitForFunction(() => /2,4 mil/.test(document.querySelector("[data-cost-total]").textContent));
    assert.equal((await api(`/api/custos/${diesel.id}`, null, token)).data.value, 400);
    const salario = (await api("/api/custos?q=sal", null, token)).data[0];
    await page.evaluate((id) => document.querySelector(`[data-delete-cost="${id}"]`).click(), salario.id);
    await page.waitForFunction(() => document.querySelectorAll("[data-cost-table] tbody tr").length === 1);
    assert.equal((await api(`/api/custos/${salario.id}`, null, token)).status, 404);
    console.log("OK edição, exclusão e erro de vínculo");

    // 5. Recarga: o período continua e os valores vêm do servidor.
    await page.reload();
    await page.waitForFunction(() => document.querySelectorAll("[data-cost-table] tbody tr").length === 1);
    await settled(page);
    assert.match(await text(page, "[data-period-label]"), /01\/04\/2026/);
    assert.match(await text(page, "[data-cost-total]"), /400,00/);

    // O painel mostra o mesmo total, sem filtro: 1000 + 400.
    await page.goto(base + "/dashboard.html");
    await page.waitForFunction(() => /1\.400,00/.test(document.querySelector('[data-metric="cost"]').textContent));
    console.log("OK recarga e painel");

    // 6. Consumo pela tela de Insumos: 50 kg a R$ 2,50 viram R$ 125,00 de custo.
    const atividade = (await api("/api/atividades", { nome: "Adubação T-01", tipo: "Aplicação", talhao: "T-01 Norte", data: "2026-04-15", safraId: safra.id }, token)).data;
    assert.ok(atividade.id, "atividade de apoio");
    const ureia = (await api("/api/insumos", { nome: "Ureia", categoria: "Fertilizante", unidade: "kg", saldo: 200, minimo: 10, custoUnitario: 2.5 }, token)).data;
    await page.goto(base + "/insumos.html");
    const linha = page.locator("[data-stock-table] tbody tr").first();
    await linha.waitFor();
    await linha.locator("[data-popover-trigger]").click();
    await linha.locator("[data-operation=saida]").click();
    await page.waitForFunction((id) => document.querySelector(`[data-saida-form] [name=atividadeId] option[value="${id}"]`), atividade.id);
    await page.locator("[data-saida-form] [name=quantidade]").fill("50");
    await page.locator("[data-saida-form] [name=atividadeId]").selectOption(atividade.id);
    await page.locator("[data-saida-form]").evaluate((form) => form.requestSubmit());
    await page.waitForFunction(() => [...document.querySelectorAll(".toast")].some((toast) => toast.textContent.includes("125,00 lançado em Custos")));
    const consumo = (await api("/api/custos?q=consumo", null, token)).data;
    assert.equal(consumo.length, 1, "um custo para a saída");
    assert.equal(consumo[0].value, 125);
    assert.equal(consumo[0].talhaoId, norte.id);
    assert.equal(consumo[0].safraId, safra.id);
    assert.equal(consumo[0].origem.insumoId, ureia.id);
    assert.equal((await api(`/api/custos/${consumo[0].id}`, { description: "x", category: "Outros", value: 1, date: "2026-04-15" }, token, "PUT")).status, 409);

    // O custo é da data da baixa (hoje): sai do período de abril guardado antes.
    await page.goto(base + "/custos.html");
    await settled(page);
    await page.locator("[data-cost-clear-filters]").click();
    await page.waitForFunction(() => document.querySelector("[data-cost-table] tbody tr .cost-origin"));
    await settled(page);
    const linhaConsumo = page.locator("[data-cost-table] tbody tr").filter({ hasText: "Consumo de Ureia" });
    assert.equal(await linhaConsumo.locator("[data-edit-cost]").count(), 0, "sem editar no custo de consumo");
    assert.equal(await linhaConsumo.locator(".cost-origin-link").getAttribute("href"), `insumos.html?insumoId=${ureia.id}`);
    await page.reload();
    await page.waitForFunction(() => document.querySelector("[data-cost-table] tbody tr .cost-origin"));
    assert.equal((await api("/api/custos?q=consumo", null, token)).data.length, 1, "consultas repetidas não duplicam");
    console.log("OK consumo de estoque gera custo sem duplicidade");

    assert.deepEqual(pageErrors, [], "sem erros de script");
    console.log("custos-real: todas as verificações passaram");
  } catch (error) {
    console.error(error.stack || error);
    process.exitCode = 1;
  } finally {
    if (browser) await browser.close();
    server.kill();
  }
})();
