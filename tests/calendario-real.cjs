/* Autor: Tomas de Paula Michelon Toniato - RA: 25004211 */
/**
 * Calendário de atividades com servidor real em memória e navegador (#169, #170).
 * 1. Semana e mês mostram só as atividades do período, com contagem por dia.
 * 2. Datas "12 ago" (sem ano) caem na ocorrência mais próxima da criação.
 * 3. Selecionar um dia ou "só este período" atualiza o quadro.
 * 4. Filtros do quadro e uma atividade nova atualizam as contagens.
 * 5. Recarga mantém visão, período e dia escolhidos.
 */
const assert = require("node:assert/strict");
const path = require("node:path");
const { spawn } = require("node:child_process");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const { launchTestBrowser } = require("./browser-launch.cjs");

const base = "http://127.0.0.1:8080";
const classpath = `bin${process.platform === "win32" ? ";" : ":"}lib/*`;
const MESES = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"];

// Datas no fuso da fazenda, como no servidor e na tela.
function hoje() {
  const partes = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(new Date());
  return ["year", "month", "day"].map((chave) => partes.find((parte) => parte.type === chave).value).join("-");
}
function somar(iso, dias) {
  const data = new Date(iso + "T12:00:00Z");
  data.setUTCDate(data.getUTCDate() + dias);
  return data.toISOString().slice(0, 10);
}
const diaDaSemana = (iso) => (new Date(iso + "T12:00:00Z").getUTCDay() + 6) % 7; // segunda = 0
const prototipo = (iso) => `${Number(iso.slice(8))} ${MESES[Number(iso.slice(5, 7)) - 1]}`;

async function choose(page, selector, value) {
  await page.evaluate(({ selector, value }) => {
    const select = document.querySelector(selector);
    select.value = value;
    select.dispatchEvent(new Event("change", { bubbles: true }));
  }, { selector, value });
}

async function submit(page, selector, values) {
  await page.evaluate(({ selector, values }) => {
    const form = document.querySelector(selector);
    for (const [name, value] of Object.entries(values)) {
      form.elements[name].value = value;
      form.elements[name].dispatchEvent(new Event("change", { bubbles: true }));
    }
    form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
  }, { selector, values });
}

// Com UI_SCREENSHOTS=<pasta>, guarda imagens para revisão visual.
async function foto(page, nome) {
  if (!process.env.UI_SCREENSHOTS) return;
  require("node:fs").mkdirSync(process.env.UI_SCREENSHOTS, { recursive: true });
  await page.screenshot({ path: path.join(process.env.UI_SCREENSHOTS, nome + ".png") });
}

const visiveis = (page) => page.evaluate(() => [...document.querySelectorAll(".kanban-card")].filter((card) => !card.hidden).map((card) => card.dataset.activity).sort());
const contagem = (page, iso) => page.evaluate((iso) => document.querySelector(`[data-calendar-date="${iso}"] .week-day-count`)?.textContent, iso);

// Espera a consulta terminar e o dia mostrar a contagem esperada.
async function contar(page, iso, total) {
  await page.waitForFunction(({ iso, total }) => document.querySelector("[data-calendar-message]").textContent === ""
    && document.querySelector(`[data-calendar-date="${iso}"] .week-day-count`)?.textContent === String(total), { iso, total });
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
      name: "Operador Calendário", email: "calendario@agro.test", phone: "(19) 98888-3333",
      password: "Senha@123", confirm: "Senha@123", farm: "Fazenda Calendário", area: "90 ha", terms: true,
    });
    await api("/api/auth/confirm-email", { token: serverLog.match(/token: ([a-zA-Z0-9-]+)/)[1] });
    const login = await api("/api/auth/login", { email: "calendario@agro.test", password: "Senha@123" });
    assert.ok(login.ok, "login");
    const token = login.data.token;
    for (const [nome, cultura] of [["T-01 Norte", "Soja"], ["T-02 Sul", "Milho"]])
      assert.ok((await api("/api/talhoes", { nome, cultura, hectares: 40, status: "Ativo", safra: "2025/26" }, token)).ok);

    // Semana atual (segunda a domingo) e a seguinte.
    const segunda = somar(hoje(), -diaDaSemana(hoje()));
    const terca = somar(segunda, 1), quarta = somar(segunda, 2), domingo = somar(segunda, 6), proxima = somar(segunda, 7);
    const criar = async (nome, tipo, talhao, data) => assert.equal((await api("/api/atividades", { nome, tipo, talhao, data }, token)).status, 201, nome);
    await criar("Plantio", "Aplicação", "T-01 Norte", segunda);
    await criar("Irrigar", "Irrigação", "T-02 Sul", segunda);
    await criar("Protótipo", "Aplicação", "T-01 Norte", prototipo(quarta));
    await criar("Colher", "Colheita", "T-01 Norte", domingo);
    await criar("Revisar", "Manutenção", "T-01 Norte", proxima);

    browser = await launchTestBrowser(chromium);
    const context = await browser.newContext();
    await context.addInitScript(({ token, user }) => {
      sessionStorage.setItem("agro.sessionToken", token);
      sessionStorage.setItem("agro.sessionUser", JSON.stringify(user));
    }, { token, user: login.data.usuario });
    const page = await context.newPage();
    const pageErrors = [];
    page.on("pageerror", (error) => pageErrors.push(error.message));

    await page.goto(base + "/atividades.html");
    await page.waitForFunction(() => document.querySelectorAll(".kanban-card").length === 5);

    // 1. Semana atual: contagens reais por dia, inclusive a data sem ano.
    await contar(page, segunda, 2);
    assert.equal(await contagem(page, quarta), "1", "12 ago cai nesta semana");
    assert.equal(await contagem(page, domingo), "1");
    assert.equal(await contagem(page, terca), "0");
    assert.equal(await page.locator("[data-metric='total']").textContent(), "4", "próxima semana fica de fora");
    assert.match(await page.locator("[data-calendar-title]").textContent(), /^Semana \d+$/);
    const resumo = Object.fromEntries(await page.locator("[data-calendar-summary] div").evaluateAll((linhas) => linhas.map((linha) => [linha.querySelector("dt").textContent, linha.querySelector("dd").textContent])));
    assert.deepEqual([resumo["Aplicação"], resumo["Irrigação"], resumo["Colheita"], resumo["Manutenção"]], ["2", "1", "1", "0"]);
    await foto(page, "calendario-semana");
    console.log("OK semana com contagens reais e data sem ano");

    // 2. Dia selecionado filtra o quadro; clicar de novo volta.
    await page.locator(`[data-calendar-date="${segunda}"]`).click();
    assert.deepEqual(await visiveis(page), ["Irrigar", "Plantio"]);
    await page.locator(`[data-calendar-date="${segunda}"]`).click();
    assert.equal((await visiveis(page)).length, 5);

    // 3. Só este período: a atividade da próxima semana sai do quadro.
    await page.locator("[data-calendar-only-period]").check();
    assert.deepEqual(await visiveis(page), ["Colher", "Irrigar", "Plantio", "Protótipo"]);
    await page.locator("[data-calendar-next]").click();
    await contar(page, proxima, 1);
    assert.deepEqual(await visiveis(page), ["Revisar"]);
    console.log("OK seleção de dia e de período atualiza o quadro");

    // 4. Mês: todas as atividades do mês corrente.
    await page.locator("[data-calendar-today]").click();
    await contar(page, segunda, 2);
    await page.locator("[data-calendar-view='mes']").click();
    const mes = hoje().slice(0, 7);
    const doMes = [segunda, segunda, quarta, domingo, proxima].filter((iso) => iso.startsWith(mes)).length;
    await page.waitForFunction((total) => document.querySelector("[data-calendar-message]").textContent === ""
      && document.querySelector("[data-metric='total']").textContent === String(total), doMes);
    await foto(page, "calendario-mes");
    const diasNoMes = new Date(Date.UTC(Number(mes.slice(0, 4)), Number(mes.slice(5)), 0)).getUTCDate();
    assert.equal(await page.locator(".month-day").count(), diasNoMes);
    await page.locator("[data-calendar-view='semana']").click();
    await contar(page, segunda, 2);
    console.log("OK visão mensal");

    // 5. Filtro do quadro e atividade nova atualizam as contagens.
    await page.locator("[data-calendar-only-period]").uncheck();
    await choose(page, "[data-filter-tipo]", "Aplicação");
    await contar(page, segunda, 1);
    assert.equal(await page.locator("[data-metric='total']").textContent(), "2");
    await choose(page, "[data-filter-tipo]", "");
    await contar(page, segunda, 2);
    await page.waitForFunction(() => document.querySelector('[name=talhao] option[value="T-02 Sul"]'));
    await page.locator('[data-open-modal="activity-form"]').first().click();
    await submit(page, "[data-activity-form]", { nome: "Adubar", tipo: "Aplicação", talhao: "T-02 Sul", data: terca });
    await contar(page, terca, 1);
    assert.equal(await page.locator("[data-metric='total']").textContent(), "5");
    console.log("OK filtros e atividade nova recalculam o calendário");

    // 6. Recarga mantém visão, período e dia.
    await page.locator(`[data-calendar-date="${terca}"]`).click();
    assert.deepEqual(await visiveis(page), ["Adubar"]);
    await page.reload();
    await page.waitForFunction(() => document.querySelectorAll(".kanban-card").length === 6);
    await contar(page, terca, 1);
    assert.equal(await page.locator(`[data-calendar-date="${terca}"]`).getAttribute("aria-pressed"), "true");
    assert.deepEqual(await visiveis(page), ["Adubar"]);
    console.log("OK recarga mantém o contexto");

    // 7. Dia na URL fora do período é descartado (não zera o quadro).
    await page.goto(`${base}/atividades.html?visao=semana&data=${segunda}&dia=${proxima}`);
    await page.waitForFunction(() => document.querySelector("[data-calendar-message]").textContent === "");
    await contar(page, segunda, 2);
    assert.equal(await page.locator("[data-calendar-days] [aria-pressed='true']").count(), 0, "dia fora do período não fica selecionado");
    assert.ok((await visiveis(page)).length >= 5, "quadro não fica vazio por dia inválido");
    console.log("OK dia fora do período é limpo");

    assert.deepEqual(pageErrors, [], "sem erros de script");
    console.log("calendario-real: todas as verificações passaram");
  } catch (error) {
    console.error(error.stack || error);
    process.exitCode = 1;
  } finally {
    if (browser) await browser.close();
    server.kill();
  }
})();
