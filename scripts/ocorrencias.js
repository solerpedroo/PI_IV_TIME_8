/**
 * Agro — interações da tela Ocorrências (dados mock híbridos)
 * --------------------------------------------------------------------------
 * Cobre o Flow "Registrar ocorrência" e o Flow "Ações da ocorrência" do
 * board Fluxos de Interação (D — Ocorrências, Custos e Relatórios):
 * - Registrar ocorrência: formulário com tipo, talhão e prioridade
 *   (segmented control) → validação (G3) → nova linha "Aberta" → toast.
 * - Ficha de detalhe: meta grid + linha do tempo + campo de nova
 *   atualização (publica um evento na timeline sem fechar o modal).
 * - Resolver (confirmação → status "Resolvida" + evento na timeline) e
 *   Excluir (confirmação destrutiva), acessíveis tanto pelo menu "⋮" da
 *   linha quanto pelos botões da própria ficha de detalhe.
 * - `?talhao=` na URL filtra a tabela e mostra um chip removível (mesmo
 *   padrão usado em atividades.js).
 *
 * Simplificação assumida: "Resolvidas (Mês)" reflete a contagem atual de
 * linhas com status "Resolvida" na tabela (não há um recorte histórico por
 * mês nesta camada mock — a API deve fornecer esse agregado futuramente).
 */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

  const filters = {
    query: "",
    talhao: new URLSearchParams(window.location.search).get("talhao") || "",
  };

  /** Timeline por linha (Map<tr, Array<{time, title, desc}>>) — estado só de UI. */
  const timelines = new Map();
  let activeRow = null;
  let rowSeq = 100;

  function toast(type, title, description) {
    if (window.AgroToast) window.AgroToast.show({ type, title, description });
  }

  /* ---------------------------------------------------------------------- */
  /* Modal helpers                                                          */
  /* ---------------------------------------------------------------------- */
  function openModal(modal) {
    if (!modal) return;
    modal.classList.add("is-open");
    modal.setAttribute("aria-hidden", "false");
    modal.querySelector("input, select, textarea, button")?.focus();
  }

  function closeModal(modal) {
    if (!modal) return;
    modal.classList.remove("is-open");
    modal.setAttribute("aria-hidden", "true");
  }

  function initGenericModals() {
    $$(".modal-backdrop").forEach((modal) => {
      $$("[data-close-modal]", modal).forEach((btn) => btn.addEventListener("click", () => closeModal(modal)));
      modal.addEventListener("click", (event) => {
        if (event.target === modal) closeModal(modal);
      });
    });
  }

  function clearErrors(form) {
    $$(".field-error", form).forEach((el) => (el.textContent = ""));
    $$("input, select", form).forEach((el) => el.removeAttribute("aria-invalid"));
  }

  function setError(form, field, message) {
    const errorEl = form.querySelector(`[data-error-for="${field}"]`);
    if (errorEl) errorEl.textContent = message;
    form.querySelector(`[name="${field}"]`)?.setAttribute("aria-invalid", "true");
  }

  /* ---------------------------------------------------------------------- */
  /* Filtro por talhão vindo da URL                                         */
  /* ---------------------------------------------------------------------- */
  function initTalhaoFilterChip() {
    const bar = $("[data-talhao-filter-bar]");
    const label = $("[data-talhao-filter-label]");
    const clearBtn = $("[data-clear-talhao-filter]");
    if (!bar) return;

    function refresh() {
      bar.hidden = !filters.talhao;
      if (filters.talhao && label) label.textContent = `Talhão: ${filters.talhao}`;
    }

    clearBtn?.addEventListener("click", () => {
      filters.talhao = "";
      const url = new URL(window.location.href);
      url.searchParams.delete("talhao");
      window.history.replaceState({}, "", url);
      refresh();
      applyFilters();
    });

    refresh();
  }

  /* ---------------------------------------------------------------------- */
  /* Busca + filtro por talhão                                              */
  /* ---------------------------------------------------------------------- */
  function applyFilters() {
    const rows = $$("[data-occ-table] tbody tr");
    const q = filters.query;
    let visible = 0;

    rows.forEach((row) => {
      const matchesQuery = !q || row.dataset.occ.toLowerCase().includes(q);
      const matchesTalhao = !filters.talhao || row.dataset.talhao === filters.talhao;
      const show = matchesQuery && matchesTalhao;
      row.hidden = !show;
      if (show) visible += 1;
    });

    const empty = $("[data-occ-empty]");
    const table = $("[data-occ-table]");
    if (empty) empty.hidden = visible > 0;
    if (table) table.hidden = visible === 0;
  }

  function initSearch() {
    $("[data-occ-search]")?.addEventListener("input", (event) => {
      filters.query = event.target.value.trim().toLowerCase();
      applyFilters();
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Métricas (recalculadas a partir do estado real da tabela)              */
  /* ---------------------------------------------------------------------- */
  function recomputeMetrics() {
    const rows = $$("[data-occ-table] tbody tr");
    const counts = { Aberta: 0, "Em análise": 0, Resolvida: 0 };
    let severidadeAlta = 0;

    rows.forEach((row) => {
      counts[row.dataset.status] = (counts[row.dataset.status] || 0) + 1;
      if (row.dataset.severity === "Alta" && row.dataset.status !== "Resolvida") severidadeAlta += 1;
    });

    const setText = (selector, value) => {
      const el = $(selector);
      if (el) el.textContent = String(value);
    };
    setText("[data-metric='abertas']", counts.Aberta);
    setText("[data-metric='em-analise']", counts["Em análise"]);
    setText("[data-metric='resolvidas']", counts.Resolvida);
    setText("[data-metric='severidade-alta']", severidadeAlta);
  }

  /* ---------------------------------------------------------------------- */
  /* Menu de ações por linha                                                */
  /* ---------------------------------------------------------------------- */
  function actionMenuMarkup(status) {
    const isResolved = status === "Resolvida";
    return `
      <button type="button" class="action-menu-item" role="menuitem" data-row-action="detail"><span class="action-menu-icon tone-info" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"/><circle cx="12" cy="12" r="3"/></svg></span><span class="action-menu-label">Ver detalhes</span></button>
      ${!isResolved ? `<button type="button" class="action-menu-item" role="menuitem" data-row-action="resolve"><span class="action-menu-icon tone-success" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M21.801 10A10 10 0 1 1 17 3.335"/><path d="m9 11 3 3L22 4"/></svg></span><span class="action-menu-label">Marcar resolvida</span></button>` : ""}
      <button type="button" class="action-menu-item is-danger" role="menuitem" data-row-action="delete"><span class="action-menu-icon tone-error" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg></span><span class="action-menu-label">Excluir</span></button>
    `;
  }

  function populateActionMenus() {
    $$("[data-occ-table] tbody tr").forEach((row) => {
      const menu = $("[data-popover]", row);
      if (menu) menu.innerHTML = actionMenuMarkup(row.dataset.status);
    });
  }

  function refreshRowMenu(row) {
    const menu = $("[data-popover]", row);
    if (menu) menu.innerHTML = actionMenuMarkup(row.dataset.status);
  }

  /* ---------------------------------------------------------------------- */
  /* Timeline por ocorrência                                                */
  /* ---------------------------------------------------------------------- */
  function timelineFor(row) {
    if (!timelines.has(row)) {
      timelines.set(row, [
        { time: `${row.dataset.date} · registro`, title: "Ocorrência aberta", desc: `${row.dataset.tipo} identificado(a) em ${row.dataset.talhao}.` },
      ]);
    }
    return timelines.get(row);
  }

  function renderTimeline(row) {
    const host = $("[data-detail-timeline]");
    const entries = timelineFor(row);
    host.innerHTML = entries
      .map(
        (entry, index) => `
        <div class="timeline-entry ${index === entries.length - 1 ? "is-latest" : ""}">
          <div class="timeline-rail"><span class="timeline-dot"></span><span class="timeline-line"></span></div>
          <div class="timeline-body">
            <span class="timeline-time">${entry.time}</span>
            <p class="timeline-title">${entry.title}</p>
            <p class="timeline-desc">${entry.desc}</p>
          </div>
        </div>`
      )
      .join("");
  }

  /* ---------------------------------------------------------------------- */
  /* Modal de detalhe                                                       */
  /* ---------------------------------------------------------------------- */
  function severityBadgeClass(sev) {
    if (sev === "Alta") return "badge-error";
    if (sev === "Média") return "badge-warning";
    return "badge-neutral";
  }

  function statusBadgeClass(status) {
    if (status === "Aberta") return "badge-warning";
    if (status === "Em análise") return "badge-info";
    return "badge-success";
  }

  function openDetail(row) {
    activeRow = row;
    const modal = $("[data-modal='occ-detail']");
    $("[data-detail-title]", modal).textContent = row.dataset.occ;
    $("[data-detail-subtitle]", modal).textContent = `${row.dataset.talhao} · ${row.dataset.date}`;
    $("[data-detail-badges]", modal).innerHTML = `
      <span class="badge ${severityBadgeClass(row.dataset.severity)}">${row.dataset.severity}</span>
      <span class="badge ${statusBadgeClass(row.dataset.status)}">${row.dataset.status}</span>
    `;
    $("[data-detail-meta]", modal).innerHTML = `
      <div class="meta-cell"><span class="meta-cell-label">Tipo</span><span class="meta-cell-value">${row.dataset.tipo}</span></div>
      <div class="meta-cell"><span class="meta-cell-label">Talhão</span><span class="meta-cell-value">${row.dataset.talhao}</span></div>
      <div class="meta-cell"><span class="meta-cell-label">Registrada</span><span class="meta-cell-value">${row.dataset.date}</span></div>
      <div class="meta-cell"><span class="meta-cell-label">Status</span><span class="meta-cell-value">${row.dataset.status}</span></div>
    `;
    renderTimeline(row);

    const resolveBtn = $("[data-detail-resolve]", modal);
    if (resolveBtn) resolveBtn.hidden = row.dataset.status === "Resolvida";

    $("[data-update-text]", modal).value = "";
    openModal(modal);
  }

  function initDetailModal() {
    document.addEventListener("click", (event) => {
      const btn = event.target.closest("[data-row-action='detail'], .col-occ-name");
      if (!btn) return;
      const row = btn.closest("tr");
      if (row) openDetail(row);
    });

    const modal = $("[data-modal='occ-detail']");

    $("[data-detail-publish]", modal)?.addEventListener("click", () => {
      const textarea = $("[data-update-text]", modal);
      const text = textarea.value.trim();
      if (!text) {
        toast("error", "Falha ao publicar", "Escreva uma atualização antes de publicar.");
        return;
      }
      timelineFor(activeRow).push({ time: "Agora", title: "Atualização publicada", desc: text });
      renderTimeline(activeRow);
      textarea.value = "";
      toast("success", "Atualização publicada", "A linha do tempo da ocorrência foi atualizada.");
    });

    $("[data-detail-resolve]", modal)?.addEventListener("click", () => {
      const confirmModal = $("[data-modal='occ-resolve']");
      $("[data-resolve-desc]", confirmModal).textContent = `${activeRow.dataset.occ} será marcada como resolvida.`;
      openModal(confirmModal);
    });

    $("[data-detail-delete]", modal)?.addEventListener("click", () => {
      const confirmModal = $("[data-modal='occ-delete']");
      openModal(confirmModal);
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Resolver (linha ou ficha de detalhe)                                   */
  /* ---------------------------------------------------------------------- */
  function initResolve() {
    document.addEventListener("click", (event) => {
      const btn = event.target.closest("[data-row-action='resolve']");
      if (!btn) return;
      activeRow = btn.closest("tr");
      const confirmModal = $("[data-modal='occ-resolve']");
      $("[data-resolve-desc]", confirmModal).textContent = `${activeRow.dataset.occ} será marcada como resolvida.`;
      openModal(confirmModal);
    });

    $("[data-confirm-resolve]")?.addEventListener("click", () => {
      const row = activeRow;
      if (!row) return;

      row.dataset.status = "Resolvida";
      $("[data-status-badge]", row).className = "badge badge-success";
      $("[data-status-badge]", row).textContent = "Resolvida";
      refreshRowMenu(row);

      timelineFor(row).push({ time: "Agora", title: "Ocorrência resolvida", desc: "Status atualizado para Resolvida." });

      closeModal($("[data-modal='occ-resolve']"));
      closeModal($("[data-modal='occ-detail']"));
      recomputeMetrics();
      toast("success", "Ocorrência resolvida", `${row.dataset.occ} foi marcada como resolvida.`);
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Excluir (linha ou ficha de detalhe)                                    */
  /* ---------------------------------------------------------------------- */
  function initDelete() {
    document.addEventListener("click", (event) => {
      const btn = event.target.closest("[data-row-action='delete']");
      if (!btn) return;
      activeRow = btn.closest("tr");
      openModal($("[data-modal='occ-delete']"));
    });

    $("[data-confirm-delete]")?.addEventListener("click", () => {
      const row = activeRow;
      if (!row) return;
      const name = row.dataset.occ;
      timelines.delete(row);
      row.remove();

      closeModal($("[data-modal='occ-delete']"));
      closeModal($("[data-modal='occ-detail']"));
      recomputeMetrics();
      applyFilters();
      toast("info", "Ocorrência excluída", `${name} foi removida do registro.`);
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Formulário: registrar ocorrência                                       */
  /* ---------------------------------------------------------------------- */
  function initPrioritySegmented(form) {
    const options = $$(".segmented-option", form);
    const hidden = form.querySelector("[name='prioridade']");
    options.forEach((opt) => {
      opt.addEventListener("click", () => {
        options.forEach((o) => o.setAttribute("aria-pressed", "false"));
        opt.setAttribute("aria-pressed", "true");
        hidden.value = opt.dataset.priority;
      });
    });
  }

  function buildRow(values) {
    const menuId = `menu-o${++rowSeq}`;
    const row = document.createElement("tr");
    row.dataset.occ = values.titulo;
    row.dataset.tipo = values.tipo;
    row.dataset.talhao = values.talhao;
    row.dataset.date = "hoje";
    row.dataset.severity = values.prioridade;
    row.dataset.status = "Aberta";

    row.innerHTML = `
      <td class="col-occ-name">${values.titulo}</td>
      <td class="col-muted">${values.tipo}</td>
      <td class="col-muted">${values.talhao}</td>
      <td class="col-muted">hoje</td>
      <td><span class="badge ${severityBadgeClass(values.prioridade)}" data-severity-badge>${values.prioridade}</span></td>
      <td><span class="badge badge-warning" data-status-badge>Aberta</span></td>
      <td class="col-actions"><div class="action-menu-wrap"><button type="button" class="icon-btn icon-btn--sm" data-popover-trigger aria-controls="${menuId}" aria-expanded="false" aria-label="Mais ações — ${values.titulo}"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/></svg></button><div class="action-menu" id="${menuId}" data-popover role="menu">${actionMenuMarkup("Aberta")}</div></div></td>
    `;
    return row;
  }

  function initOccForm() {
    const modal = $("[data-modal='occ-form']");
    const form = $("[data-occ-form]", modal);
    if (!modal || !form) return;

    initPrioritySegmented(form);

    $$("[data-open-modal='occ-form']").forEach((btn) => {
      btn.addEventListener("click", () => {
        clearErrors(form);
        openModal(modal);
      });
    });

    form.addEventListener("submit", (event) => {
      event.preventDefault();
      clearErrors(form);

      const titulo = form.titulo.value.trim();
      const tipo = form.tipo.value;
      const talhao = form.talhao.value;
      const prioridade = form.prioridade.value || "Baixa";

      let hasError = false;
      if (!titulo) { setError(form, "titulo", "Informe um título."); hasError = true; }
      if (!tipo) { setError(form, "tipo", "Selecione o tipo."); hasError = true; }
      if (!talhao) { setError(form, "talhao", "Selecione o talhão."); hasError = true; }

      if (hasError) {
        toast("error", "Falha ao salvar", "Revise os campos destacados no formulário.");
        return;
      }

      const row = buildRow({ titulo, tipo, talhao, prioridade });
      $("[data-occ-table] tbody").appendChild(row);

      closeModal(modal);
      form.reset();
      $$(".segmented-option", form).forEach((o, i) => o.setAttribute("aria-pressed", i === 0 ? "true" : "false"));
      form.prioridade.value = "Baixa";

      recomputeMetrics();
      applyFilters();
      toast("success", "Ocorrência registrada", `${titulo} · ${talhao} · prioridade ${prioridade.toLowerCase()}.`);
    });
  }

  function init() {
    populateActionMenus();
    initGenericModals();
    initTalhaoFilterChip();
    initSearch();
    initDetailModal();
    initResolve();
    initDelete();
    initOccForm();
    applyFilters();
    recomputeMetrics();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
