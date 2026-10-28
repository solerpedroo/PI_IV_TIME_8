/* Miguel — RA: 25003110 */
/**
 * Teste End-to-End de Atividades com Servidor Real e Navegador
 * --------------------------------------------------------------------------
 * Valida os critérios de aceite da subissue #140:
 * 1. Inicializa o servidor backend real em memória.
 * 2. Autentica e adquire sessão do operador.
 * 3. Cria talhões de suporte na API para a propriedade do operador.
 * 4. Criação de atividade e registro inicial do evento 0 no histórico.
 * 5. Conclusão de atividade -> bloqueio de estados terminais e registro no histórico.
 * 6. Cancelamento justificado de atividade com motivo registrado na timeline.
 * 7. Casos inválidos pertinentes (409 Conflict em transições após estado terminal).
 * 8. Persistência real comprovada após recarga completa da página (reload).
 */
const assert = require("node:assert/strict");
const path = require("node:path");
const { spawn } = require("node:child_process");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const { launchTestBrowser } = require("./browser-launch.cjs");

const base = "http://127.0.0.1:8080";
const cpSeparator = process.platform === "win32" ? ";" : ":";
const classpath = `bin${cpSeparator}lib/*`;

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

async function clickCardAction(page, name, action) {
  await page.evaluate(({ name, action }) => {
    const card = [...document.querySelectorAll(".kanban-card")].find((item) => item.dataset.activity === name);
    if (!card) throw new Error("Card ausente: " + name);
    const btn = card.querySelector(`[data-card-action="${action}"]`);
    if (!btn) throw new Error(`Ação "${action}" não encontrada no card: ${name}`);
    btn.click();
  }, { name, action });
}

(async () => {
  console.log("Iniciando validação E2E de Atividades com servidor real...");

  // Garante que a porta 8080 não tem processo residual
  try {
    const res = await fetch(base + "/health");
    if (res.ok) {
      console.error("A porta 8080 já está em uso por outro processo.");
      process.exit(1);
    }
  } catch {
    // Porta livre conforme esperado
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
        const res = await fetch(base + "/health");
        if (res.ok) break;
      } catch {}
      if (attempt === 60 || server.exitCode !== null) {
        throw new Error("Servidor backend não iniciou a tempo. Log:\n" + serverLog);
      }
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
    console.log("Servidor backend iniciado em modo memória na porta 8080.");

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
      return { status: res.status, ok: res.ok, data: await res.json().catch(() => ({})) };
    };

    // Cadastro e autenticação do operador
    await api("/api/auth/register", {
      name: "Operador Miguel",
      email: "miguel.atividades@agro.test",
      phone: "(19) 98888-1111",
      password: "Senha@123",
      confirm: "Senha@123",
      farm: "Fazenda Progresso",
      area: "120 ha",
      terms: true,
    });

    const tokenMatch = serverLog.match(/token: ([a-zA-Z0-9-]+)/);
    assert.ok(tokenMatch, "Token de confirmação entregue no log do servidor.");
    await api("/api/auth/confirm-email", { token: tokenMatch[1] });

    const loginRes = await api("/api/auth/login", {
      email: "miguel.atividades@agro.test",
      password: "Senha@123",
    });
    assert.ok(loginRes.ok, "Login efetuado com sucesso.");
    const sessionToken = loginRes.data.token;
    const sessionUser = loginRes.data.usuario;

    // Criar talhões de suporte para a propriedade
    console.log("Criando talhões de suporte via API...");
    const talhao1 = await api("/api/talhoes", {
      nome: "T-01 Norte",
      cultura: "Soja",
      hectares: 50,
      status: "Ativo",
      safra: "2025/26",
    }, sessionToken);
    assert.ok(talhao1.ok, "Talhão 1 criado.");

    const talhao2 = await api("/api/talhoes", {
      nome: "T-02 Sul",
      cultura: "Milho",
      hectares: 40,
      status: "Ativo",
      safra: "2025/26",
    }, sessionToken);
    assert.ok(talhao2.ok, "Talhão 2 criado.");

    // Inicialização do navegador e injeção da sessão
    browser = await launchTestBrowser(chromium);
    const context = await browser.newContext();
    await context.addInitScript(({ token, user }) => {
      sessionStorage.setItem("agro.sessionToken", token);
      sessionStorage.setItem("agro.sessionUser", JSON.stringify(user));
    }, { token: sessionToken, user: sessionUser });

    const page = await context.newPage();
    const pageErrors = [];
    page.on("pageerror", (err) => pageErrors.push(err.message));

    // Acessa a página de atividades servida pelo backend
    await page.goto(base + "/atividades.html");
    await page.waitForLoadState("domcontentloaded");
    assert.equal(pageErrors.length, 0, "Página carregada sem erros de script: " + pageErrors.join(", "));

    // Aguarda o carregamento dos talhões da API nos selects do formulário
    await page.waitForFunction(() => document.querySelector('[name=talhao] option[value="T-01 Norte"]'));

    // 1. Criar Atividade 1: Adubação Nitrogenada
    console.log("Passo 1: Criando atividade via modal...");
    await page.locator('[data-open-modal="activity-form"]').first().click();
    await page.waitForSelector('[data-modal="activity-form"].is-open');
    await submit(page, '[data-activity-form]', {
      nome: "Adubação Nitrogenada",
      tipo: "Aplicação",
      talhao: "T-01 Norte",
      data: "15 nov",
    });

    // Aguarda o card surgir na coluna Pendente
    const cardAdubacao = page.locator('.kanban-card[data-activity="Adubação Nitrogenada"]');
    await cardAdubacao.waitFor({ state: "visible" });
    assert.equal(await cardAdubacao.getAttribute("data-status"), "Pendente");
    console.log("OK - Atividade criada com status Pendente.");

    // Aguarda sincronização com a API e verifica histórico
    await new Promise((r) => setTimeout(r, 400));
    const listaAtividades = await api("/api/atividades", null, sessionToken);
    assert.ok(listaAtividades.ok, "GET /api/atividades retornou 200");
    const atividade1Api = listaAtividades.data.find((a) => a.nome === "Adubação Nitrogenada");
    assert.ok(atividade1Api, "Atividade persistida no banco do servidor");
    const idAtividade1 = atividade1Api.id;

    const hist1 = await api(`/api/atividades/${idAtividade1}/historico`, null, sessionToken);
    assert.ok(hist1.ok && hist1.data.length >= 1, "Histórico da atividade possui evento de criação");
    assert.equal(hist1.data[0].ordem, 0);
    assert.equal(hist1.data[0].status, "Pendente");
    console.log("OK - Evento 0 de criação confirmado no histórico.");

    // 2. Visualizar detalhes e timeline da atividade
    console.log("Passo 2: Verificando modal de detalhes e timeline...");
    await clickCardAction(page, "Adubação Nitrogenada", "detail");
    await page.waitForSelector('[data-modal="activity-detail"].is-open');
    await page.waitForSelector('[data-detail-timeline] .timeline-entry');
    const timelineText = await page.locator('[data-detail-timeline]').innerText();
    assert.ok(timelineText.includes("Atividade criada"), "Timeline exibe evento de criação da atividade");
    await page.locator('[data-modal="activity-detail"] [data-close-modal]').click();
    console.log("OK - Linha do tempo visualizada com sucesso.");

    // 3. Concluir a Atividade 1
    console.log("Passo 3: Concluindo atividade...");
    await clickCardAction(page, "Adubação Nitrogenada", "complete");
    await page.waitForSelector('[data-modal="activity-complete"].is-open');
    await page.click('[data-confirm-complete]');

    // Confere que o card migrou para a coluna Concluída
    await page.waitForFunction(() => {
      const card = document.querySelector('.kanban-card[data-activity="Adubação Nitrogenada"]');
      return card && card.dataset.status === "Concluída";
    });
    console.log("OK - Atividade marcada como Concluída no Kanban.");
    await new Promise((r) => setTimeout(r, 400));

    // Confere que no menu de ações do card não há mais 'Concluir', 'Cancelar' ou 'Editar'
    const hasEdit = await page.evaluate(() => {
      const card = [...document.querySelectorAll('.kanban-card')].find((c) => c.dataset.activity === "Adubação Nitrogenada");
      return !!card.querySelector('[data-card-action="edit"]');
    });
    const hasComplete = await page.evaluate(() => {
      const card = [...document.querySelectorAll('.kanban-card')].find((c) => c.dataset.activity === "Adubação Nitrogenada");
      return !!card.querySelector('[data-card-action="complete"]');
    });
    const hasCancel = await page.evaluate(() => {
      const card = [...document.querySelectorAll('.kanban-card')].find((c) => c.dataset.activity === "Adubação Nitrogenada");
      return !!card.querySelector('[data-card-action="cancel"]');
    });
    assert.equal(hasEdit, false, "Atividade concluída não deve ter opção de Editar");
    assert.equal(hasComplete, false, "Atividade concluída não deve ter opção de Concluir");
    assert.equal(hasCancel, false, "Atividade concluída não deve ter opção de Cancelar");

    // 4. Criar e Cancelar a Atividade 2 com justificativa
    console.log("Passo 4: Criando e cancelando segunda atividade com justificativa...");
    await page.locator('[data-open-modal="activity-form"]').first().click();
    await page.waitForSelector('[data-modal="activity-form"].is-open');
    await submit(page, '[data-activity-form]', {
      nome: "Pulverização Fungicida",
      tipo: "Aplicação",
      talhao: "T-02 Sul",
      data: "16 nov",
    });

    const cardFungicida = page.locator('.kanban-card[data-activity="Pulverização Fungicida"]');
    await cardFungicida.waitFor({ state: "visible" });

    // Cancelar a atividade via menu de ações
    await clickCardAction(page, "Pulverização Fungicida", "cancel");
    await page.waitForSelector('[data-modal="activity-cancel"].is-open');
    await page.fill('[data-cancel-reason]', "Chuva intensa inviabilizou aplicação");
    await page.click('[data-confirm-cancel]');

    // Confere que migrou para a coluna Cancelada
    await page.waitForFunction(() => {
      const card = document.querySelector('.kanban-card[data-activity="Pulverização Fungicida"]');
      return card && card.dataset.status === "Cancelada";
    });
    console.log("OK - Atividade cancelada com justificativa movida para coluna Cancelada.");

    // Aguarda persistência e verifica motivo no histórico
    await new Promise((r) => setTimeout(r, 400));
    const lista2 = await api("/api/atividades", null, sessionToken);
    const atividade2Api = lista2.data.find((a) => a.nome === "Pulverização Fungicida");
    assert.ok(atividade2Api, "Atividade 2 encontrada no backend");
    assert.equal(atividade2Api.status, "Cancelada");

    const hist2 = await api(`/api/atividades/${atividade2Api.id}/historico`, null, sessionToken);
    const eventoCancel = hist2.data.find((e) => e.status === "Cancelada" || e.acao === "cancelamento");
    assert.ok(eventoCancel, "Evento de cancelamento registrado");
    assert.equal(eventoCancel.motivo, "Chuva intensa inviabilizou aplicação");
    console.log("OK - Histórico contém evento de cancelamento com o motivo correto.");

    // 5. Validar casos inválidos pertinentes (Rejeição de operações sobre estados terminais)
    console.log("Passo 5: Validando casos inválidos e proteção de estados terminais...");
    console.log("Log do servidor até aqui:\n", serverLog);
    const ativ1NoBackend = await api(`/api/atividades/${idAtividade1}`, null, sessionToken);
    console.log("Atividade 1 no backend:", ativ1NoBackend.data);
    // Tentar concluir atividade cancelada
    const resConcluirCancelada = await api(`/api/atividades/${atividade2Api.id}/concluir`, {}, sessionToken);
    assert.equal(resConcluirCancelada.status, 409, "Servidor deve retornar 409 Conflict ao tentar concluir atividade cancelada");

    // Tentar cancelar atividade concluída
    const resCancelarConcluida = await api(`/api/atividades/${idAtividade1}/cancelar`, { motivo: "Invalido" }, sessionToken);
    assert.equal(resCancelarConcluida.status, 409, "Servidor deve retornar 409 Conflict ao tentar cancelar atividade concluída");

    // Tentar mudar status de atividade concluída para Agendada
    const resMudarStatus = await api(`/api/atividades/${idAtividade1}/status`, { status: "Agendada" }, sessionToken);
    assert.equal(resMudarStatus.status, 409, "Servidor deve retornar 409 Conflict ao tentar mudar status de atividade terminal");
    console.log("OK - Todos os casos inválidos foram devidamente rejeitados com HTTP 409.");

    // 6. Validar persistência após reload completo da página
    console.log("Passo 6: Validando persistência dos dados e estados reais após reload...");
    await page.reload();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector('.kanban-card');

    // Confere que ambas as atividades foram restauradas da API nos estados corretos
    const card1Reload = page.locator('.kanban-card[data-activity="Adubação Nitrogenada"]');
    const card2Reload = page.locator('.kanban-card[data-activity="Pulverização Fungicida"]');

    assert.equal(await card1Reload.getAttribute("data-status"), "Concluída", "Card 1 permanece Concluída após reload");
    assert.equal(await card2Reload.getAttribute("data-status"), "Cancelada", "Card 2 permanece Cancelada após reload");

    // Confere badge na coluna Cancelada
    const countCancelada = await page.locator('.kanban-column[data-column="Cancelada"] [data-column-count]').innerText();
    assert.equal(countCancelada, "1", "Coluna Cancelada exibe contagem 1 após reload");

    // Confere linha do tempo de ambas após reload
    await clickCardAction(page, "Pulverização Fungicida", "detail");
    await page.waitForSelector('[data-modal="activity-detail"].is-open');
    await page.waitForSelector('[data-detail-timeline] .timeline-entry');
    const timelineReloadText = await page.locator('[data-detail-timeline]').innerText();
    assert.ok(timelineReloadText.includes("Atividade cancelada"), "Timeline pós-reload exibe evento de cancelamento");
    assert.ok(timelineReloadText.includes("Chuva intensa"), "Timeline pós-reload exibe o motivo persistido");
    console.log("OK - Histórico e estados reais completamente persistidos e confirmados após reload.");

    console.log("=== TODOS OS CRITÉRIOS DE ACEITE DA SUBISSUE #140 FORAM VALIDADOS COM SUCESSO! ===");
  } finally {
    if (browser) await browser.close();
    if (server.exitCode === null) {
      server.stdin.write("desativar\n");
      await new Promise((resolve) => server.once("exit", resolve));
    }
  }
})();
