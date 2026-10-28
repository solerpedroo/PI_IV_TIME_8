// Miguel — RA: 25003110
// Tela de Safras: acompanha etapas oficiais, vínculos operacionais e histórico persistido na API.
(() => {
  "use strict";
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const ETAPAS = ["Planejamento", "Preparação", "Plantio", "Manejo", "Colheita", "Finalização"];
  const ROTULOS_TIPO = { atividade: "Atividade", ocorrencia: "Ocorrência", custo: "Custo" };
  const moeda = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
  const state = { safras: [], talhoes: [], historySafra: null, historyRecords: [], historyFilter: "" };
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

  function syncHistoryUrl(safraId, tipo = "") {
    const url = new URL(window.location.href);
    if (safraId) url.searchParams.set("historico", safraId);
    else url.searchParams.delete("historico");
    if (safraId && tipo) url.searchParams.set("tipo", tipo);
    else url.searchParams.delete("tipo");
    window.history.replaceState({}, "", url);
  }

  function modal(name, open) {
    const target = $(`[data-modal='${name}']`);
    target.classList.toggle("is-open", open);
    target.setAttribute("aria-hidden", String(!open));
    if (!open && name === "safra-history") {
      state.historySafra = null;
      syncHistoryUrl("");
    }
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

  // Monta os atalhos contextuais para Atividades, Ocorrências e Custos usando os talhões reais da safra.
  function renderLinksContextuais(safra, nomesTalhoes) {
    return (safra.talhoes || []).map((id) => {
      const nome = nomesTalhoes.get(id) || id;
      const paramNome = encodeURIComponent(nome);
      const paramId = encodeURIComponent(id);
      return `<span><strong>${esc(nome)}:</strong> <a href="atividades.html?talhao=${paramNome}" data-context-link="atividades" data-plot-name="${esc(nome)}">Atividades</a> · <a href="ocorrencias.html?talhaoId=${paramId}&amp;talhao=${paramNome}" data-context-link="ocorrencias" data-plot-id="${esc(id)}">Ocorrências</a> · <a href="custos.html?talhao=${paramNome}" data-context-link="custos" data-plot-name="${esc(nome)}">Custos</a></span>`;
    }).join(" · ");
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
      return `<article class="list-item" data-safra-id="${esc(safra.id)}" data-safra-stage="${esc(safra.etapa)}"><div class="safra-head"><div><strong>${esc(safra.nome)}</strong><p class="safra-meta">${esc(safra.cultura)} · ${esc(safra.dataInicio)} a ${esc(safra.dataFim)} · ${esc(safra.areaHectares)} ha${talhoes ? ` · Talhões: ${esc(talhoes)}` : ""}</p><span class="badge ${finalizada ? "badge-success" : "badge-neutral"}" data-current-stage>${esc(safra.etapa)} (${indiceAtual + 1}/${ETAPAS.length})</span></div><div class="safra-actions"><button class="btn btn-outline" type="button" data-history="${esc(safra.id)}">Registros relacionados</button>${proxima ? `<button class="btn btn-primary" type="button" data-advance="${esc(safra.id)}" data-next-stage="${esc(proxima)}">Avançar para ${esc(proxima)}</button>` : `<span class="badge badge-success" data-safra-readonly>Ciclo finalizado · Somente leitura</span>`}</div></div>${renderEtapas(safra, indiceAtual)}<div class="safra-links" data-safra-links>Acesso contextual — ${renderLinksContextuais(safra, nomesTalhoes)}</div></article>`;
    }).join("");
    $("[data-safras-empty]").hidden = lista.length > 0;
  }

  // Unifica registros do histórico do talhão, ocorrências no período e custos vinculados à safra.
  function consolidarRegistros(safra, historicoTalhoes, ocorrencias, custos) {
    const talhoesSet = new Set(safra.talhoes || []);
    const registros = [];
    const chaves = new Set();
    const adicionar = (item) => {
      const chave = `${item.tipo}|${item.talhaoNome}|${item.descricao}`;
      if (chaves.has(chave)) return;
      chaves.add(chave);
      registros.push(item);
    };

    (historicoTalhoes || []).forEach((ev) => {
      const tipo = String(ev.tipo || "atividade").toLowerCase();
      adicionar({
        tipo,
        rotulo: ROTULOS_TIPO[tipo] || ev.tipo || "Registro",
        descricao: ev.descricao || ev.titulo || ev.nome || "Registro da safra",
        talhaoNome: ev.talhaoNome || "Talhão",
        data: String(ev.criadoEm || "").slice(0, 10),
      });
    });

    (ocorrencias || []).forEach((occ) => {
      if (!talhoesSet.has(occ.talhaoId)) return;
      if (!occ.data || occ.data < safra.dataInicio || occ.data > safra.dataFim) return;
      adicionar({
        tipo: "ocorrencia",
        rotulo: "Ocorrência",
        descricao: `${occ.titulo} (${occ.tipo} · Prioridade ${occ.prioridade})`,
        talhaoNome: occ.talhaoNome || "Talhão",
        data: occ.data,
      });
    });

    (custos || []).forEach((custo) => {
      adicionar({
        tipo: "custo",
        rotulo: "Custo",
        descricao: `${custo.description} (${custo.category} · ${moeda.format(Number(custo.value || 0))})`,
        talhaoNome: custo.talhaoNome || "Safra geral",
        data: custo.date || String(custo.criadoEm || "").slice(0, 10),
      });
    });

    return registros.sort((a, b) => String(b.data).localeCompare(String(a.data)));
  }

  function renderHistoryModal() {
    const safra = state.historySafra;
    if (!safra) return;
    const nomesTalhoes = new Map(state.talhoes.map((talhao) => [talhao.id, talhao.nome]));
    const finalizada = safra.etapa === "Finalização";
    $("[data-history-title]").textContent = `Histórico da safra — ${safra.nome}`;
    $("[data-history-subtitle]").textContent = `${safra.cultura} · ${safra.dataInicio} a ${safra.dataFim} · ${safra.areaHectares} ha${finalizada ? " · Safra finalizada (somente leitura)" : ""}`;

    const qtdAtividades = state.historyRecords.filter((r) => r.tipo === "atividade").length;
    const qtdOcorrencias = state.historyRecords.filter((r) => r.tipo === "ocorrencia").length;
    const qtdCustos = state.historyRecords.filter((r) => r.tipo === "custo").length;
    const etapasConcluidas = (safra.historicoEtapas || []).map((e) => e.etapa).join(" → ");

    $("[data-safra-history-summary]").innerHTML = `
      <div class="meta-cell"><span class="meta-cell-label">Etapa do ciclo</span><span class="meta-cell-value" data-history-stage>${esc(safra.etapa)}${finalizada ? " (Finalizada)" : ""}</span><small>${esc(etapasConcluidas)}</small></div>
      <div class="meta-cell"><span class="meta-cell-label">Atividades</span><span class="meta-cell-value" data-history-count="atividade">${qtdAtividades}</span></div>
      <div class="meta-cell"><span class="meta-cell-label">Ocorrências</span><span class="meta-cell-value" data-history-count="ocorrencia">${qtdOcorrencias}</span></div>
      <div class="meta-cell"><span class="meta-cell-label">Custos na safra</span><span class="meta-cell-value" data-history-count="custo">${qtdCustos} · ${esc(moeda.format(state.historyTotalCost || 0))}</span></div>
    `;
    $("[data-safra-context-links]").innerHTML = `<span>Ir para módulos filtrados por talhão:</span> ${renderLinksContextuais(safra, nomesTalhoes)}`;

    $$("[data-history-filter]").forEach((btn) => {
      btn.classList.toggle("is-active", btn.dataset.historyFilter === state.historyFilter);
    });

    const filtrados = state.historyRecords.filter((item) => !state.historyFilter || item.tipo === state.historyFilter);
    const list = $("[data-safra-history-list]");
    if (!filtrados.length) {
      list.innerHTML = '<div class="empty-state"><h3>Sem registros no período</h3><p>Os eventos dos talhões aparecerão aqui quando forem registrados.</p></div>';
      return;
    }
    list.innerHTML = filtrados.map((record) => `<article class="meta-cell" data-history-item="${esc(record.tipo)}"><strong>${esc(record.rotulo)}</strong><span class="meta-cell-value">${esc(record.descricao)}</span><small>${esc(record.talhaoNome)} · ${esc(record.data)}</small></article>`).join("");
  }

  async function abrirHistorico(safraId, filtro = "") {
    const id = encodeURIComponent(safraId);
    const [safra, historicoTalhoes, ocorrencias, custos, resumoCustos] = await Promise.all([
      api(`/api/safras/${id}`),
      api(`/api/safras/${id}/historico`),
      api("/api/ocorrencias").catch(() => []),
      api(`/api/custos?safraId=${id}`).catch(() => []),
      api(`/api/custos/resumo?safraId=${id}`).catch(() => ({ total: 0 })),
    ]);
    state.historySafra = safra;
    state.historyFilter = ["atividade", "ocorrencia", "custo"].includes(filtro) ? filtro : "";
    state.historyTotalCost = Number(resumoCustos?.total || 0);
    state.historyRecords = consolidarRegistros(safra, historicoTalhoes, ocorrencias, custos);
    syncHistoryUrl(safra.id, state.historyFilter);
    renderHistoryModal();
    modal("safra-history", true);
  }

  async function carregar() {
    const [safras, talhoes] = await Promise.all([api("/api/safras"), api("/api/talhoes")]);
    state.safras = safras;
    state.talhoes = talhoes;
    $("[name='talhoes']").innerHTML = talhoes.map((talhao) => `<option value="${esc(talhao.id)}">${esc(talhao.nome)}</option>`).join("");
    render();

    const params = new URLSearchParams(window.location.search);
    const historicoId = params.get("historico");
    if (historicoId) {
      try {
        await abrirHistorico(historicoId, params.get("tipo") || "");
      } catch (error) {
        syncHistoryUrl("");
        toast("error", "Não foi possível consultar a safra", error?.error || "Safra não encontrada para consulta de histórico.");
      }
    }
  }

  $("[data-new-safra]").addEventListener("click", () => modal("safra-form", true));
  $("[data-close-safra]").addEventListener("click", () => modal("safra-form", false));
  $("[data-close-history]").addEventListener("click", () => modal("safra-history", false));
  $("[data-filter-stage]").addEventListener("change", render);
  $$("[data-history-filter]").forEach((btn) => btn.addEventListener("click", () => {
    state.historyFilter = btn.dataset.historyFilter || "";
    if (state.historySafra) syncHistoryUrl(state.historySafra.id, state.historyFilter);
    renderHistoryModal();
  }));
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
        await abrirHistorico(history.dataset.history);
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
