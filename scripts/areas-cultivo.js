/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */
/**
 * Agro — interações da tela Áreas de Cultivo (dados mock híbridos)
 * --------------------------------------------------------------------------
 * - Busca por nome + filtro por cultura (chips), com contagem de resultados
 *   e empty state (G8 "Espécime de filtros" / E "Empty states").
 * - Modal único de talhão reaproveitado para criar e editar (G3 "Validação
 *   de formulários"): valida campos obrigatórios, mostra erro inline e só
 *   fecha/atualiza a tabela quando os dados são válidos.
 * - Menu de ações por linha ("⋮"): "Ver atividades" e "Ver ocorrências"
 *   navegam para os outros módulos já filtrados por este talhão (contrato
 *   de navegação G7); "Ver custos" mostra toast — módulo fora desta entrega.
 *
 * Os registros são carregados e gravados na API autenticada de Talhões.
 */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const API_BASE = window.location.port === "8080" ? window.location.origin : "http://127.0.0.1:8080";

  /** Estado local — calculado dinamicamente a partir da tabela no load. */
  const state = {
    activePlots: 0,
    totalHectares: 0,
    editingRow: null, // <tr> atualmente em edição, ou null em modo "criar"
  };

  let persist = () => true;
  function toast(type, title, description) {
    if (window.AgroToast) window.AgroToast.show({ type, title, description });
  }

  // O token identifica a propriedade no servidor; o cliente nao envia esse ID.
  async function api(path, method = "GET", body) {
    const token = sessionStorage.getItem("agro.sessionToken");
    if (!token) throw new Error("Entre novamente para acessar os talhões.");

    const response = await fetch(API_BASE + path, {
      method,
      headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
      body: body ? JSON.stringify(body) : undefined,
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = new Error(data.error || "Não foi possível salvar o talhão.");
      error.fields = data.fields || {};
      throw error;
    }
    return data;
  }

  function formatHectares(value) {
    return `${value.toLocaleString("pt-BR")} ha`;
  }

  /** Recalcula activePlots e totalHectares a partir das linhas da tabela. */
  function computeMetricsFromTable() {
    const rows = $$("[data-plots-table] tbody tr");
    state.activePlots = rows.filter((row) => row.dataset.status === "Ativo").length;
    state.totalHectares = rows.reduce((sum, row) => sum + Number(row.dataset.hectares || 0), 0);
  }

  function updateHectaresMetric() {
    const el = $("[data-metric='hectares']");
    if (el) el.textContent = formatHectares(state.totalHectares);
  }

  function updateActivePlotsMetric() {
    const el = $("[data-metric='active-plots']");
    if (el) el.textContent = String(state.activePlots);
  }

  /* ---------------------------------------------------------------------- */
  /* Toasts de link "em preparação" fora do padrão de <a>/<button> do shell  */
  /* ---------------------------------------------------------------------- */
  function initPrepToasts() {
    $$("[data-prep-toast]").forEach((el) => {
      el.addEventListener("click", () => {
        toast("info", "Módulo em preparação", el.dataset.prepToast);
      });
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Busca + filtro por cultura                                             */
  /* ---------------------------------------------------------------------- */
  function initFilters() {
    const search = $("[data-plots-search]");
    const chips = $$("[data-filter-crop]");
    const count = $("[data-plots-count]");
    const empty = $("[data-plots-empty]");
    const table = $("[data-plots-table]");

    let activeCrop = "todos";

    function apply() {
      const q = (search?.value || "").trim().toLowerCase();
      let visible = 0;

      $$("[data-plots-table] tbody tr").forEach((row) => {
        const matchesCrop = activeCrop === "todos" || row.dataset.crop === activeCrop;
        const matchesQuery = !q || row.dataset.plot.toLowerCase().includes(q);
        const show = matchesCrop && matchesQuery;
        row.hidden = !show;
        if (show) visible += 1;
      });

      if (count) count.textContent = `${visible} talh${visible === 1 ? "ão" : "ões"} encontrado${visible === 1 ? "" : "s"}`;
      if (empty) empty.hidden = visible > 0;
      if (table) table.hidden = visible === 0;
    }

    search?.addEventListener("input", apply);

    chips.forEach((chip) => {
      chip.addEventListener("click", () => {
        chips.forEach((c) => c.classList.remove("is-active"));
        chip.classList.add("is-active");
        activeCrop = chip.dataset.filterCrop;
        apply();
      });
    });

    apply();
  }

  /* ---------------------------------------------------------------------- */
  /* Modal: Novo / Editar talhão                                            */
  /* ---------------------------------------------------------------------- */
  function openModal(modal) {
    if (!modal) return;
    modal.classList.add("is-open");
    modal.setAttribute("aria-hidden", "false");
    modal.querySelector("input, select, button")?.focus();
  }

  function closeModal(modal) {
    if (!modal) return;
    modal.classList.remove("is-open");
    modal.setAttribute("aria-hidden", "true");
  }

  function clearErrors(form) {
    $$(".field-error", form).forEach((el) => (el.textContent = ""));
    $$("input, select", form).forEach((el) => el.removeAttribute("aria-invalid"));
  }

  function setError(form, field, message) {
    const errorEl = form.querySelector(`[data-error-for="${field}"]`);
    if (errorEl) errorEl.textContent = message;
    const control = form.querySelector(`[name="${field}"]`);
    control?.setAttribute("aria-invalid", "true");
  }

  function statusBadgeClass(status) {
    if (status === "Ativo") return "badge-success";
    if (status === "Irrigação pendente") return "badge-warning";
    return "badge-neutral";
  }

  /** Constrói (ou atualiza) a <tr> da tabela a partir dos valores do formulário. */
  function buildRowMarkup(id, values) {
    const encoded = encodeURIComponent(values.nome);
    values = Object.fromEntries(Object.entries(values).map(([key, value]) => [key, window.AgroLocal.escape(value)]));
    return `
      <td class="col-plot-name">${values.nome}</td>
      <td><span class="badge badge-neutral">${values.cultura}</span></td>
      <td>${values.hectares} ha</td>
      <td><span class="badge ${statusBadgeClass(values.status)}" data-status-badge>${values.status}</span></td>
      <td class="col-muted">Cadastro manual</td>
      <td class="col-actions">
        <button type="button" class="row-action" data-edit-plot title="Editar talhão" aria-label="Editar ${values.nome}">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg>
        </button>
        <div class="action-menu-wrap">
          <button type="button" class="icon-btn icon-btn--sm" data-popover-trigger aria-controls="${id}" aria-expanded="false" aria-label="Mais ações — ${values.nome}">
            <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/></svg>
          </button>
          <div class="action-menu" id="${id}" data-popover role="menu">
            <a class="action-menu-item" role="menuitem" href="atividades.html?talhao=${encoded}">
              <span class="action-menu-icon tone-info" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M8 2v4"/><path d="M16 2v4"/><rect width="18" height="18" x="3" y="4" rx="2"/><path d="M3 10h18"/></svg></span>
              <span class="action-menu-label">Ver atividades</span>
            </a>
            <a class="action-menu-item" role="menuitem" href="ocorrencias.html?talhao=${encoded}">
              <span class="action-menu-icon" style="background:var(--warning-bg);color:var(--warning)" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg></span>
              <span class="action-menu-label">Ver ocorrências</span>
            </a>
            <a class="action-menu-item" role="menuitem" href="custos.html?talhao=${encoded}">
              <span class="action-menu-icon tone-secondary" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3 3v16a2 2 0 0 0 2 2h16"/><path d="M7 16v-5"/><path d="M12 16v-9"/><path d="M17 16V8"/></svg></span>
              <span class="action-menu-label">Ver custos</span>
            </a>
          </div>
        </div>
      </td>
    `;
  }

  function initPlotForm() {
    const modal = $("[data-modal='talhao-form']");
    const form = $("[data-talhao-form]", modal);
    const title = $("[data-talhao-form-title]", modal);
    const tbody = $("[data-plots-table] tbody");
    if (!modal || !form || !tbody) return;

    $$("[data-open-modal='talhao-form']").forEach((btn) => {
      btn.addEventListener("click", () => {
        state.editingRow = null;
        form.reset();
        clearErrors(form);
        title.textContent = "Novo Talhão";
        form.querySelector("[name='status']")?.dispatchEvent(new Event("change"));
        openModal(modal);
      });
    });

    // Delegação: qualquer botão "Editar" (linhas originais ou criadas depois).
    tbody.addEventListener("click", (event) => {
      const btn = event.target.closest("[data-edit-plot]");
      if (!btn) return;
      const row = btn.closest("tr");
      state.editingRow = row;
      clearErrors(form);
      title.textContent = "Editar Talhão";
      form.nome.value = row.dataset.plot;
      form.cultura.value = row.dataset.crop.charAt(0).toUpperCase() + row.dataset.crop.slice(1);
      form.cultura.dispatchEvent(new Event("change"));
      form.hectares.value = row.dataset.hectares;
      form.safra.value = row.dataset.safra || "2025/26";
      form.status.value = row.dataset.status;
      form.status.dispatchEvent(new Event("change"));
      openModal(modal);
    });

    $$("[data-close-modal]", modal).forEach((btn) => btn.addEventListener("click", () => closeModal(modal)));
    modal.addEventListener("click", (event) => {
      if (event.target === modal) closeModal(modal);
    });

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      clearErrors(form);

      const nome = form.nome.value.trim();
      const cultura = form.cultura.value;
      const hectares = Number(form.hectares.value);
      const safra = form.safra.value;
      const status = form.status.value || "Ativo";

      let hasError = false;
      if (!nome) {
        setError(form, "nome", "Informe o nome do talhão.");
        hasError = true;
      }
      if (!cultura) {
        setError(form, "cultura", "Selecione uma cultura.");
        hasError = true;
      }
      if (!hectares || hectares <= 0) {
        setError(form, "hectares", "Informe um valor de hectares maior que zero.");
        hasError = true;
      }

      if (hasError) {
        toast("error", "Falha ao salvar", "Revise os campos destacados no formulário.");
        return;
      }

      let values = { nome, cultura, hectares, safra, status };

      try {
        values = state.editingRow
          ? await api("/api/talhoes/" + state.editingRow.dataset.id, "PUT", values)
          : await api("/api/talhoes", "POST", values);
      } catch (error) {
        Object.entries(error.fields || {}).forEach(([field, message]) => setError(form, field, message));
        toast("error", "Falha ao salvar", error.message);
        return;
      }

      if (state.editingRow) {
        const row = state.editingRow;
        row.dataset.plot = values.nome;
        row.dataset.crop = values.cultura.toLowerCase();
        row.dataset.hectares = String(values.hectares);
        row.dataset.safra = values.safra;
        row.dataset.status = values.status;
        const menuId = row.querySelector("[data-popover]")?.id || `menu-${window.AgroLocal.id()}`;
        row.innerHTML = buildRowMarkup(menuId, values);
        if (!persist()) return;
        toast("success", "Talhão atualizado", `${nome} foi salvo com sucesso.`);
      } else {
        const menuId = `menu-${window.AgroLocal.id()}`;
        const row = document.createElement("tr");
        row.dataset.id = values.id;
        row.dataset.plot = values.nome;
        row.dataset.crop = values.cultura.toLowerCase();
        row.dataset.hectares = String(values.hectares);
        row.dataset.safra = values.safra;
        row.dataset.status = values.status;
        row.innerHTML = buildRowMarkup(menuId, values);
        tbody.appendChild(row);

        if (!persist()) return;
        toast("success", "Talhão salvo", `${nome} foi cadastrado com sucesso.`);
      }

      computeMetricsFromTable();
      updateActivePlotsMetric();
      updateHectaresMetric();
      closeModal(modal);
      form.reset();
      // Reaplica busca/filtro para a nova linha respeitar o estado atual.
      $("[data-plots-search]")?.dispatchEvent(new Event("input"));
    });
  }

  // Salvamos somente os campos do talhão, sem filtros, menus ou HTML.
  function restorePlots(records) {
    // O rollback recria as linhas; preservamos o contexto da edição pelo ID.
    const editingId = state.editingRow?.dataset.id;
    const tbody = $("[data-plots-table] tbody");
    tbody.replaceChildren(...records.map((record, index) => {
      const row = document.createElement("tr");
      Object.assign(row.dataset, record);
      row.innerHTML = buildRowMarkup("plot-menu-" + index, {nome: record.plot,
        cultura: record.crop.charAt(0).toUpperCase() + record.crop.slice(1), hectares: record.hectares, status: record.status});
      return row;
    }));
    state.editingRow = $$("[data-plots-table] tbody tr").find((row) => row.dataset.id === editingId) || null;
    computeMetricsFromTable(); updateHectaresMetric(); updateActivePlotsMetric();
    $("[data-plots-search]")?.dispatchEvent(new Event("input"));
  }

  function init() {
    computeMetricsFromTable();
    updateHectaresMetric();
    updateActivePlotsMetric();
    initPrepToasts();
    initFilters();
    initPlotForm();
    // A recarga sempre parte do resultado confirmado pela API.
    api("/api/talhoes").then((records) => restorePlots(records.map((record) => ({
      id: record.id, plot: record.nome, crop: record.cultura.toLowerCase(),
      hectares: record.hectares, status: record.status,
    })))).catch((error) => toast("error", "Não foi possível carregar", error.message));
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
