// Miguel — RA: 25003110
/* Gestão da equipe: convite por link e permissões por tela. */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

  const TELAS = [
    { key: "dashboard", label: "Painel", hint: "Visão geral e indicadores" },
    { key: "areas-cultivo", label: "Áreas de Cultivo", hint: "Talhões e plantio" },
    { key: "culturas", label: "Culturas", hint: "Catálogo de culturas" },
    { key: "safras", label: "Safras", hint: "Planejamento e etapas" },
    { key: "atividades", label: "Atividades", hint: "Agenda operacional" },
    { key: "insumos", label: "Insumos", hint: "Estoque e consumo" },
    { key: "manutencao", label: "Manutenção", hint: "Equipamentos e preventivas" },
    { key: "ocorrencias", label: "Ocorrências", hint: "Eventos e pragas" },
    { key: "custos", label: "Custos", hint: "Despesas e orçamento" },
    { key: "configuracoes", label: "Configurações", hint: "Conta e preferências" },
  ];

  const base = window.location.origin;
  const padraoTelas = () => Object.fromEntries(TELAS.map((tela) => [tela.key, tela.key !== "configuracoes"]));
  let cargaAtual = 0;

  function toast(type, title, description) {
    window.AgroToast?.show({ type, title, description });
  }

  function iniciais(nome) {
    const partes = String(nome || "").trim().split(/\s+/).filter(Boolean);
    if (!partes.length) return "—";
    return partes.slice(0, 2).map((parte) => parte[0]?.toUpperCase() || "").join("") || "—";
  }

  async function api(path, options = {}) {
    const token = sessionStorage.getItem("agro.sessionToken");
    if (!token) throw new Error("Sessão ausente.");
    const response = await fetch(base + path, {
      ...options,
      signal: AbortSignal.timeout(15000),
      headers: {
        Authorization: "Bearer " + token,
        "Content-Type": "application/json",
        ...(options.headers || {}),
      },
    });
    if (response.status === 204) return null;
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw Object.assign(new Error(data.error || "Não foi possível concluir."), { fields: data.fields || {}, status: response.status });
    }
    return data;
  }

  function coletarTelas(root) {
    const telas = {};
    $$("[data-tela]", root).forEach((input) => {
      telas[input.dataset.tela] = input.checked;
    });
    return telas;
  }

  function resumoTelas(telas = {}) {
    const ativas = TELAS.filter((t) => telas[t.key] ?? t.key !== "configuracoes").length;
    return `${ativas} de ${TELAS.length} telas`;
  }

  function atualizarResumoAcessos(root) {
    const hint = $("[data-equipe-acessos-hint]", root);
    if (hint) hint.textContent = resumoTelas(coletarTelas(root));
  }

  function definirPainelAcessos(root, aberto) {
    const trigger = $("[data-equipe-acessos-trigger]", root);
    const body = $("[data-equipe-acessos-body]", root);
    if (!trigger || !body) return;
    trigger.setAttribute("aria-expanded", String(aberto));
    body.hidden = !aberto;
  }

  // Cabeçalho retrátil: no card novo começa aberto; nos demais, fechado.
  function htmlPainelAcessos(sufixo, aberto) {
    const bodyId = `equipe-acessos-${sufixo}`;
    return (
      '<div class="team-access-panel">' +
      `<button type="button" class="team-access-trigger" data-equipe-acessos-trigger aria-expanded="${aberto}" aria-controls="${bodyId}">` +
      '<span class="team-access-title">Telas liberadas</span>' +
      '<span class="team-access-hint" data-equipe-acessos-hint></span>' +
      '<svg class="team-access-chevron" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>' +
      "</button>" +
      `<div class="team-access-body" id="${bodyId}" data-equipe-acessos-body${aberto ? "" : " hidden"}>` +
      '<div class="preference-list team-access-list" data-equipe-acessos></div></div></div>'
    );
  }

  function ligarPainelAcessos(root) {
    const trigger = $("[data-equipe-acessos-trigger]", root);
    if (!trigger) return;
    trigger.addEventListener("click", () => {
      const abrir = trigger.getAttribute("aria-expanded") !== "true";
      definirPainelAcessos(root, abrir);
    });
    $$("input[data-tela]", root).forEach((input) => {
      input.addEventListener("change", () => atualizarResumoAcessos(root));
    });
    atualizarResumoAcessos(root);
  }

  function renderAcessos(root, telas = {}) {
    const lista = $("[data-equipe-acessos]", root);
    if (!lista) return;
    lista.innerHTML = TELAS.map((tela) => {
      const ativo = telas[tela.key] ?? tela.key !== "configuracoes";
      return (
        `<label class="preference-row team-access-row"><span><strong>${tela.label}</strong>` +
        `<small>${tela.hint}</small></span>` +
        `<input type="checkbox" data-tela="${tela.key}" ${ativo ? "checked" : ""}>` +
        `<span class="toggle-control" aria-hidden="true"></span></label>`
      );
    }).join("");
    atualizarResumoAcessos(root);
  }

  function etiquetaStatus(status) {
    if (status === "convite_pendente") return "Convite pendente";
    if (status === "ativo") return "Colaborador ativo";
    return "Referência local";
  }

  async function copiarLink(link) {
    try {
      await navigator.clipboard.writeText(link);
      toast("success", "Link copiado", "Envie manualmente para o convidado.");
    } catch {
      toast("info", "Copie o link", link);
    }
  }

  function montarPainelConvite(card, membro) {
    if (membro?.status !== "convite_pendente" || !membro.conviteLink) return;
    const painel = document.createElement("div");
    painel.className = "team-invite-panel";
    painel.innerHTML =
      '<p class="team-invite-label">Link do convite</p>' +
      '<div class="team-invite-row">' +
        '<input type="text" readonly data-equipe-link>' +
        '<button type="button" class="btn btn-outline btn-small" data-equipe-copiar-link>Copiar</button>' +
        '<button type="button" class="btn btn-outline btn-small" data-equipe-renovar-convite>Novo link</button>' +
      "</div>" +
      '<small>Válido por 7 dias. O convidado precisa entrar com o mesmo e-mail (reenvio automático ao renovar).</small>';
    $("[data-equipe-link]", painel).value = membro.conviteLink;
    $("[data-equipe-copiar-link]", painel).addEventListener("click", () => copiarLink(membro.conviteLink));
    $("[data-equipe-renovar-convite]", painel).addEventListener("click", async () => {
      try {
        const resposta = await api(`/api/equipe/${encodeURIComponent(membro.id)}/convite/renovar`, { method: "POST" });
        const detalhe = resposta?.conviteEmailEnviado
          ? "Novo prazo e e-mail reenviado ao convidado."
          : "Novo prazo — copie o link se o e-mail não chegar.";
        toast("success", "Link renovado", detalhe);
        await carregar();
        if (resposta?.conviteLink) copiarLink(resposta.conviteLink);
      } catch (erro) {
        toast("error", "Não foi possível renovar", erro.message);
      }
    });
    card.appendChild(painel);
  }

  function montarCard(membro) {
    const pendente = membro?.status === "convite_pendente";
    const article = document.createElement("article");
    article.className = "team-member-card";
    article.dataset.equipeItem = membro.id;
    article.innerHTML =
      `<p class="team-status-badge" data-equipe-status-badge>${etiquetaStatus(membro.status)}</p>` +
      '<div class="team-member-head">' +
        '<span class="team-member-avatar" data-equipe-avatar aria-hidden="true">—</span>' +
        '<div class="team-member-fields">' +
          (pendente
            ? `<div class="input-group"><label>E-mail do convite</label><input name="email" type="email" readonly></div>`
            : "") +
          '<div class="input-group"><label>Nome *</label>' +
          `<input name="nome" maxlength="120" ${pendente ? "readonly" : "required"} placeholder="Ex.: Ana Silva">` +
          '<p class="field-error" data-error-for="nome"></p></div>' +
          '<div class="input-group"><label>Função na fazenda</label>' +
          '<input name="cargo" maxlength="80" placeholder="Ex.: Operador de campo">' +
          '<p class="field-error" data-error-for="cargo"></p></div>' +
        "</div>" +
        '<div class="team-member-actions">' +
          '<button type="button" class="btn btn-primary btn-small" data-equipe-save>Salvar</button>' +
          '<button type="button" class="btn btn-outline btn-small" data-equipe-remove>Remover</button>' +
        "</div>" +
      "</div>" +
      htmlPainelAcessos(membro.id, false);

    const nomeInput = $("input[name='nome']", article);
    const cargoInput = $("input[name='cargo']", article);
    nomeInput.value = membro.nome || "";
    cargoInput.value = membro.cargo || "";
    if (pendente) $("input[name='email']", article).value = membro.email || "";
    const avatar = $("[data-equipe-avatar]", article);
    const syncAvatar = () => { avatar.textContent = iniciais(nomeInput.value || membro.email); };
    if (!pendente) nomeInput.addEventListener("input", syncAvatar);
    renderAcessos(article, membro.telas || padraoTelas());
    syncAvatar();
    montarPainelConvite(article, membro);
    return article;
  }

  function montarNovo() {
    const article = document.createElement("article");
    article.className = "team-member-card";
    article.dataset.equipeItem = "novo";
    article.innerHTML =
      '<div class="team-member-head">' +
        '<span class="team-member-avatar" data-equipe-avatar aria-hidden="true">—</span>' +
        '<div class="team-member-fields">' +
          '<div class="input-group"><label>E-mail do convidado *</label>' +
          '<input name="email" type="email" required autocomplete="email" placeholder="colega@exemplo.com">' +
          '<p class="field-error" data-error-for="email"></p></div>' +
          '<div class="input-group"><label>Nome de exibição</label>' +
          '<input name="nome" maxlength="120" placeholder="Opcional até aceitar o convite">' +
          '<p class="field-error" data-error-for="nome"></p></div>' +
          '<div class="input-group"><label>Função na fazenda</label>' +
          '<input name="cargo" maxlength="80" placeholder="Ex.: Operador de campo"></div>' +
        "</div>" +
        '<div class="team-member-actions">' +
          '<button type="button" class="btn btn-primary btn-small" data-equipe-save>Gerar convite</button>' +
        "</div>" +
      "</div>" +
      '<p class="team-invite-help">Enviaremos o convite por e-mail (se o SMTP estiver configurado). O link também fica disponível para copiar.</p>' +
      htmlPainelAcessos("novo", true);

    const nomeInput = $("input[name='nome']", article);
    const emailInput = $("input[name='email']", article);
    const avatar = $("[data-equipe-avatar]", article);
    const syncAvatar = () => {
      avatar.textContent = iniciais(nomeInput.value || emailInput.value);
    };
    nomeInput.addEventListener("input", syncAvatar);
    emailInput.addEventListener("input", syncAvatar);
    renderAcessos(article, padraoTelas());
    syncAvatar();
    return article;
  }

  function aplicarErros(card, fields = {}) {
    $$(".field-error", card).forEach((el) => { el.textContent = ""; });
    $$("[aria-invalid]", card).forEach((el) => el.removeAttribute("aria-invalid"));
    Object.entries(fields).forEach(([key, text]) => {
      const input = card.querySelector(`[name='${key}']`);
      input?.setAttribute("aria-invalid", "true");
      const target = $(`[data-error-for='${key}']`, card) || input?.closest(".input-group")?.querySelector(".field-error");
      if (target) target.textContent = text;
    });
    $("[aria-invalid]", card)?.focus();
  }

  async function salvar(card) {
    const id = card.dataset.equipeItem;
    const nome = $("input[name='nome']", card)?.value.trim();
    const cargo = $("input[name='cargo']", card)?.value.trim();
    const email = $("input[name='email']", card)?.value.trim();
    const novoConvite = id === "novo";

    if (novoConvite && !email) {
      aplicarErros(card, { email: "Informe o e-mail do convidado." });
      return;
    }
    if (id !== "novo" && (!nome || nome.length < 2)) {
      aplicarErros(card, { nome: "Use pelo menos 2 caracteres." });
      return;
    }

    const corpo = { nome, cargo, telas: coletarTelas(card) };
    if (novoConvite) corpo.email = email;

    const botao = $("[data-equipe-save]", card);
    botao.disabled = true;
    botao.classList.add("is-loading");
    try {
      const resposta = id && id !== "novo"
        ? await api(`/api/equipe/${encodeURIComponent(id)}`, { method: "PUT", body: JSON.stringify(corpo) })
        : await api("/api/equipe", { method: "POST", body: JSON.stringify(corpo) });
      let msg = "Permissões e dados salvos.";
      if (novoConvite) {
        msg = resposta?.conviteEmailEnviado
          ? "Convite enviado por e-mail. O link também foi copiado."
          : "Convite criado. SMTP indisponível — copie o link e envie manualmente.";
      }
      toast("success", "Equipe atualizada", msg);
      await carregar();
      if (resposta?.conviteLink) copiarLink(resposta.conviteLink);
    } catch (erro) {
      aplicarErros(card, erro.fields || {});
      toast("error", "Não foi possível salvar", erro.message);
    } finally {
      botao.disabled = false;
      botao.classList.remove("is-loading");
    }
  }

  async function remover(card) {
    const id = card.dataset.equipeItem;
    if (!id || id === "novo") return;
    if (!window.confirm("Remover este integrante da equipe?")) return;
    const botao = $("[data-equipe-remove]", card);
    botao.disabled = true;
    try {
      await api(`/api/equipe/${encodeURIComponent(id)}`, { method: "DELETE" });
      toast("success", "Integrante removido", "O registro saiu da equipe desta propriedade.");
      await carregar();
    } catch (erro) {
      toast("error", "Não foi possível remover", erro.message);
      botao.disabled = false;
    }
  }

  function ligarAcoes(card) {
    ligarPainelAcessos(card);
    $("[data-equipe-save]", card)?.addEventListener("click", () => salvar(card));
    $("[data-equipe-remove]", card)?.addEventListener("click", () => remover(card));
  }

  async function carregar() {
    const lista = $("[data-equipe-list]");
    const status = $("[data-equipe-status]");
    const formulario = $("[data-equipe-form]");
    const painel = $("[data-settings-panel='team']");
    if (!lista || !status) return;

    const vez = ++cargaAtual;
    status.textContent = "Carregando equipe…";
    lista.replaceChildren();
    if (formulario) formulario.replaceChildren();
    try {
      const membros = await api("/api/equipe");
      if (vez !== cargaAtual) return;
      if (!Array.isArray(membros)) throw new Error("Resposta inválida da equipe.");
      status.textContent = `${membros.length} integrante(s) nesta propriedade.`;
      membros.forEach((membro) => {
        const card = montarCard(membro);
        ligarAcoes(card);
        lista.appendChild(card);
      });
      if (formulario) {
        const novo = montarNovo();
        ligarAcoes(novo);
        formulario.appendChild(novo);
      }
    } catch (erro) {
      if (vez !== cargaAtual) return;
      if (erro.status === 403) {
        status.textContent = "Somente o proprietário gerencia a equipe desta propriedade.";
        painel?.querySelectorAll("[data-equipe-form], [data-equipe-list]").forEach((el) => { el.hidden = true; });
        return;
      }
      status.textContent = "Não foi possível carregar a equipe.";
      const detalhe = erro.status === 404
        ? "A API não expõe /api/equipe. Pare o servidor e suba de novo com bash emular.sh --build."
        : erro.message;
      toast("error", "Equipe indisponível", detalhe);
    }
  }

  function init() {
    if (!$("[data-settings-panel='team']")) return;
    $("[data-equipe-retry]")?.addEventListener("click", carregar);
  }

  document.addEventListener("agro:settings-tab", (event) => {
    if (event.detail === "team") carregar();
  });

  document.addEventListener("DOMContentLoaded", init);
  window.AgroEquipe = { iniciais, carregar, TELAS };
})();