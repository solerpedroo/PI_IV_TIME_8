/* Autor: Pedro André do Carmo Chavier - RA: 25018639 */
/**
 * Manutenção de equipamentos com servidor real em memória e navegador (#235, #236, #237).
 * Antes de rodar, suba o serversocket na porta 3000 (a API que este teste sobe procura o socket lá):
 *   sh backend/serversocket/compilar.sh && (cd backend/serversocket && java -cp bin Servidor 3000 &)
 *   sh backend/api/compilar.sh
 *   PLAYWRIGHT_MODULE=playwright node tests/manutencao-real.cjs
 * 1. Sem equipamentos: métricas "-" viram 0 depois da API; listas vazias com estado vazio.
 * 2. Trator cadastrado pela tela; nome repetido volta erro no campo.
 * 3. Duas preventivas (vencida ontem e em 3 dias): a agenda mostra atrasada e próxima.
 * 4. A vencida registrada como feita com valor e talhão: toast só depois do servidor, a agenda tira a
 *    vencida e mostra a próxima gerada; em Custos aparece o custo em Maquinário com selo "Manutenção",
 *    sem menu de editar, e o atalho volta para manutencao.html?equipamentoId=... com o histórico aberto.
 * 5. Corretiva sem valor: nenhum custo novo.
 * 6. Editar o valor atualiza o custo; excluir com confirmação tira o custo.
 * 7. Recarga mantém tudo (vem do servidor). 8. Nenhum erro no console fora dos 409 provocados.
 * Com UI_SCREENSHOTS=<pasta> grava a tela (desktop e 390 px).
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

function dia(deslocamento) {
  const hoje = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
  const data = new Date(hoje + "T12:00:00Z");
  data.setUTCDate(data.getUTCDate() + deslocamento);
  return data.toISOString().slice(0, 10);
}
const br = (iso) => iso.split("-").reverse().join("/");

async function shot(page, name) {
  if (!shots) return;
  fs.mkdirSync(shots, { recursive: true });
  await page.locator("main").screenshot({ path: path.join(shots, `manutencao-${name}.png`) });
}

async function abrirManutencao(page, consulta = "") {
  await page.goto(base + "/manutencao.html" + consulta);
  await page.waitForFunction(() => document.querySelector("[data-maint-metric='ativos']").textContent !== "-");
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
    cwd: path.join(__dirname, "../backend/api"),
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
      return { status: res.status, ok: res.ok, data: res.status === 204 ? {} : await res.json().catch(() => ({})) };
    };

    await api("/api/auth/register", {
      name: "Operador Oficina", email: "oficina@agro.test", phone: "(19) 98888-6666",
      password: "Senha@123", confirm: "Senha@123", farm: "Fazenda Oficina", area: "60 ha", terms: true,
    });
    await api("/api/auth/confirm-email", { token: serverLog.match(/token: ([a-zA-Z0-9-]+)/)[1] });
    const login = await api("/api/auth/login", { email: "oficina@agro.test", password: "Senha@123" });
    assert.ok(login.ok, "login");
    const token = login.data.token;
    const norte = await api("/api/talhoes", { nome: "T-01 Norte", cultura: "Soja", hectares: 40, status: "Ativo", safra: "2025/26" }, token);
    assert.equal(norte.status, 201, "talhão criado (o serversocket precisa estar na porta 3000)");
    const custosDeManutencao = async () => (await api("/api/custos", null, token)).data.filter((c) => c.origem?.tipo === "manutencao");

    browser = await launchTestBrowser(chromium);
    const context = await browser.newContext();
    await context.addInitScript(({ token, user }) => {
      sessionStorage.setItem("agro.sessionToken", token);
      sessionStorage.setItem("agro.sessionUser", JSON.stringify(user));
    }, { token, user: login.data.usuario });
    const page = await context.newPage();
    const pageErrors = [];
    page.on("pageerror", (error) => pageErrors.push(error.message));
    // 409 provocado de propósito (nome repetido).
    page.on("console", (entry) => { if (entry.type() === "error" && !/status of 409/.test(entry.text())) pageErrors.push(entry.text()); });

    // 1. "-" até a API responder; depois 0 e estados vazios.
    await page.route("**/api/manutencao/resumo", async (route) => { await new Promise((r) => setTimeout(r, 2500)); await route.continue(); });
    await page.goto(base + "/manutencao.html", { waitUntil: "domcontentloaded" });
    await page.locator("[data-maint-metric='ativos']").waitFor();
    assert.equal(await page.locator("[data-maint-metric='gasto']").textContent(), "-", "sem número inventado antes da API");
    await page.waitForFunction(() => document.querySelector("[data-maint-metric='ativos']").textContent === "0");
    await page.unroute("**/api/manutencao/resumo");
    assert.match(await page.locator("[data-maint-metric='gasto']").textContent(), /R\$\s?0,00/);
    assert.equal(await page.locator("[data-maint-metric='atrasadas']").textContent(), "0");
    assert.equal(await page.locator("[data-equipment-empty]").isVisible(), true, "estado vazio de equipamentos");
    assert.equal(await page.locator("[data-agenda-empty]").isVisible(), true, "estado vazio da agenda");
    assert.equal(await page.locator(".sidebar-link.is-active").getAttribute("href"), "manutencao.html");
    await shot(page, "vazio");

    // 2. Trator pela tela; nome repetido volta no campo.
    await page.locator("[data-open-equipment]").click();
    await page.fill("#equipment-nome", "Trator 01");
    await page.selectOption("#equipment-tipo", "Trator");
    await page.fill("#equipment-marca", "John Deere");
    await page.fill("#equipment-modelo", "6110J");
    await page.fill("#equipment-ano", "2020");
    await page.fill("#equipment-horimetro", "1200");
    await page.locator("[data-equipment-form] button[type='submit']").click();
    await page.locator("[data-equipment-table] tbody tr").first().waitFor();
    assert.equal(await page.locator("[data-modal='equipment-form']").getAttribute("aria-hidden"), "true");
    const tratorId = await page.locator("[data-equipment-table] tbody tr").first().getAttribute("data-equipment-id");
    assert.equal((await api("/api/equipamentos", null, token)).data[0].nome, "Trator 01", "gravado no servidor");

    await page.locator("[data-open-equipment]").click();
    await page.fill("#equipment-nome", "TRATOR 01");
    await page.selectOption("#equipment-tipo", "Trator");
    await page.locator("[data-equipment-form] button[type='submit']").click();
    await page.waitForFunction(() => /Já existe um equipamento/.test(document.querySelector("[data-equipment-form] [data-error-for='nome']").textContent));
    assert.equal(await page.locator("[data-modal='equipment-form']").getAttribute("aria-hidden"), "false", "modal continua aberto");
    await page.locator("[data-modal='equipment-form'] [data-close-modal]").click();
    assert.equal((await api("/api/equipamentos", null, token)).data.length, 1, "nada duplicado");

    // 3. Duas preventivas: vencida ontem (a cada 90 dias) e em 3 dias.
    const programar = async (descricao, data, intervalo) => {
      await page.locator("[data-open-preventive]").click();
      await page.selectOption("#preventive-equipamento", tratorId);
      await page.fill("#preventive-descricao", descricao);
      await page.fill("#preventive-data", data);
      if (intervalo) await page.fill("#preventive-intervalo", String(intervalo));
      await page.locator("[data-preventive-form] button[type='submit']").click();
      await page.locator("[data-agenda-table] tbody tr", { hasText: descricao }).waitFor();
    };
    await programar("Troca de óleo do motor", dia(-1), 90);
    await programar("Filtro de ar", dia(3));
    const atrasada = page.locator("[data-agenda-table] tbody tr[data-situacao='atrasada']");
    assert.equal(await atrasada.count(), 1, "uma atrasada");
    assert.match(await atrasada.textContent(), /Troca de óleo do motor/);
    assert.equal(await page.locator("[data-agenda-table] tbody tr[data-situacao='proxima']").count(), 1, "uma próxima");
    await page.waitForFunction(() => document.querySelector("[data-maint-metric='atrasadas']").textContent === "1");
    assert.equal(await page.locator("[data-maint-metric='proximas']").textContent(), "1");
    await page.locator("[data-agenda-filter='atrasada']").click();
    assert.equal(await page.locator("[data-agenda-table] tbody tr").count(), 1, "filtro de atrasadas");
    await page.locator("[data-agenda-filter='todas']").click();
    await shot(page, "agenda");

    // 4. Registrar a vencida como feita, com valor e talhão.
    const vencidaId = await atrasada.getAttribute("data-preventive-id");
    await atrasada.locator("[data-do-preventive]").click();
    await page.locator("[data-modal='intervention-form'].is-open").waitFor();
    assert.equal(await page.locator("#intervention-tipo").inputValue(), "preventiva");
    assert.equal(await page.locator("#intervention-equipamento").isDisabled(), true, "equipamento travado");
    await page.fill("#intervention-valor", "350.50");
    await page.fill("#intervention-horimetro", "1300");
    await page.selectOption("#intervention-talhao", norte.data.id);
    await page.locator("[data-intervention-form] button[type='submit']").click();
    await page.locator("[data-toast-viewport]", { hasText: "Intervenção registrada" }).waitFor();
    const custos1 = await custosDeManutencao();
    assert.equal(custos1.length, 1, "o toast só veio depois de o servidor gravar o custo");
    assert.equal(custos1[0].category, "Maquinário");
    assert.equal(custos1[0].value, 350.5);
    assert.equal(custos1[0].talhaoId, norte.data.id);
    await page.waitForFunction((id) => !document.querySelector(`[data-agenda-table] tr[data-preventive-id="${id}"]`), vencidaId);
    const gerada = page.locator("[data-agenda-table] tbody tr", { hasText: br(dia(89 + 1)) });
    assert.match(await gerada.textContent(), /Troca de óleo do motor/, "próxima gerada pelo intervalo de 90 dias");
    assert.equal(await gerada.getAttribute("data-situacao"), "futura");
    await page.waitForFunction(() => /350,50/.test(document.querySelector("[data-maint-metric='gasto']").textContent));
    assert.match(await page.locator("[data-equipment-table] tbody tr").first().textContent(), /1\.300 h/, "horímetro atualizado");

    await page.goto(base + "/custos.html");
    const linhaCusto = page.locator("[data-cost-table] tbody tr", { hasText: "Manutenção preventiva: Trator 01" });
    await linhaCusto.waitFor();
    assert.match(await linhaCusto.textContent(), /Maquinário/);
    assert.equal(await linhaCusto.locator(".cost-origin").textContent(), "Manutenção", "selo de manutenção");
    assert.equal(await linhaCusto.locator("[data-edit-cost]").count(), 0, "sem menu de editar");
    assert.equal(await linhaCusto.locator(".cost-origin-link").getAttribute("href"), `manutencao.html?equipamentoId=${tratorId}`);
    await linhaCusto.locator(".cost-origin-link").click();
    await page.waitForURL(/manutencao\.html\?equipamentoId=/);
    await page.locator("[data-modal='equipment-history'].is-open").waitFor();
    await page.locator("[data-history-table] tbody tr").first().waitFor();
    assert.match(await page.locator("#history-title").textContent(), /Trator 01/);
    assert.match(await page.locator("[data-history-table] tbody").textContent(), /R\$\s?350,50/);
    await shot(page, "historico");
    await page.locator("[data-modal='equipment-history'] [data-close-modal]").click();

    // 5. Corretiva sem valor: nenhum custo novo.
    await page.locator("[data-open-intervention]").click();
    await page.selectOption("#intervention-equipamento", tratorId);
    await page.selectOption("#intervention-tipo", "corretiva");
    await page.fill("#intervention-descricao", "Ajuste do freio");
    await page.locator("[data-intervention-form] button[type='submit']").click();
    await page.locator("[data-toast-viewport]", { hasText: "Sem valor: nenhum custo lançado" }).waitFor();
    assert.equal((await custosDeManutencao()).length, 1, "corretiva sem valor não cria custo");

    // 6. Editar o valor e excluir com confirmação.
    await page.locator(`[data-history-equipment="${tratorId}"]`).click();
    const linhaPreventiva = page.locator("[data-history-table] tbody tr", { hasText: "Troca de óleo do motor" });
    await linhaPreventiva.waitFor();
    assert.equal(await page.locator("[data-history-table] tbody tr").count(), 2);
    await linhaPreventiva.locator("[data-edit-intervention]").click();
    await page.locator("[data-modal='intervention-form'].is-open").waitFor();
    assert.equal(await page.locator("#intervention-valor").inputValue(), "350.5");
    await page.fill("#intervention-valor", "410.25");
    await page.locator("[data-intervention-form] button[type='submit']").click();
    await page.locator("[data-toast-viewport]", { hasText: "Intervenção atualizada" }).waitFor();
    const custos2 = await custosDeManutencao();
    assert.equal(custos2.length, 1);
    assert.equal(custos2[0].value, 410.25, "custo atualizado pela intervenção");
    assert.equal(custos2[0].id, custos1[0].id, "o mesmo lançamento, sem duplicar");

    await page.locator("[data-history-table] tbody tr", { hasText: "Troca de óleo do motor" }).locator("[data-delete-intervention]").click();
    await page.locator("[data-modal='intervention-delete'].is-open").waitFor();
    assert.match(await page.locator("[data-intervention-delete-desc]").textContent(), /O custo de R\$\s?410,25 lançado em Custos também será excluído\./);
    await page.locator("[data-confirm-intervention-delete]").click();
    await page.locator("[data-toast-viewport]", { hasText: "Intervenção excluída" }).waitFor();
    assert.equal((await custosDeManutencao()).length, 0, "o custo saiu junto");
    await page.waitForFunction(() => document.querySelectorAll("[data-history-table] tbody tr").length === 1);
    await page.locator("[data-modal='equipment-history'] [data-close-modal]").click();
    await page.waitForFunction((id) => document.querySelector(`[data-agenda-table] tr[data-preventive-id="${id}"]`)?.dataset.situacao === "atrasada", vencidaId);

    // 7. Recarga: tudo vem do servidor.
    await abrirManutencao(page);
    await page.locator("[data-agenda-table] tbody tr").first().waitFor();
    assert.equal(await page.locator("[data-equipment-table] tbody tr").count(), 1);
    assert.equal(await page.locator("[data-agenda-table] tbody tr").count(), 2, "atrasada de volta e a próxima");
    assert.match(await page.locator("[data-maint-metric='gasto']").textContent(), /R\$\s?0,00/);
    assert.match(await page.locator("[data-equipment-table] tbody tr").first().textContent(), /Ajuste do freio/, "última intervenção na lista");
    await shot(page, "final");

    if (shots) {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.locator("main").screenshot({ path: path.join(shots, "manutencao-celular.png") });
    }

    // 8. Console limpo.
    assert.deepEqual(pageErrors, [], "erros no navegador");
    console.log("OK manutenção: métricas sem inventar, cadastro e nome repetido, agenda, preventiva feita com custo em Maquinário, selo e atalho em Custos, histórico, corretiva sem custo, edição e exclusão do custo, recarga");
  } finally {
    await browser?.close();
    server.kill();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
