/* Autor: Pedro André do Carmo Chavier - RA: 25018639 */
/**
 * Georreferenciamento com servidor real em memória e navegador (#231, #232, #233).
 * Antes de rodar, suba o serversocket na porta 3000 (a API que este teste sobe procura o socket lá):
 *   sh backend/serversocket/compilar.sh && (cd backend/serversocket && java -cp bin Servidor 3000 &)
 *   sh backend/api/compilar.sh
 *   PLAYWRIGHT_MODULE=playwright node tests/georreferenciamento-real.cjs
 * 1. Sem localização nada é desenhado: mapa vazio com mensagem e os talhões em "Sem localização".
 * 2. Polígono e ponto definidos pela tela; erro do servidor (latitude 95) volta no campo.
 * 3. O mapa desenha 1 polígono e 1 ponto, mostra a área calculada e continua igual depois da recarga.
 * 4. Ocorrências da API viram marcadores (posição própria e centro do talhão).
 * 5. Em Ocorrências, a posição só aparece para o talhão com polígono; ponto fora volta erro no campo.
 * 6. Resolver a ocorrência tira o marcador (padrão: só abertas).
 * 7. Remover a localização com ocorrência marcada mostra o 409; sem ocorrência marcada, remove.
 * Com UI_SCREENSHOTS=<pasta> grava o card do mapa (desktop e 390 px).
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

const POLIGONO = "-22.905000, -47.061000\n-22.905000, -47.058000\n-22.908000, -47.058000\n-22.908000, -47.061000";

async function shot(page, name) {
  if (!shots) return;
  fs.mkdirSync(shots, { recursive: true });
  await page.locator(".plots-map-card").screenshot({ path: path.join(shots, `georreferenciamento-${name}.png`) });
}

const formas = (page, tag) => page.locator(`[data-geo-map] ${tag}[data-geo-plot]`).count();
const marcadores = (page) => page.locator("[data-geo-map] [data-geo-occurrence]").count();

async function abrirMapa(page) {
  await page.goto(base + "/areas-cultivo.html");
  await page.waitForFunction(() => {
    const texto = document.querySelector("[data-geo-message]").textContent;
    return /no mapa|Nenhum talhão com localização/.test(texto);
  });
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
      name: "Operador Mapa", email: "mapa@agro.test", phone: "(19) 98888-5555",
      password: "Senha@123", confirm: "Senha@123", farm: "Fazenda Mapa", area: "30 ha", terms: true,
    });
    await api("/api/auth/confirm-email", { token: serverLog.match(/token: ([a-zA-Z0-9-]+)/)[1] });
    const login = await api("/api/auth/login", { email: "mapa@agro.test", password: "Senha@123" });
    assert.ok(login.ok, "login");
    const token = login.data.token;

    const plot = async (nome, hectares, cultura) => {
      const res = await api("/api/talhoes", { nome, cultura, hectares, status: "Ativo", safra: "2025/26" }, token);
      assert.equal(res.status, 201, "talhão criado (o serversocket precisa estar na porta 3000)");
      return res.data;
    };
    const norte = await plot("T-01 Norte", 10, "Soja");
    const sul = await plot("T-02 Sul", 5, "Milho");

    browser = await launchTestBrowser(chromium);
    const context = await browser.newContext();
    await context.addInitScript(({ token, user }) => {
      sessionStorage.setItem("agro.sessionToken", token);
      sessionStorage.setItem("agro.sessionUser", JSON.stringify(user));
    }, { token, user: login.data.usuario });
    const page = await context.newPage();
    const pageErrors = [];
    page.on("pageerror", (error) => pageErrors.push(error.message));
    // 400 e 409 provocados de propósito (latitude 95, ponto fora, remoção com ocorrência marcada).
    page.on("console", (entry) => { if (entry.type() === "error" && !/status of 40[09]/.test(entry.text())) pageErrors.push(entry.text()); });

    // 1. Nada desenhado sem localização.
    await abrirMapa(page);
    assert.match(await page.locator("[data-geo-message]").textContent(), /Nenhum talhão com localização/);
    assert.equal(await page.locator("[data-geo-map] *").count(), 0, "SVG vazio, sem desenho de exemplo");
    assert.equal(await page.locator("[data-geo-missing] [data-geo-edit]").count(), 2, "dois talhões sem localização");
    await page.locator(`[data-plots-table] [data-geo-edit="${norte.id}"]`).waitFor({ state: "attached" });
    await shot(page, "vazio");

    // 2. Polígono do Norte: primeiro com latitude 95 (erro do servidor no campo), depois válido.
    await page.locator(`[data-geo-missing] [data-geo-edit="${norte.id}"]`).click();
    await page.locator("[data-modal='geo-form'].is-open").waitFor();
    await page.locator("[data-geo-type='poligono']").click();
    await page.fill("[data-geo-form] [name='pontos']", POLIGONO.replace("-22.905000, -47.061000", "95, -47.061"));
    await page.locator("[data-geo-form] button[type='submit']").click();
    await page.waitForFunction(() => /Latitude inválida/.test(document.querySelector("[data-geo-form] [data-error-for='pontos']").textContent));
    assert.equal(await page.locator("[data-modal='geo-form']").getAttribute("aria-hidden"), "false", "modal continua aberto no erro");
    assert.equal((await api("/api/talhoes", null, token)).data.find((t) => t.id === norte.id).localizacao, undefined, "nada gravado com erro");

    await page.fill("[data-geo-form] [name='pontos']", POLIGONO);
    await page.locator("[data-geo-form] button[type='submit']").click();
    await page.locator("[data-geo-map] polygon[data-geo-plot]").waitFor();
    assert.equal(await page.locator("[data-modal='geo-form']").getAttribute("aria-hidden"), "true", "fecha depois da confirmação");
    // ~0,003 x 0,003 grau perto de Campinas: 10,2 ha calculados contra 10 ha digitados.
    await page.waitForFunction(() => /Área calculada: 10,2 ha \(0,2 ha a mais que os 10 ha cadastrados\)/.test(document.querySelector("[data-geo-panel-meta]").textContent));
    assert.match(await page.locator("[data-geo-panel-osm]").getAttribute("href"), /openstreetmap\.org\/\?mlat=-22\.906500&mlon=-47\.059500/);

    // Ponto do Sul pela lista "Sem localização".
    await page.locator(`[data-geo-missing] [data-geo-edit="${sul.id}"]`).click();
    await page.locator("[data-modal='geo-form'].is-open").waitFor();
    assert.equal(await page.locator("[data-geo-type='ponto']").getAttribute("aria-pressed"), "true", "ponto é o padrão");
    await page.fill("[data-geo-form] [name='lat']", "-22.912000");
    await page.fill("[data-geo-form] [name='lng']", "-47.052000");
    await page.locator("[data-geo-form] button[type='submit']").click();
    await page.locator("[data-geo-map] circle[data-geo-plot]").waitFor();

    // 3. Um polígono e um ponto; a recarga traz o mesmo do servidor.
    assert.equal(await formas(page, "polygon"), 1);
    assert.equal(await formas(page, "circle"), 1);
    assert.match(await page.locator("[data-geo-missing]").textContent(), /Todos os talhões têm localização/);
    await abrirMapa(page);
    assert.equal(await formas(page, "polygon"), 1, "polígono depois da recarga");
    assert.equal(await formas(page, "circle"), 1, "ponto depois da recarga");
    const salvo = (await api("/api/talhoes", null, token)).data.find((t) => t.id === norte.id);
    assert.equal(salvo.localizacao.pontos.length, 4, "4 vértices gravados no servidor");

    // 4. Ocorrências pela API: uma dentro do polígono e outra sem posição no talhão de ponto.
    const ocorrencia = async (titulo, talhaoId, localizacao) => {
      const res = await api("/api/ocorrencias", { titulo, tipo: "Praga", prioridade: "Alta", data: "2026-10-01", descricao: "", talhaoId, ...(localizacao ? { localizacao } : {}) }, token);
      assert.equal(res.status, 201, "ocorrência " + titulo);
      return res.data;
    };
    const dentro = await ocorrencia("Lagarta no centro", norte.id, { lat: -22.9065, lng: -47.0595 });
    const noPonto = await ocorrencia("Ferrugem no Sul", sul.id, null);
    await abrirMapa(page);
    assert.equal(await marcadores(page), 2, "dois marcadores");
    assert.match(await page.locator(`[data-geo-occurrence="${dentro.id}"]`).getAttribute("href"), new RegExp(`ocorrenciaId=${dentro.id}`));
    assert.equal(await page.locator(`[data-geo-occurrence="${noPonto.id}"]`).count(), 1);
    await shot(page, "com-ocorrencias");

    // 5. Ocorrências: posição só para o talhão com polígono; ponto fora volta erro no campo.
    await page.goto(base + "/ocorrencias.html");
    await page.waitForFunction(() => !document.querySelector("[data-open-modal='occ-form']").disabled);
    await page.locator("[data-open-modal='occ-form']").click();
    await page.locator("[data-modal='occ-form'].is-open").waitFor();
    assert.equal(await page.locator("[data-occ-geo]").isHidden(), true, "sem talhão, sem bloco de posição");
    await page.selectOption("#occ-talhao", sul.id);
    assert.equal(await page.locator("[data-occ-geo]").isVisible(), true);
    assert.equal(await page.locator("[data-occ-geo-fields]").isHidden(), true, "talhão de ponto não aceita posição");
    assert.match(await page.locator("[data-occ-geo-hint]").textContent(), /não tem polígono/);
    await page.selectOption("#occ-talhao", norte.id);
    assert.equal(await page.locator("[data-occ-geo-fields]").isVisible(), true, "talhão com polígono aceita posição");
    await page.fill("#occ-titulo", "Mancha na borda");
    await page.selectOption("#occ-tipo", "Doença");
    await page.fill("[data-occ-lat]", "-22.900000");
    await page.fill("[data-occ-lng]", "-47.059000");
    await page.locator("[data-occ-form] button[type='submit']").click();
    await page.waitForFunction(() => /fora do polígono/.test(document.querySelector("[data-occ-form] [data-error-for='localizacao']").textContent));
    await page.fill("[data-occ-lat]", "-22.906000");
    await page.locator("[data-occ-form] button[type='submit']").click();
    await page.locator("[data-modal='occ-detail'].is-open").waitFor();
    assert.match(await page.locator("[data-detail-geo-text]").textContent(), /Posição: -22\.906000, -47\.059000/);
    const naTela = (await api("/api/ocorrencias", null, token)).data.find((o) => o.titulo === "Mancha na borda");
    assert.deepEqual(naTela.localizacao, { lat: -22.906, lng: -47.059 }, "posição gravada pelo servidor");

    // 6. Resolver a ocorrência do centro tira o marcador dela.
    assert.equal((await api(`/api/ocorrencias/${dentro.id}/timeline`, { versao: 1, acao: "resolucao", descricao: "Controlada" }, token)).status, 200);
    await abrirMapa(page);
    assert.equal(await marcadores(page), 2, "resolvida sai do mapa (ficam a da tela e a do Sul)");
    assert.equal(await page.locator(`[data-geo-occurrence="${dentro.id}"]`).count(), 0);

    // 7. Remover com ocorrência marcada: 409 na tela; o Sul (sem marcação) sai do mapa.
    await page.locator("[data-geo-map] polygon[data-geo-plot]").dispatchEvent("click");
    await page.locator("[data-geo-panel]:not([hidden])").waitFor();
    await page.locator("[data-geo-panel-edit]").click();
    await page.locator("[data-modal='geo-form'].is-open").waitFor();
    assert.equal(await page.locator("[data-geo-type='poligono']").getAttribute("aria-pressed"), "true", "abre com o polígono salvo");
    await page.locator("[data-geo-remove]").click();
    await page.waitForFunction(() => /marcadas neste polígono/.test(document.querySelector("[data-geo-form-error]").textContent));
    assert.equal(await page.locator("[data-modal='geo-form']").getAttribute("aria-hidden"), "false");
    await page.locator("[data-modal='geo-form'] [data-close-modal]").click();
    assert.equal(await formas(page, "polygon"), 1, "polígono mantido depois do 409");

    await page.locator("[data-geo-map] circle[data-geo-plot]").dispatchEvent("click");
    await page.locator("[data-geo-panel-edit]").click();
    await page.locator("[data-modal='geo-form'].is-open").waitFor();
    await page.locator("[data-geo-remove]").click();
    await page.waitForFunction(() => document.querySelectorAll("[data-geo-missing] [data-geo-edit]").length === 1);
    assert.equal(await formas(page, "circle"), 0, "ponto removido do mapa");
    assert.equal((await api("/api/talhoes", null, token)).data.find((t) => t.id === sul.id).localizacao, null, "localização removida no servidor");
    await shot(page, "final");

    if (shots) {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.locator(".plots-map-card").screenshot({ path: path.join(shots, "georreferenciamento-celular.png") });
    }

    assert.deepEqual(pageErrors, [], "erros no navegador");
    console.log("OK georreferenciamento: mapa vazio sem inventar, polígono e ponto pela tela, erro no campo, área calculada, recarga, marcadores, posição em Ocorrências, resolvida sai do mapa, 409 e remoção");
  } finally {
    await browser?.close();
    server.kill();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
