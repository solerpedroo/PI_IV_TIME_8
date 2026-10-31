/* Autor: Tomas de Paula Michelon Toniato - RA: 25004211 */
/**
 * Orçamento por safra com servidor real em memória e navegador (#226, #229).
 * 1. Safra sem orçamento não ganha meta inventada; o realizado vem dos custos da safra.
 * 2. Definir, alterar e remover o planejado só valem depois da confirmação da API.
 * 3. Custo novo lançado pela tela muda realizado, desvio e alerta de limite.
 * 4. Erro do servidor volta no campo; o filtro de safra troca o card.
 * 5. Recarga mantém o planejado; safra finalizada fica somente leitura.
 * Com UI_SCREENSHOTS=<pasta> grava as telas de cada situação.
 */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { spawn } = require("node:child_process");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const { launchTestBrowser } = require("./browser-launch.cjs");

const base = "http://127.0.0.1:8080";
const classpath = `bin${process.platform === "win32" ? ";" : ":"}lib/*`;
const shots = process.env.UI_SCREENSHOTS || "";

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

async function choose(page, selector, value) {
  await page.evaluate(({ selector, value }) => {
    const select = document.querySelector(selector);
    select.value = value;
    select.dispatchEvent(new Event("change", { bubbles: true }));
  }, { selector, value });
}

const text = (page, selector) => page.locator(selector).textContent();
const situation = (page, id) => page.locator(`[data-budget-pick="${id}"] [data-budget-situation]`).getAttribute("data-budget-situation");

async function shot(page, name) {
  if (!shots) return;
  fs.mkdirSync(shots, { recursive: true });
  await page.locator("[data-budget-card]").screenshot({ path: path.join(shots, `orcamento-${name}.png`) });
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
      name: "Operador Orçamento", email: "orcamento@agro.test", phone: "(19) 98888-3333",
      password: "Senha@123", confirm: "Senha@123", farm: "Fazenda Orçamento", area: "90 ha", terms: true,
    });
    await api("/api/auth/confirm-email", { token: serverLog.match(/token: ([a-zA-Z0-9-]+)/)[1] });
    const login = await api("/api/auth/login", { email: "orcamento@agro.test", password: "Senha@123" });
    assert.ok(login.ok, "login");
    const token = login.data.token;

    const norte = (await api("/api/talhoes", { nome: "T-01 Norte", cultura: "Soja", hectares: 50, status: "Ativo", safra: "2025/26" }, token)).data;
    const sul = (await api("/api/talhoes", { nome: "T-02 Sul", cultura: "Milho", hectares: 40, status: "Ativo", safra: "2025/26" }, token)).data;
    const soja = (await api("/api/safras", { nome: "Soja 2026", cultura: "Soja", dataInicio: "2026-01-01", dataFim: "2026-12-31", talhoes: [norte.id] }, token)).data;
    const milho = (await api("/api/safras", { nome: "Milho 2026", cultura: "Milho", dataInicio: "2026-01-01", dataFim: "2026-12-31", talhoes: [sul.id] }, token)).data;
    assert.ok(norte.id && sul.id && soja.id && milho.id, "talhões e safras de apoio");

    // Realizado conhecido: Soja 300 + 500, Milho 200; o custo sem safra não entra em nenhuma.
    for (const custo of [
      { description: "Semente", category: "Insumos", value: 300, date: "2026-03-01", talhaoId: norte.id, safraId: soja.id },
      { description: "Adubo", category: "Insumos", value: 500, date: "2026-03-10", safraId: soja.id },
      { description: "Ureia", category: "Insumos", value: 200, date: "2026-03-12", talhaoId: sul.id, safraId: milho.id },
      { description: "Diesel geral", category: "Combustível", value: 999, date: "2026-03-15" },
    ]) assert.equal((await api("/api/custos", custo, token)).status, 201, custo.description);

    browser = await launchTestBrowser(chromium);
    const context = await browser.newContext();
    await context.addInitScript(({ token, user }) => {
      sessionStorage.setItem("agro.sessionToken", token);
      sessionStorage.setItem("agro.sessionUser", JSON.stringify(user));
    }, { token, user: login.data.usuario });
    const page = await context.newPage();
    const pageErrors = [];
    page.on("pageerror", (error) => pageErrors.push(error.message));
    // O 400 do valor acima do máximo é provocado de propósito no passo 2.
    page.on("console", (entry) => { if (entry.type() === "error" && !/status of 400/.test(entry.text())) pageErrors.push(entry.text()); });

    await page.goto(base + "/custos.html");
    await page.locator(`[data-budget-pick="${milho.id}"]`).waitFor();

    // 1. Sem orçamento: nada inventado, realizado real.
    assert.equal(await page.locator("[data-budget-season]").inputValue(), soja.id, "card abre na primeira safra em andamento");
    assert.equal(await text(page, "[data-budget-total]"), "Não definido");
    assert.match(await text(page, "[data-budget-spent]"), /800,00/);
    assert.equal(await text(page, "[data-budget-deviation]"), "-");
    assert.equal(await text(page, "[data-budget-percent]"), "-");
    assert.equal(await situation(page, soja.id), "sem-orcamento");
    assert.equal(await situation(page, milho.id), "sem-orcamento");
    assert.match(await text(page, "[data-budget-status]"), /Defina o planejado/);
    await shot(page, "sem-orcamento");
    if (shots) {
      await page.screenshot({ path: path.join(shots, "orcamento-tela.png"), fullPage: true });
      await page.setViewportSize({ width: 390, height: 844 });
      await page.locator("[data-budget-card]").scrollIntoViewIfNeeded();
      await page.locator("[data-budget-card]").screenshot({ path: path.join(shots, "orcamento-celular.png") });
      await page.setViewportSize({ width: 1280, height: 720 });
    }

    // 2. Validação local não envia nada; depois o planejado vale só com a resposta da API.
    await page.locator("[data-budget-edit]").click();
    await submit(page, "[data-budget-form]", { valor: "0", alerta: "90" });
    assert.match(await text(page, "[data-budget-form] [data-error-for='valor']"), /maior que zero/);
    assert.equal((await api(`/api/orcamentos/${soja.id}`, null, token)).data.planejado, null, "valor inválido não chegou ao servidor");

    // Valor acima do máximo passa no navegador e volta do servidor no campo.
    await submit(page, "[data-budget-form]", { valor: "2000000000", alerta: "90" });
    await page.waitForFunction(() => /1\.000\.000\.000,00/.test(document.querySelector("[data-budget-form] [data-error-for='valor']").textContent));
    assert.equal(await page.locator("[data-modal='budget-form']").getAttribute("aria-hidden"), "false", "modal continua aberto no erro");

    await submit(page, "[data-budget-form]", { valor: "1000", alerta: "90" });
    await page.waitForFunction(() => document.querySelector("[data-budget-total]").textContent.includes("1.000,00"));
    const definido = (await api(`/api/orcamentos/${soja.id}`, null, token)).data;
    assert.equal(definido.planejado, 1000);
    assert.equal(definido.alerta, 90);
    assert.equal(await text(page, "[data-budget-percent]"), "80% utilizado");
    assert.match(await text(page, "[data-budget-deviation]"), /^-R\$\s?200,00$/);
    assert.match(await text(page, "[data-budget-remaining]"), /200,00 restantes/);
    assert.equal(await situation(page, soja.id), "dentro");
    await shot(page, "dentro");

    // 3. Custo lançado pela tela: 950 de 1.000 passa do aviso de 90%.
    await page.locator("[data-open-cost-modal]").click();
    await choose(page, "[data-cost-form] [name='safraId']", soja.id);
    await submit(page, "[data-cost-form]", { description: "Defensivo", category: "Insumos", value: "150", date: "2026-04-01" });
    await page.waitForFunction(() => document.querySelector("[data-budget-spent]").textContent.includes("950,00"));
    assert.equal(await situation(page, soja.id), "proximo");
    assert.match(await text(page, "[data-budget-status]"), /Atenção: 95% do planejado já foi usado \(aviso a partir de 90%\)/);
    await shot(page, "perto-do-limite");

    // Planejado menor que o realizado: acima, com desvio positivo.
    await page.locator("[data-budget-edit]").click();
    assert.equal(await page.locator("#budget-form-title").textContent(), "Editar orçamento");
    assert.equal(await page.locator("[data-budget-form] [name='valor']").inputValue(), "1000");
    await submit(page, "[data-budget-form]", { valor: "900", alerta: "90" });
    await page.waitForFunction(() => document.querySelector("[data-budget-total]").textContent.includes("900,00"));
    assert.equal(await situation(page, soja.id), "acima");
    assert.match(await text(page, "[data-budget-deviation]"), /^\+R\$\s?50,00$/);
    assert.match(await text(page, "[data-budget-remaining]"), /50,00 acima do orçamento/);
    assert.match(await text(page, "[data-budget-status]"), /Acima do planejado em R\$\s?50,00 \(5,6%\)/);
    await shot(page, "acima");

    // 4. O filtro de safra troca o card; o realizado da outra safra é só o dela.
    await choose(page, "[data-cost-filter-season]", milho.id);
    await page.waitForFunction((id) => document.querySelector("[data-budget-season]").value === id, milho.id);
    assert.equal(await text(page, "[data-budget-total]"), "Não definido");
    assert.match(await text(page, "[data-budget-spent]"), /200,00/);

    // 5. Recarga: o planejado vem do servidor, não do navegador.
    await page.reload();
    await page.locator(`[data-budget-pick="${soja.id}"]`).waitFor();
    assert.equal(await situation(page, soja.id), "acima");
    await page.locator(`[data-budget-pick="${soja.id}"]`).click();
    assert.match(await text(page, "[data-budget-total]"), /900,00/);

    // Remover devolve a safra para "sem orçamento" e mantém o realizado.
    await page.locator("[data-budget-edit]").click();
    await page.locator("[data-budget-remove]").click();
    await page.waitForFunction(() => document.querySelector("[data-budget-total]").textContent === "Não definido");
    assert.equal((await api(`/api/orcamentos/${soja.id}`, null, token)).data.planejado, null);
    assert.match(await text(page, "[data-budget-spent]"), /950,00/);

    // Safra finalizada: consulta sim, edição não.
    for (let i = 0; i < 5; i++) assert.equal((await api(`/api/safras/${milho.id}/avancar`, {}, token)).status, 200);
    await page.reload();
    await page.locator(`[data-budget-pick="${milho.id}"]`).click();
    assert.equal(await page.locator("[data-budget-edit]").isDisabled(), true, "safra finalizada não abre edição");
    assert.match(await text(page, "[data-budget-status]"), /finalizada/);
    assert.equal((await api(`/api/orcamentos/${milho.id}`, { valor: 10 }, token, "PUT")).status, 409);

    assert.deepEqual(pageErrors, [], "erros no navegador");
    console.log("OK orçamento por safra: sem meta inventada, definir/alterar/remover pela API, alerta e desvio com custo novo, erro no campo, filtro, recarga e safra finalizada");
  } finally {
    await browser?.close();
    server.kill();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
