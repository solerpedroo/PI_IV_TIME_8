/**
 * Agro — interações da tela Atividades (dados mock híbridos)
 * --------------------------------------------------------------------------
 * Fonte única de dados: os cards do Kanban (`.kanban-card`). A visualização
 * em Lista é derivada deles a cada mudança — não existe um segundo estado
 * para manter sincronizado manualmente.
 *
 * Cobre os fluxos "C — Atividades e Estoque" do board Fluxos de Interação:
 * - Flow 1: Nova atividade (form validado → card em "Pendente" → toast)
 * - Flow 2/2b: Ações do card "⋮" → Ver detalhes / Editar
 * - Flow 3: Concluir (confirmação → card muda de coluna → toast + baixa
 *   simulada de insumo, citando a cadeia Insumo → Atividade → Talhão → Custo)
 * - Flow 4: Excluir (confirmação destrutiva) e Duplicar (reabre o formulário
 *   pré-preenchido com "(cópia)")
 * - `?talhao=` na URL (vindo de Áreas de Cultivo): filtra e mostra chip
 *   removível, replicando a tela "Atividades filtradas" do pen.dev.
 */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

  const filters = {
    query: "",
    status: "",
    tipo: "",
    day: "",
    talhao: new URLSearchParams(window.location.search).get("talhao") || "",
  };

  /** Card em edição/conclusão/exclusão no momento (contexto dos modais). */
  let activeCard = null;
  let formMode = "create"; // "create" | "edit"

  function toast(type, title, description) {
    if (window.AgroToast) window.AgroToast.show({ type, title, description });
  }

  /* ---------------------------------------------------------------------- */
  /* Modal helpers (genérico — reaproveitado pelos 4 modais desta tela)     */
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

  function initGenericModals() {
    $$(".modal-backdrop").forEach((modal) => {
      $$("[data-close-modal]", modal).forEach((btn) => btn.addEventListener("click", () => closeModal(modal)));
      modal.addEventListener("click", (event) => {
        if (event.target === modal) closeModal(modal);
      });
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Filtro por talhão vindo da URL (Áreas de Cultivo → Atividades)         */
  /* ---------------------------------------------------------------------- */
  function initTalhaoFilterChip() {
    const bar = $("[data-talhao-filter-bar]");
    const label = $("[data-talhao-filter-label]");
    const clearBtn = $("[data-clear-talhao-filter]");
    if (!bar) return;

    function refresh() {
      if (filters.talhao) {
        bar.hidden = false;
        if (label) label.textContent = `Talhão: ${filters.talhao}`;
      } else {
        bar.hidden = true;
      }
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
  /* Filtros: busca, status, tipo, dia da semana, talhão                    */
  /* ---------------------------------------------------------------------- */
  function cardMatches(card) {
    const q = filters.query;
    const text = `${card.dataset.activity} ${card.dataset.talhao}`.toLowerCase();
    if (q && !text.includes(q)) return false;
    if (filters.status && card.dataset.status !== filters.status) return false;
    if (filters.tipo && card.dataset.tipo !== filters.tipo) return false;
    if (filters.day && card.dataset.date !== filters.day) return false;
    if (filters.talhao && card.dataset.talhao !== filters.talhao) return false;
    return true;
  }

  function buildListRow(card) {
    const statusClass = {
      Pendente: "badge-warning",
      Agendada: "badge-info",
      "Em andamento": "badge-warning",
      Concluída: "badge-success",
    }[card.dataset.status] || "badge-neutral";

    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${card.dataset.activity}</td>
      <td>${card.dataset.tipo}</td>
      <td>${card.dataset.talhao}</td>
      <td>${card.dataset.date}</td>
      <td><span class="badge ${statusClass}">${card.dataset.status}</span></td>
    `;
    return tr;
  }

  function recomputeMetrics() {
    const all = $$(".kanban-card");
    const counts = { Pendente: 0, Agendada: 0, "Em andamento": 0, Concluída: 0 };
    all.forEach((card) => {
      counts[card.dataset.status] = (counts[card.dataset.status] || 0) + 1;
    });

    const total = all.length;
    const pending = counts.Pendente + counts.Agendada;
    const inProgress = counts["Em andamento"];
    const done = counts.Concluída;

    const setText = (selector, value) => {
      const el = $(selector);
      if (el) el.textContent = String(value);
    };
    setText("[data-metric='total']", total);
    setText("[data-metric='pending']", pending);
    setText("[data-metric='in-progress']", inProgress);
    setText("[data-metric='done']", done);

    $$("[data-column]").forEach((column) => {
      const name = column.dataset.column;
      const badge = $("[data-column-count]", column);
      if (badge) badge.textContent = String(counts[name] || 0);
    });
  }

  function applyFilters() {
    const cards = $$(".kanban-card");
    let visibleCount = 0;

    cards.forEach((card) => {
      const show = cardMatches(card);
      card.hidden = !show;
      if (show) visibleCount += 1;
    });

    const emptyState = $("[data-kanban-empty]");
    const board = $("[data-kanban-board]");
    if (emptyState) emptyState.hidden = visibleCount > 0;
    if (board) board.hidden = visibleCount === 0;

    // Reconstrói a lista a partir dos cards visíveis (mesma fonte de dados).
    const listBody = $("[data-list-body]");
    if (listBody) {
      listBody.innerHTML = "";
      cards.filter((c) => !c.hidden).forEach((card) => listBody.appendChild(buildListRow(card)));
    }

    recomputeMetrics();
  }

  function initFilterControls() {
    $("[data-activities-search]")?.addEventListener("input", (event) => {
      filters.query = event.target.value.trim().toLowerCase();
      applyFilters();
    });

    $("[data-filter-status]")?.addEventListener("change", (event) => {
      filters.status = event.target.value;
      applyFilters();
    });

    $("[data-filter-tipo]")?.addEventListener("change", (event) => {
      filters.tipo = event.target.value;
      applyFilters();
    });

    $$(".week-day").forEach((day) => {
      day.addEventListener("click", () => {
        const isSame = filters.day === day.dataset.day;
        $$(".week-day").forEach((d) => d.classList.remove("is-selected"));
        filters.day = isSame ? "" : day.dataset.day;
        if (!isSame) day.classList.add("is-selected");
        applyFilters();
      });
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Alternância Kanban / Lista                                             */
  /* ---------------------------------------------------------------------- */
  function initViewToggle() {
    const options = $$("[data-view-option]");
    const kanbanRow = $("[data-view-kanban]");
    const listCard = $("[data-view-list]");

    options.forEach((option) => {
      option.addEventListener("click", () => {
        options.forEach((o) => o.setAttribute("aria-pressed", "false"));
        option.setAttribute("aria-pressed", "true");
        const isKanban = option.dataset.viewOption === "kanban";
        if (kanbanRow) kanbanRow.hidden = !isKanban;
        if (listCard) listCard.hidden = isKanban;
      });
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Ações do card "⋮" (Flow 2/3/4)                                         */
  /* ---------------------------------------------------------------------- */
  function actionMenuMarkup(id, status) {
    const isDone = status === "Concluída";
    return `
      <button type="button" class="icon-btn icon-btn--sm" data-popover-trigger aria-controls="${id}" aria-expanded="false" aria-label="Mais ações">
        <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/></svg>
      </button>
      <div class="action-menu" id="${id}" data-popover role="menu">
        <button type="button" class="action-menu-item" role="menuitem" data-card-action="detail"><span class="action-menu-icon tone-info" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"/><circle cx="12" cy="12" r="3"/></svg></span><span class="action-menu-label">Ver detalhes</span></button>
        ${!isDone ? `<button type="button" class="action-menu-item" role="menuitem" data-card-action="edit"><span class="action-menu-icon tone-secondary" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg></span><span class="action-menu-label">Editar</span></button>` : ""}
        ${!isDone ? `<button type="button" class="action-menu-item" role="menuitem" data-card-action="complete"><span class="action-menu-icon tone-success" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M21.801 10A10 10 0 1 1 17 3.335"/><path d="m9 11 3 3L22 4"/></svg></span><span class="action-menu-label">Concluir</span></button>` : ""}
        <button type="button" class="action-menu-item" role="menuitem" data-card-action="duplicate"><span class="action-menu-icon tone-primary" aria-hidden="true"><svg viewBox="0 0 24 24"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg></span><span class="action-menu-label">Duplicar</span></button>
        <button type="button" class="action-menu-item is-danger" role="menuitem" data-card-action="delete"><span class="action-menu-icon tone-error" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg></span><span class="action-menu-label">Excluir</span></button>
      </div>
    `;
  }

  function buildCard(values, seq) {
    const menuId = `card-menu-${seq}`;
    const card = document.createElement("article");
    card.className = "kanban-card";
    card.tabIndex = 0;
    card.dataset.activity = values.nome;
    card.dataset.tipo = values.tipo;
    card.dataset.talhao = values.talhao;
    card.dataset.date = values.data;
    card.dataset.status = values.status;

    const badgeTone = { Pendente: "badge-warning", Agendada: "badge-info", "Em andamento": "badge-warning", Concluída: "badge-success" }[values.status] || "badge-neutral";

    card.innerHTML = `
      <div class="kanban-card-top">
        <span class="kanban-card-title">${values.nome}</span>
        <span class="badge ${badgeTone}">${values.tipo}</span>
      </div>
      <span class="kanban-card-meta"><svg viewBox="0 0 24 24"><path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"/><circle cx="12" cy="10" r="3"/></svg>${values.talhao}</span>
      <div class="kanban-card-footer">
        <span class="kanban-card-date"><svg viewBox="0 0 24 24"><path d="M8 2v4"/><path d="M16 2v4"/><rect width="18" height="18" x="3" y="4" rx="2"/><path d="M3 10h18"/></svg>${values.data}</span>
        <div class="action-menu-wrap">${actionMenuMarkup(menuId, values.status)}</div>
      </div>
    `;
    return card;
  }

  function initCardActions() {
    const board = $("[data-kanban-board]");
    if (!board) return;

    board.addEventListener("click", (event) => {
      const btn = event.target.closest("[data-card-action]");
      if (!btn) return;
      const card = btn.closest(".kanban-card");
      if (!card) return;
      activeCard = card;

      const action = btn.dataset.cardAction;
      if (action === "detail") openDetail(card);
      else if (action === "edit") openEditForm(card);
      else if (action === "complete") openCompleteConfirm(card);
      else if (action === "duplicate") openDuplicateForm(card);
      else if (action === "delete") openDeleteConfirm(card);
    });
  }

  function openDetail(card) {
    const modal = $("[data-modal='activity-detail']");
    $("[data-detail-title]", modal).textContent = card.dataset.activity;
    $("[data-detail-subtitle]", modal).textContent = `${card.dataset.talhao} · ${card.dataset.date}`;
    $("[data-detail-meta]", modal).innerHTML = `
      <div class="meta-cell"><span class="meta-cell-label">Tipo</span><span class="meta-cell-value">${card.dataset.tipo}</span></div>
      <div class="meta-cell"><span class="meta-cell-label">Talhão</span><span class="meta-cell-value">${card.dataset.talhao}</span></div>
      <div class="meta-cell"><span class="meta-cell-label">Data</span><span class="meta-cell-value">${card.dataset.date}</span></div>
      <div class="meta-cell"><span class="meta-cell-label">Status</span><span class="meta-cell-value">${card.dataset.status}</span></div>
    `;
    openModal(modal);
  }

  function openCompleteConfirm(card) {
    const modal = $("[data-modal='activity-complete']");
    $("[data-complete-desc]", modal).textContent = `${card.dataset.activity} será marcada como concluída.`;
    openModal(modal);
  }

  function openDeleteConfirm(card) {
    openModal($("[data-modal='activity-delete']"));
  }

  /* ---------------------------------------------------------------------- */
  /* Formulário (criar / editar / duplicar)                                 */
  /* ---------------------------------------------------------------------- */
  function clearErrors(form) {
    $$(".field-error", form).forEach((el) => (el.textContent = ""));
    $$("input, select", form).forEach((el) => el.removeAttribute("aria-invalid"));
  }

  function setError(form, field, message) {
    const errorEl = form.querySelector(`[data-error-for="${field}"]`);
    if (errorEl) errorEl.textContent = message;
    form.querySelector(`[name="${field}"]`)?.setAttribute("aria-invalid", "true");
  }

  function fillForm(form, values) {
    form.nome.value = values.nome;
    form.tipo.value = values.tipo;
    form.tipo.dispatchEvent(new Event("change"));
    form.talhao.value = values.talhao;
    form.talhao.dispatchEvent(new Event("change"));
    form.data.value = values.data;
  }

  function openCreateForm() {
    formMode = "create";
    activeCard = null;
    const modal = $("[data-modal='activity-form']");
    const form = $("[data-activity-form]", modal);
    form.reset();
    form.tipo.dispatchEvent(new Event("change"));
    form.talhao.dispatchEvent(new Event("change"));
    clearErrors(form);
    $("[data-activity-form-title]", modal).textContent = "Nova atividade";
    openModal(modal);
  }

  function openEditForm(card) {
    formMode = "edit";
    activeCard = card;
    const modal = $("[data-modal='activity-form']");
    const form = $("[data-activity-form]", modal);
    clearErrors(form);
    fillForm(form, {
      nome: card.dataset.activity,
      tipo: card.dataset.tipo,
      talhao: card.dataset.talhao,
      data: card.dataset.date,
    });
    $("[data-activity-form-title]", modal).textContent = "Editar atividade";
    openModal(modal);
  }

  function openDuplicateForm(card) {
    formMode = "create";
    activeCard = null;
    const modal = $("[data-modal='activity-form']");
    const form = $("[data-activity-form]", modal);
    clearErrors(form);
    fillForm(form, {
      nome: `${card.dataset.activity} (cópia)`,
      tipo: card.dataset.tipo,
      talhao: card.dataset.talhao,
      data: card.dataset.date,
    });
    $("[data-activity-form-title]", modal).textContent = "Nova atividade (cópia)";
    openModal(modal);
    toast("info", "Cópia editável criada", "Ajuste talhão e data antes de salvar.");
  }

  let cardSeq = 100;

  function initActivityForm() {
    const modal = $("[data-modal='activity-form']");
    const form = $("[data-activity-form]", modal);
    if (!modal || !form) return;

    $$("[data-open-modal='activity-form']").forEach((btn) => btn.addEventListener("click", openCreateForm));

    form.addEventListener("submit", (event) => {
      event.preventDefault();
      clearErrors(form);

      const nome = form.nome.value.trim();
      const tipo = form.tipo.value;
      const talhao = form.talhao.value;
      const data = form.data.value.trim();

      let hasError = false;
      if (!nome) { setError(form, "nome", "Informe o nome da atividade."); hasError = true; }
      if (!tipo) { setError(form, "tipo", "Selecione o tipo."); hasError = true; }
      if (!talhao) { setError(form, "talhao", "Selecione o talhão."); hasError = true; }
      if (!data) { setError(form, "data", "Informe a data."); hasError = true; }

      if (hasError) {
        toast("error", "Falha ao salvar", "Revise os campos destacados no formulário.");
        return;
      }

      if (formMode === "edit" && activeCard) {
        activeCard.dataset.activity = nome;
        activeCard.dataset.tipo = tipo;
        activeCard.dataset.talhao = talhao;
        activeCard.dataset.date = data;
        $(".kanban-card-title", activeCard).textContent = nome;
        $(".kanban-card-top .badge", activeCard).textContent = tipo;
        $(".kanban-card-meta", activeCard).lastChild.textContent = talhao;
        $(".kanban-card-date", activeCard).lastChild.textContent = data;
        toast("success", "Atividade atualizada", `${nome} foi salva com sucesso.`);
      } else {
        cardSeq += 1;
        const card = buildCard({ nome, tipo, talhao, data, status: "Pendente" }, cardSeq);
        $("[data-column='Pendente'] [data-column-cards]").appendChild(card);
        toast("success", "Atividade criada", `${nome} agendada em ${talhao}.`);
      }

      closeModal(modal);
      form.reset();
      applyFilters();
    });
  }

  function initCompleteConfirm() {
    const modal = $("[data-modal='activity-complete']");
    $("[data-confirm-complete]", modal)?.addEventListener("click", () => {
      if (!activeCard) return;
      const name = activeCard.dataset.activity;
      const talhao = activeCard.dataset.talhao;

      activeCard.dataset.status = "Concluída";
      $(".kanban-card-top .badge", activeCard).className = "badge badge-success";
      const menuId = $("[data-popover]", activeCard)?.id || `card-menu-${++cardSeq}`;
      $(".action-menu-wrap", activeCard).innerHTML = actionMenuMarkup(menuId, "Concluída");
      $("[data-column='Concluída'] [data-column-cards]").appendChild(activeCard);

      closeModal(modal);
      toast(
        "success",
        "Atividade concluída",
        `${name} marcada como concluída · baixa de insumo lançada no estoque de ${talhao}.`
      );
      applyFilters();
      activeCard = null;
    });
  }

  function initDeleteConfirm() {
    const modal = $("[data-modal='activity-delete']");
    $("[data-confirm-delete]", modal)?.addEventListener("click", () => {
      if (!activeCard) return;
      const name = activeCard.dataset.activity;
      activeCard.remove();
      closeModal(modal);
      toast("info", "Atividade excluída", `${name} foi removida da lista.`);
      applyFilters();
      activeCard = null;
    });
  }

  /** Botões de módulo/ação fora do escopo desta entrega (ex.: Exportar). */
  function initPrepToasts() {
    $$("[data-prep-toast]").forEach((el) => {
      el.addEventListener("click", () => {
        toast("info", "Módulo em preparação", el.dataset.prepToast);
      });
    });
  }

  function init() {
    initGenericModals();
    initPrepToasts();
    initTalhaoFilterChip();
    initFilterControls();
    initViewToggle();
    initCardActions();
    initActivityForm();
    initCompleteConfirm();
    initDeleteConfirm();
    applyFilters();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
