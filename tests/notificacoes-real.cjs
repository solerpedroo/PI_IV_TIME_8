/* Autor secundário: Pedro Henrique Contardi Soler - 25005592 */
/**
 * E2E das issues #26 / #166–#168: alertas reais, navegação e leitura persistente.
 */
const assert = require("node:assert/strict");
const path = require("node:path");
const { spawn } = require("node:child_process");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const { launchTestBrowser } = require("./browser-launch.cjs");

const base = "http://127.0.0.1:8080";
const cpSeparator = process.platform === "win32" ? ";" : ":";
const classpath = `bin${cpSeparator}lib/*`;

function hojeIso() {
  const agora = new Date();
  return [
    agora.getFullYear(),
    String(agora.getMonth() + 1).padStart(2, "0"),
    String(agora.getDate()).padStart(2, "0"),
  ].join("-");
}

function ontemIso() {
  const data = new Date();
  data.setDate(data.getDate() - 2);
  return [
    data.getFullYear(),
    String(data.getMonth() + 1).padStart(2, "0"),
    String(data.getDate()).padStart(2, "0"),
  ].join("-");
}

(async () => {
  try {
    if ((await fetch(base + "/health")).ok) {
      console.error("A porta 8080 já está em uso.");
      process.exit(1);
    }
  } catch {
    // livre
  }

  const server = spawn("java", ["-cp", classpath, "Servidor", "8080", "--memoria"], {
    cwd: path.join(__dirname, "../backend/servidor"),
    env: { ...process.env, SMTP_HOST: "", SMTP_USERNAME: "", SMTP_PASSWORD: "", SMTP_FROM: "", SMTP_PORT: "" },
  });
  let log = "";
  server.stdout.on("data", (chunk) => { log += chunk.toString(); });
  server.stderr.on("data", (chunk) => { log += chunk.toString(); });

  let browser;
  try {
    for (let i = 0; ; i++) {
      try { if ((await fetch(base + "/health")).ok) break; } catch {}
      if (i === 60 || server.exitCode !== null) throw new Error("Servidor não iniciou:\n" + log);
      await new Promise((r) => setTimeout(r, 100));
    }

    const api = async (endpoint, body, token, method = null) => {
      const verb = method || (body ? "POST" : "GET");
      const res = await fetch(base + endpoint, {
        method: verb,
        headers: {
          "Content-Type": "application/json",
          ...(token ? { Authorization: "Bearer " + token } : {}),
        },
        body: body ? JSON.stringify(body) : undefined,
      });
      return { status: res.status, data: await res.json().catch(() => ({})) };
    };

    await api("/api/auth/register", {
      name: "Operador Notificacoes", email: "notif@agro.test", phone: "(19) 98888-2222",
      password: "Senha@123", confirm: "Senha@123", farm: "Fazenda Notif", area: "40 ha", terms: true,
    });
    for (let i = 0; i < 50 && !/token: ([\w-]+)/.test(log); i++) await new Promise((r) => setTimeout(r, 100));
    const tokenEmail = log.match(/token: ([\w-]+)/)?.[1];
    assert.ok(tokenEmail);
    assert.equal((await api("/api/auth/confirm-email", { token: tokenEmail })).status, 200);
    const login = await api("/api/auth/login", { email: "notif@agro.test", password: "Senha@123" });
    assert.equal(login.status, 200);
    const token = login.data.token;

    const talhao = await api("/api/talhoes", { nome: "T-Notif", cultura: "Soja", hectares: 12, status: "Ativo" }, token);
    assert.equal(talhao.status, 201);

    const atrasada = await api("/api/atividades", {
      nome: "Irrigação atrasada", tipo: "Irrigação", talhao: "T-Notif", data: ontemIso(), status: "Agendada",
    }, token);
    assert.equal(atrasada.status, 201);

    const hoje = await api("/api/atividades", {
      nome: "Manejo de hoje", tipo: "Manejo", talhao: "T-Notif", data: hojeIso(), status: "Pendente",
    }, token);
    assert.equal(hoje.status, 201);

    const insumo = await api("/api/insumos", {
      nome: "NPK Notif", categoria: "Fertilizante", unidade: "kg", saldo: 2, minimo: 10, custoUnitario: 1,
    }, token);
    assert.equal(insumo.status, 201);

    const ocorrencia = await api("/api/ocorrencias", {
      titulo: "Praga no talhão", tipo: "Praga", prioridade: "Alta",
      data: hojeIso(), talhaoId: talhao.data.id, descricao: "Monitoramento inicial",
    }, token);
    assert.equal(ocorrencia.status, 201);

    browser = await launchTestBrowser(chromium);
    const page = await browser.newPage();
    await page.goto(base + "/login.html");
    await page.evaluate((session) => {
      sessionStorage.setItem("agro.sessionToken", session.token);
      sessionStorage.setItem("agro.sessionUser", JSON.stringify(session.usuario));
    }, { token, usuario: login.data.usuario });

    const waitAlert = (kind) => page.waitForSelector(`.notif-item[data-notif-kind="${kind}"]`, { state: "attached" });
    const openBell = async () => {
      await page.locator('[data-popover-trigger][aria-controls="notif-panel"]').click();
      await page.waitForSelector("#notif-panel.is-open, [data-popover].is-open, .popover[aria-hidden='false'], #notif-panel:not([hidden])", { timeout: 5000 }).catch(() => {});
    };

    await page.goto(base + "/dashboard.html");
    await waitAlert("stock");
    await waitAlert("atraso");
    await waitAlert("hoje");
    await waitAlert("ocorrencia");
    assert.equal(await page.locator(".notif-item.is-unread").count(), 4);

    // #167 — clique abre o registro e consome a leitura.
    await openBell();
    await page.locator('.notif-item[data-notif-kind="stock"]').click({ force: true });
    await page.waitForURL(/insumos\.html\?insumoId=/);
    await page.waitForSelector('[data-modal="produto-detalhe"].is-open');
    assert.ok((await page.locator("[data-detalhe-title]").textContent()).includes("NPK Notif"));

    await page.goto(base + "/dashboard.html");
    await waitAlert("atraso");
    assert.equal(await page.locator('.notif-item[data-notif-kind="stock"].is-unread').count(), 0);

    await openBell();
    await page.locator('.notif-item[data-notif-kind="atraso"]').click({ force: true });
    await page.waitForURL(/atividades\.html\?atividadeId=/);
    await page.waitForSelector("[data-modal='activity-detail'].is-open");

    await page.goto(base + "/dashboard.html");
    await waitAlert("hoje");
    await openBell();
    await page.locator('.notif-item[data-notif-kind="hoje"]').click({ force: true });
    await page.waitForURL(/atividades\.html\?hoje=1/);
    await page.waitForSelector('.kanban-card[data-activity="Manejo de hoje"]');

    await page.goto(base + "/dashboard.html");
    await waitAlert("ocorrencia");
    await openBell();
    await page.locator('.notif-item[data-notif-kind="ocorrencia"]').click({ force: true });
    await page.waitForURL(/ocorrencias\.html\?ocorrenciaId=/);
    await page.waitForFunction(() => document.body.innerText.includes("Praga no talhão"));

    // #168 — marcar todas e sobreviver ao reload.
    await page.goto(base + "/dashboard.html");
    await page.waitForSelector(".notif-item", { state: "attached" });
    await openBell();
    await page.locator("[data-notif-mark-all]").click({ force: true });
    assert.equal(await page.locator(".notif-item.is-unread").count(), 0);
    await page.reload();
    await page.waitForSelector(".notif-item", { state: "attached" });
    assert.equal(await page.locator(".notif-item.is-unread").count(), 0);

    // Preferência desligada remove a família de estoque.
    assert.equal((await api("/api/perfil/preferencias", {
      stock: false, weather: true, activities: true, occurrences: true, reports: false,
    }, token, "PUT")).status, 200);
    await page.reload();
    await waitAlert("atraso");
    assert.equal(await page.locator('.notif-item[data-notif-kind="stock"]').count(), 0);
    assert.ok(await page.locator('.notif-item[data-notif-kind="ocorrencia"]').count() >= 1);

    // Sem as preferências confirmadas, o painel falha fechado para não reativar
    // uma família que a conta já desabilitou.
    await page.route("**/api/perfil", (route) => route.fulfill({
      status: 503, contentType: "application/json", body: JSON.stringify({ error: "Indisponível" }),
    }));
    await page.reload();
    await page.waitForFunction(() => document.querySelector("[data-notif-empty]")?.textContent.includes("Não foi possível carregar"));
    assert.equal(await page.locator(".notif-item").count(), 0);
    await page.unroute("**/api/perfil");

    console.log("OK: quatro alertas, navegação, leitura persistente e preferências.");
  } finally {
    if (browser) await browser.close();
    if (server.exitCode === null) {
      const ended = new Promise((resolve) => server.once("exit", resolve));
      server.stdin.write("desativar\n");
      await ended;
    }
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
