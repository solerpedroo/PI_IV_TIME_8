// Miguel — RA: 25003110
// Tela de Safras: acompanha as etapas oficiais do ciclo produtivo e o histórico persistidos na API.
(() => {
  "use strict";
  const $ = (selector, root = document) => root.querySelector(selector);
  const ETAPAS = ["Planejamento", "Preparação", "Plantio", "Manejo", "Colheita", "Finalização"];
  const state = { safras: [], talhoes: [] };
  const base = window.location.port === "8080" ? window.location.origin : "http://127.0.0.1:8080";
  const token = () => sessionStorage.getItem("agro.sessionToken");
  const toast = (type, title, description) => window.AgroToast?.show({ type, title, description });
  const esc = (value) => String(value ?? "").replace(/[&<>\"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[char]);

  // Centraliza chamadas autenticadas com Bearer e devolve o erro estruturado da API.
  async function api(path, options = {}) {
    if (!token()) throw new Error("Sessão ausente.");
    const response = await fetch(base + path, {
      ...options,
      headers: { Authorization: "Bearer " + token(), "Content-Type": "application/json", ...(options.headers || {}) },
    });
    const body = response.status === 204 ? null : await response.json();
    if (!response.ok) throw body;
    return body;
  }

  function modal(name, open) {
    const target = $(`[data-modal='${name}']`);
    target.classList.toggle("is-open", open);
    target.setAttribute("aria-hidden", String(!open));
    if (open) target.querySelector("input, select, button")?.focus();
  }

  function clearErrors(form) {
    form.querySelectorAll("[data-error-for]").forEach((node) => { node.textContent = ""; });
    form.querySelectorAll("[aria-invalid='true']").forEach((node) => node.removeAttribute("aria-invalid"));
  }

  function showErrors(form, fields) {
    let first;
    Object.entries(fields).forEach(([name, message]) => {
      const field = form.elements[name];
      const output = $(`[data-error-for='${name}']`, form);
      if (output) output.textContent = message;
      if (field) {
        field.setAttribute("aria-invalid", "true");
        first ||= field;
      }
    });
    first?.focus();
  }

  function validate(form, data) {
    const errors = {};
    if (!data.nome?.trim()) errors.nome = "Informe o nome da safra.";
    if (!data.cultura) errors.cultura = "Selecione uma cultura.";
    if (!data.talhoes.length) errors.talhoes = "Selecione ao menos um talhão.";
    if (!data.dataInicio) errors.dataInicio = "Informe a data de início.";
    if (!data.dataFim) errors.dataFim = "Informe a data de término.";
    if (data.dataInicio && data.dataFim && data.dataFim < data.dataInicio) {
      errors.dataFim = "O término deve ser igual ou posterior ao início.";
    }
    if (Object.keys(errors).length) {
      showErrors(form, errors);
      return false;
    }
    return true;
  }

  // Monta a trilha oficial de 6 etapas usando a etapa atual e o historicoEtapas da API.
  function renderEtapas(safra, indiceAtual) {
    const historico = new Map((safra.historicoEtapas || []).map((item) => [item.etapa, item.em]));
    return `<ol class="cycle-steps" aria-label="Etapas do ciclo produtivo">${ETAPAS.map((nome, indice) => {
      const atual = indice === indiceAtual;
      const concluida = indice < indiceAtual || (safra.etapa === "Finalização" && atual);
      const estado = atual ? "current" : concluida ? "done" : "pending";
      const rotulo = atual ? "Etapa atual" : concluida ? "Concluída" : "Pendente";
      const data = String(historico.get(nome) || "").slice(0, 10);
      return `<li class="cycle-step is-${estado}" data-cycle-step="${esc(nome)}" data-step-state="${estado}"${atual ? ' aria-current="step"' : ""}><strong>${indice + 1}. ${esc(nome)}</strong><small>${rotulo}${data ? ` · ${esc(data)}` : ""}</small></li>`;
    }).join("")}</ol>`;
  }

  function render() {
    const etapaFiltro = $("[data-filter-stage]").value;
    const nomesTalhoes = new Map(state.talhoes.map((talhao) => [talhao.id, talhao.nome]));
    const lista = state.safras.filter((safra) => !etapaFiltro || safra.etapa === etapaFiltro);

    $("[data-safras-list]").innerHTML = lista.map((safra) => {
      const indiceAtual = Math.max(0, ETAPAS.indexOf(safra.etapa));
      const proxima = indiceAtual < ETAPAS.length - 1 ? ETAPAS[indiceAtual + 1] : null;
      const talhoes = (safra.talhoes || []).map((id) => nomesTalhoes.get(id) || id).join(", ");
      const finalizada = safra.etapa === "Finalização";
      return `<article class="list-item" data-safra-id="${esc(safra.id)}" data-safra-stage="${esc(safra.etapa)}"><div class="safra-head"><div><strong>${esc(safra.nome)}</strong><p class="safra-meta">${esc(safra.cultura)} · ${esc(safra.dataInicio)} a ${esc(safra.dataFim)} · ${esc(safra.areaHectares)} ha${talhoes ? ` · Talhões: ${esc(talhoes)}` : ""}</p><span class="badge ${finalizada ? "badge-success" : "badge-neutral"}" data-current-stage>${esc(safra.etapa)} (${indiceAtual + 1}/${ETAPAS.length})</span></div><div class="safra-actions"><button class="btn btn-outline" type="button" data-history="${esc(safra.id)}">Registros relacionados</button>${proxima ? `<button class="btn btn-primary" type="button" data-advance="${esc(safra.id)}" data-next-stage="${esc(proxima)}">Avançar para ${esc(proxima)}</button>` : `<span class="badge badge-success" data-safra-readonly>Ciclo finalizado · Somente leitura</span>`}</div></div>${renderEtapas(safra, indiceAtual)}</article>`;
    }).join("");
    $("[data-safras-empty]").hidden = lista.length > 0;
  }

  async function carregar() {
    const [safras, talhoes] = await Promise.all([api("/api/safras"), api("/api/talhoes")]);
    state.safras = safras;
    state.talhoes = talhoes;
    $("[name='talhoes']").innerHTML = talhoes.map((talhao) => `<option value="${esc(talhao.id)}">${esc(talhao.nome)}</option>`).join("");
    render();
  }

  function renderHistory(records) {
    const list = $("[data-safra-history-list]");
    if (!records.length) {
      list.innerHTML = '<div class="empty-state"><h3>Sem registros no período</h3><p>Os eventos dos talhões aparecerão aqui quando forem registrados.</p></div>';
      return;
    }
    list.innerHTML = records.map((record) => `<article class="meta-cell"><strong>${esc(record.tipo || "Registro")}</strong><span class="meta-cell-value">${esc(record.descricao || record.titulo || record.nome || "Registro da safra")}</span><small>${esc(record.talhaoNome || "Talhão")} · ${esc(record.criadoEm || "")}</small></article>`).join("");
  }

  $("[data-new-safra]").addEventListener("click", () => modal("safra-form", true));
  $("[data-close-safra]").addEventListener("click", () => modal("safra-form", false));
  $("[data-close-history]").addEventListener("click", () => modal("safra-history", false));
  $("[data-filter-stage]").addEventListener("change", render);
  document.querySelectorAll("[data-modal]").forEach((target) => target.addEventListener("click", (event) => {
    if (event.target === target) modal(target.dataset.modal, false);
  }));

  $("[data-safra-form]").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    clearErrors(form);
    const data = Object.fromEntries(new FormData(form));
    data.nome = data.nome.trim();
    data.talhoes = [...form.talhoes.selectedOptions].map((option) => option.value);
    if (!validate(form, data)) return;
    try {
      await api("/api/safras", { method: "POST", body: JSON.stringify(data) });
      modal("safra-form", false);
      form.reset();
      await carregar();
      toast("success", "Safra criada", "O ciclo foi salvo em planejamento e permanece após recarregar.");
    } catch (error) {
      if (error?.fields) showErrors(form, error.fields);
      toast("error", "Não foi possível criar", error?.error || "Revise os dados informados.");
    }
  });

  $("[data-safras-list]").addEventListener("click", async (event) => {
    const history = event.target.closest("[data-history]");
    const advance = event.target.closest("[data-advance]");
    try {
      if (history) {
        renderHistory(await api(`/api/safras/${history.dataset.history}/historico`));
        modal("safra-history", true);
      }
      if (advance) {
        if (advance.disabled) return;
        advance.disabled = true;
        const atualizada = await api(`/api/safras/${advance.dataset.advance}/avancar`, { method: "POST", body: "{}" });
        state.safras = state.safras.map((item) => (item.id === atualizada.id ? atualizada : item));
        render();
        await carregar();
        toast("success", "Etapa atualizada", `O ciclo avançou para ${atualizada.etapa}.`);
      }
    } catch (error) {
      if (advance) advance.disabled = false;
      toast("error", "Não foi possível atualizar a safra", error?.error || "Recarregue e tente novamente.");
      await carregar().catch(() => {});
    }
  });

  carregar().catch(() => toast("error", "Safras indisponíveis", "Inicie o servidor e faça login para acompanhar os ciclos."));
})();
