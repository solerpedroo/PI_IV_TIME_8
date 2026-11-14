// Miguel — RA: 25003110
/* Gestão da equipe na aba Configurações: integrantes, cargo e acesso às telas. */
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
  }

  function montarCard(membro = null) {
    const article = document.createElement("article");
    article.className = "team-member-card";
    article.dataset.equipeItem = membro?.id || "novo";
    article.innerHTML =
      '<div class="team-member-head">' +
        '<span class="team-member-avatar" data-equipe-avatar aria-hidden="true">—</span>' +
        '<div class="team-member-fields">' +
          '<div class="input-group"><label>Nome *</label>' +
          '<input name="nome" maxlength="120" required placeholder="Ex.: Ana Silva">' +
          '<p class="field-error" data-error-for="nome"></p></div>' +
          '<div class="input-group"><label>Função na fazenda</label>' +
          '<input name="cargo" maxlength="80" placeholder="Ex.: Operador de campo">' +
          '<p class="field-error" data-error-for="cargo"></p></div>' +
        "</div>" +
        '<div class="team-member-actions">' +
          '<button type="button" class="btn btn-primary btn-small" data-equipe-save>Salvar</button>' +
          (membro?.id ? '<button type="button" class="btn btn-outline btn-small" data-equipe-remove>Remover</button>' : "") +
        "</div>" +
      "</div>" +
      '<div class="team-access-panel"><p class="team-access-title">Telas liberadas</p>' +
      '<div class="preference-list team-access-list" data-equipe-acessos></div></div>';

    const nomeInput = $("input[name='nome']", article);
    const cargoInput = $("input[name='cargo']", article);
    const sufixo = membro?.id || "novo";
    // Nome e cargo vêm da API: entram pela propriedade value, nunca no HTML.
    nomeInput.value = membro?.nome || "";
    cargoInput.value = membro?.cargo || "";
    nomeInput.id = `equipe-nome-${sufixo}`;
    cargoInput.id = `equipe-cargo-${sufixo}`;
    nomeInput.closest(".input-group").querySelector("label").htmlFor = nomeInput.id;
    cargoInput.closest(".input-group").querySelector("label").htmlFor = cargoInput.id;
    const avatar = $("[data-equipe-avatar]", article);
    const syncAvatar = () => { avatar.textContent = iniciais(nomeInput.value); };
    nomeInput.addEventListener("input", syncAvatar);
    renderAcessos(article, membro?.telas || padraoTelas());
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
    const nome = $("input[name='nome']", card)?.value.trim();
    const cargo = $("input[name='cargo']", card)?.value.trim();
    if (!nome || nome.length < 2) {
      aplicarErros(card, { nome: "Use pelo menos 2 caracteres." });
      return;
    }
    const corpo = { nome, cargo, telas: coletarTelas(card) };
    const id = card.dataset.equipeItem;
    const botao = $("[data-equipe-save]", card);
    botao.disabled = true;
    botao.classList.add("is-loading");
    try {
      const resposta = id && id !== "novo"
        ? await api(`/api/equipe/${encodeURIComponent(id)}`, { method: "PUT", body: JSON.stringify(corpo) })
        : await api("/api/equipe", { method: "POST", body: JSON.stringify(corpo) });
      toast("success", "Equipe atualizada", id === "novo" ? "Integrante adicionado à propriedade." : "Permissões e dados salvos.");
      await carregar();
      if (resposta?.id && /^[a-f\d]{24}$/i.test(resposta.id))
        document.querySelector(`[data-equipe-item="${resposta.id}"]`)?.scrollIntoView({ behavior: "smooth", block: "nearest" });
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
    $("[data-equipe-save]", card)?.addEventListener("click", () => salvar(card));
    $("[data-equipe-remove]", card)?.addEventListener("click", () => remover(card));
  }

  async function carregar() {
    const lista = $("[data-equipe-list]");
    const status = $("[data-equipe-status]");
    const formulario = $("[data-equipe-form]");
    if (!lista || !status) return;

    const vez = ++cargaAtual;
    status.textContent = "Carregando equipe…";
    lista.replaceChildren();
    if (formulario) formulario.replaceChildren();
    try {
      const membros = await api("/api/equipe");
      if (vez !== cargaAtual) return;
      if (!Array.isArray(membros)) throw new Error("Resposta inválida da equipe.");
      status.textContent = membros.length
        ? `${membros.length} integrante(s) nesta propriedade.`
        : "Nenhum integrante cadastrado. Adicione o primeiro abaixo.";
      membros.forEach((membro) => {
        const card = montarCard(membro);
        ligarAcoes(card);
        lista.appendChild(card);
      });
      if (formulario) {
        formulario.replaceChildren();
        const novo = montarCard();
        ligarAcoes(novo);
        formulario.appendChild(novo);
      }
    } catch (erro) {
      if (vez !== cargaAtual) return;
      status.textContent = "Não foi possível carregar a equipe.";
      toast("error", "Equipe indisponível", erro.message);
    }
  }

  function init() {
    if (!$("[data-settings-panel='team']")) return;
    $("[data-equipe-retry]")?.addEventListener("click", carregar);
  }

  // O script de abas dispara este evento no DOMContentLoaded, antes do init daqui.
  document.addEventListener("agro:settings-tab", (event) => {
    if (event.detail === "team") carregar();
  });

  document.addEventListener("DOMContentLoaded", init);
  window.AgroEquipe = { iniciais, carregar, TELAS };
})();