/* Autor: Tomas de Paula Michelon Toniato - RA: 25004211 */
/**
 * Colheita e produtividade com servidor real em memória e navegador (#222, #225).
 * 1. Sem colheita a produtividade não é inventada; safra antes do plantio não recebe colheita.
 * 2. Registro pela tela só aparece depois da confirmação da API; erro do servidor volta no campo.
 * 3. Produtividade (kg/ha e sc/ha) e comparativo entre dois ciclos com a área real.
 * 4. Edição e exclusão confirmadas pela API; recarga mantém os dados do servidor.
 * 5. Safra finalizada pela própria tela fica somente leitura.
 * Com UI_SCREENSHOTS=<pasta> grava a seção de colheita.
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
const cycle = (page, id, cell) => page.locator(`[data-harvest-cycle="${id}"] [data-harvest-${cell}]`).textContent();
const settled = (page) => page.waitForFunction(() => document.querySelector("[data-harvest-message]").textContent === "");

async function shot(page, name) {
  if (!shots) return;
  fs.mkdirSync(shots, { recursive: true });
  await page.locator("[data-harvest]").screenshot({ path: path.join(shots, `colheita-${name}.png`) });
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
      name: "Operador Colheita", email: "colheita@agro.test", phone: "(19) 98888-4444",
      password: "Senha@123", confirm: "Senha@123", farm: "Fazenda Colheita", area: "90 ha", terms: true,
    });
    await api("/api/auth/confirm-email", { token: serverLog.match(/token: ([a-zA-Z0-9-]+)/)[1] });
    const login = await api("/api/auth/login", { email: "colheita@agro.test", password: "Senha@123" });
    assert.ok(login.ok, "login");
    const token = login.data.token;

    const plot = async (nome, hectares) => (await api("/api/talhoes", { nome, cultura: "Soja", hectares, status: "Ativo", safra: "2025/26" }, token)).data;
    const norte = await plot("T-01 Norte", 40);
    const sul = await plot("T-02 Sul", 10);
    const leste = await plot("T-03 Leste", 5);
    const season = async (nome, cultura, dataInicio, dataFim, talhoes, avancos) => {
      const safra = (await api("/api/safras", { nome, cultura, dataInicio, dataFim, talhoes }, token)).data;
      for (let i = 0; i < avancos; i++) assert.equal((await api(`/api/safras/${safra.id}/avancar`, {}, token)).status, 200);
      return safra;
    };
    // Duas safras de soja nos mesmos 50 ha, já em Colheita; milho ainda em planejamento.
    const antiga = await season("Soja 24/25", "Soja", "2025-01-01", "2025-06-30", [norte.id, sul.id], 4);
    const atual = await season("Soja 25/26", "Soja", "2026-01-01", "2026-06-30", [norte.id, sul.id], 4);
    const milho = await season("Milho 26/27", "Milho", "2026-07-01", "2026-12-31", [leste.id], 0);

    // Ciclo anterior conhecido: 120 t + 500 sc = 150.000 kg em 50 ha (3.000 kg/ha = 50 sc/ha).
    assert.equal((await api("/api/colheitas", { safraId: antiga.id, talhaoId: norte.id, quantidade: 120, unidade: "t", data: "2025-04-10" }, token)).status, 201);
    assert.equal((await api("/api/colheitas", { safraId: antiga.id, talhaoId: sul.id, quantidade: 500, unidade: "sc", data: "2025-04-12" }, token)).status, 201);

    browser = await launchTestBrowser(chromium);
    const context = await browser.newContext();
    await context.addInitScript(({ token, user }) => {
      sessionStorage.setItem("agro.sessionToken", token);
      sessionStorage.setItem("agro.sessionUser", JSON.stringify(user));
    }, { token, user: login.data.usuario });
    const page = await context.newPage();
    const pageErrors = [];
    page.on("pageerror", (error) => pageErrors.push(error.message));
    // O 400 da data anterior ao início da safra é provocado de propósito no passo 2.
    page.on("console", (entry) => { if (entry.type() === "error" && !/status of 400/.test(entry.text())) pageErrors.push(entry.text()); });

    await page.goto(base + "/safras.html");
    await page.locator(`[data-harvest-cycle="${atual.id}"]`).waitFor();
    await settled(page);

    // 1. Nada inventado: ciclo sem colheita sem produtividade; milho em planejamento bloqueado.
    assert.match(await cycle(page, antiga.id, "yield-cell"), /^50 sc\/ha · 3\.000 kg\/ha$/);
    assert.equal(await cycle(page, atual.id, "yield-cell"), "Sem colheita");
    assert.equal(await cycle(page, milho.id, "yield-cell"), "Sem colheita");
    assert.match(await text(page, "[data-harvest-total]"), /150\.000 kg/);
    await page.locator("[data-new-harvest]").click();
    assert.equal(await page.locator(`[data-harvest-form] [name='safraId'] option[value="${milho.id}"]`).isDisabled(), true, "safra em planejamento não recebe colheita");
    await shot(page, "inicio");

    // 2. Validação local não envia nada; a do servidor volta no campo.
    await choose(page, "[data-harvest-form] [name='safraId']", atual.id);
    await submit(page, "[data-harvest-form]", { talhaoId: norte.id, quantidade: "", unidade: "sc", data: "2026-03-10" });
    assert.match(await text(page, "[data-harvest-form] [data-error-for='quantidade']"), /maior que zero/);
    assert.equal((await api(`/api/colheitas?safraId=${atual.id}`, null, token)).data.length, 0, "nada enviado sem quantidade");
    await submit(page, "[data-harvest-form]", { talhaoId: norte.id, quantidade: "2000", unidade: "sc", data: "2025-12-31" });
    await page.waitForFunction(() => /anterior ao início da safra/.test(document.querySelector("[data-harvest-form] [data-error-for='data']").textContent));
    assert.equal(await page.locator("[data-modal='harvest-form']").getAttribute("aria-hidden"), "false", "modal continua aberto no erro");

    // 3. Registros pela tela: 2.000 sc no Norte e 36 t no Sul.
    await submit(page, "[data-harvest-form]", { talhaoId: norte.id, quantidade: "2000", unidade: "sc", data: "2026-03-10" });
    await page.locator("[data-harvest-table] tbody tr", { hasText: "2.000 sc" }).waitFor();
    await page.locator("[data-new-harvest]").click();
    await choose(page, "[data-harvest-form] [name='safraId']", atual.id);
    await submit(page, "[data-harvest-form]", { talhaoId: sul.id, quantidade: "36", unidade: "t", data: "2026-03-12", observacao: "Talhão sul" });
    await page.locator("[data-harvest-table] tbody tr", { hasText: "Talhão sul" }).waitFor();
    const doServidor = (await api(`/api/colheitas?safraId=${atual.id}`, null, token)).data;
    assert.deepEqual(doServidor.map((item) => item.quantidadeKg).sort((a, b) => a - b), [36000, 120000], "kg gravados pelo servidor");

    // Uma terceira carga entra pela API; a recarga traz tudo do servidor.
    assert.equal((await api("/api/colheitas", { safraId: atual.id, talhaoId: norte.id, quantidade: 30000, unidade: "kg", data: "2026-03-15" }, token)).status, 201);
    await page.reload();
    await page.locator(`[data-harvest-cycle="${atual.id}"]`).waitFor();
    await settled(page);
    // 186.000 kg em 50 ha = 3.720 kg/ha = 62 sc/ha, 24% acima do ciclo anterior.
    assert.equal(await cycle(page, atual.id, "yield-cell"), "62 sc/ha · 3.720 kg/ha");
    assert.equal(await cycle(page, atual.id, "variation"), "+24%");

    await choose(page, "[data-harvest-season]", atual.id);
    await page.waitForFunction(() => document.querySelector("[data-harvest-count]").textContent === "3");
    assert.match(await text(page, "[data-harvest-total]"), /^186\.000 kg$/);
    assert.match(await text(page, "[data-harvest-total-sc]"), /3\.100 sc/);
    assert.equal(await text(page, "[data-harvest-area]"), "50 ha");
    assert.equal(await text(page, "[data-harvest-yield]"), "62 sc/ha");
    assert.equal(await text(page, "[data-harvest-yield-kg]"), "3.720 kg/ha");
    await shot(page, "safra-atual");

    // 4. Edição: Sul passa de 36 t para 30 t (180.000 kg = 60 sc/ha, +20%).
    const linhaSul = page.locator("[data-harvest-table] tbody tr", { hasText: "Talhão sul" });
    await linhaSul.locator("[data-edit-harvest]").click();
    assert.equal(await page.locator("#harvest-form-title").textContent(), "Editar colheita");
    await submit(page, "[data-harvest-form]", { quantidade: "30" });
    await page.waitForFunction(() => document.querySelector("[data-harvest-yield]").textContent === "60 sc/ha");
    assert.equal(await cycle(page, atual.id, "variation"), "+20%");

    // Exclusão com confirmação: sem a carga de 30.000 kg ficam 150.000 kg (50 sc/ha, 0%).
    await page.locator("[data-harvest-table] tbody tr", { hasText: "15/03/2026" }).locator("[data-delete-harvest]").click();
    await page.locator("[data-confirm-harvest-delete]").click();
    await page.waitForFunction(() => document.querySelector("[data-harvest-count]").textContent === "2");
    assert.equal(await text(page, "[data-harvest-yield]"), "50 sc/ha");
    assert.equal(await cycle(page, atual.id, "variation"), "0%");
    assert.equal((await api(`/api/colheitas?safraId=${atual.id}`, null, token)).data.length, 2);

    // 5. Finalizar a safra antiga pela lista de ciclos deixa as colheitas dela somente leitura.
    await choose(page, "[data-harvest-season]", antiga.id);
    await page.waitForFunction(() => [...document.querySelectorAll("[data-harvest-table] tbody tr")].length === 2 &&
      [...document.querySelectorAll("[data-harvest-table] tbody tr")].every((row) => row.textContent.includes("Soja 24/25")));
    await page.locator(`[data-advance="${antiga.id}"]`).click();
    await page.waitForFunction(() => [...document.querySelectorAll("[data-edit-harvest]")].every((button) => button.disabled));
    await page.locator("[data-new-harvest]").click();
    assert.equal(await page.locator(`[data-harvest-form] [name='safraId'] option[value="${antiga.id}"]`).isDisabled(), true, "safra finalizada fora do formulário");
    await page.locator("[data-close-harvest]").click();
    assert.equal((await api("/api/colheitas", { safraId: antiga.id, talhaoId: norte.id, quantidade: 1, unidade: "kg", data: "2025-05-01" }, token)).status, 409);
    assert.match(await cycle(page, antiga.id, "yield-cell"), /^50 sc\/ha/);
    await shot(page, "finalizada");

    if (shots) {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.locator("[data-harvest]").screenshot({ path: path.join(shots, "colheita-celular.png") });
    }

    assert.deepEqual(pageErrors, [], "erros no navegador");
    console.log("OK colheita: nada inventado, validação local e do servidor, registros pela tela, produtividade e comparativo, edição, exclusão, recarga e safra finalizada");
  } finally {
    await browser?.close();
    server.kill();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
