/* Interações da tela de Custos: filtros, lançamentos, orçamento e exportação. */
(() => {
  "use strict";

  /* Atalhos pequenos mantêm a leitura das funções de UI objetiva. */
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const money = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL", maximumFractionDigits: 2 });

  /* Os dados permanecem em memória, como nos demais módulos estáticos. */
  const state = {
    budget: 520000,
    filter: "Todos",
    search: "",
    plot: new URLSearchParams(window.location.search).get("talhao") || "",
    period: "Jan — Ago 2026",
    entries: [
      { id: 1, description: "Glifosato 20L", category: "Insumos", plot: "T-03 Leste", date: "2026-08-05", value: 2840 },
      { id: 2, description: "Salário operador — Jul", category: "Mão de Obra", plot: "Propriedade", date: "2026-08-01", value: 4200 },
      { id: 3, description: "Manutenção pulverizador", category: "Maquinário", plot: "T-01 Norte", date: "2026-07-28", value: 1650 },
      { id: 4, description: "Diesel — 500 L", category: "Combustível", plot: "Propriedade", date: "2026-07-25", value: 3100 },
      { id: 5, description: "Sementes de soja — 2 t", category: "Insumos", plot: "T-01 Norte", date: "2026-07-20", value: 8400 }
    ]
  };

  /* Totais anteriores aos lançamentos recentes, vindos do mock da safra. */
  const categoryBase = { Insumos: 187160, "Mão de Obra": 120600, Maquinário: 87550, Combustível: 39000, Outros: 27800 };

  function toast(type, title, description) {
    window.AgroToast?.show({ type, title, description });
  }

  function dateToISO(date) {
    return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, "0"), String(date.getDate()).padStart(2, "0")].join("-");
  }

  function displayDate(value) {
    if (!value) return "Selecione a data";
    return new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric" }).format(new Date(`${value}T12:00:00`));
  }

  function initDatePicker() {
    const input = $("#cost-date");
    if (!input || $("[data-open-date-modal]")) return;
    input.type = "hidden";
    const trigger = document.createElement("button");
    trigger.type = "button";
    trigger.className = "date-picker-trigger";
    trigger.dataset.openDateModal = "";
    trigger.setAttribute("aria-haspopup", "dialog");
    trigger.setAttribute("aria-controls", "date-form");
    trigger.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><rect width="18" height="18" x="3" y="4" rx="2"/><path d="M16 2v4"/><path d="M8 2v4"/><path d="M3 10h18"/></svg><span data-date-label>Selecione a data</span>';
    input.parentNode.insertBefore(trigger, input);

    const modal = document.createElement("div");
    modal.className = "modal-backdrop";
    modal.dataset.modal = "date-form";
    modal.setAttribute("aria-hidden", "true");
    modal.setAttribute("role", "presentation");
    modal.innerHTML = '<div class="modal calendar-modal" role="dialog" aria-modal="true" aria-labelledby="date-form-title"><div class="calendar-modal-header"><div><h2 id="date-form-title">Selecionar data</h2><p data-calendar-selected>Escolha uma data para o lançamento.</p></div><button class="icon-btn icon-btn--sm" type="button" data-calendar-close aria-label="Fechar calendário"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"/></svg></button></div><div class="calendar-toolbar"><button class="icon-btn icon-btn--sm" type="button" data-calendar-prev aria-label="Mês anterior"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 18-6-6 6-6"/></svg></button><strong data-calendar-month></strong><button class="icon-btn icon-btn--sm" type="button" data-calendar-next aria-label="Próximo mês"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg></button></div><div class="calendar-weekdays" aria-hidden="true"><span>Dom</span><span>Seg</span><span>Ter</span><span>Qua</span><span>Qui</span><span>Sex</span><span>Sáb</span></div><div class="calendar-grid" data-calendar-grid role="grid" aria-label="Calendário"></div><div class="calendar-footer"><button class="btn btn-ghost" type="button" data-calendar-clear>Limpar</button><button class="btn btn-outline" type="button" data-calendar-today>Hoje</button></div></div>';
    document.body.appendChild(modal);
    let viewDate = input.value ? new Date(`${input.value}T12:00:00`) : new Date();

    function syncLabel() {
      $("[data-date-label]", trigger).textContent = displayDate(input.value);
      $("[data-calendar-selected]", modal).textContent = input.value ? `Data selecionada: ${displayDate(input.value)}` : "Escolha uma data para o lançamento.";
    }

    function renderCalendar() {
      const monthLabel = new Intl.DateTimeFormat("pt-BR", { month: "long", year: "numeric" }).format(viewDate);
      $("[data-calendar-month]", modal).textContent = monthLabel.charAt(0).toUpperCase() + monthLabel.slice(1);
      const grid = $("[data-calendar-grid]", modal);
      const first = new Date(viewDate.getFullYear(), viewDate.getMonth(), 1);
      grid.innerHTML = "";
      for (let index = 0; index < 42; index += 1) {
        const offset = index - first.getDay() + 1;
        const date = new Date(viewDate.getFullYear(), viewDate.getMonth(), offset);
        const button = document.createElement("button");
        button.type = "button";
        button.className = "calendar-day";
        button.textContent = String(date.getDate());
        button.dataset.calendarDate = dateToISO(date);
        button.setAttribute("role", "gridcell");
        if (date.getMonth() !== viewDate.getMonth()) button.classList.add("is-outside");
        if (dateToISO(date) === input.value) button.classList.add("is-selected");
        if (dateToISO(date) === dateToISO(new Date())) button.classList.add("is-today");
        button.addEventListener("click", () => {
          input.value = button.dataset.calendarDate;
          input.dispatchEvent(new Event("change", { bubbles: true }));
          syncLabel();
          closeCalendar();
        });
        grid.appendChild(button);
      }
    }

    function openCalendar() {
      viewDate = input.value ? new Date(`${input.value}T12:00:00`) : new Date();
      renderCalendar();
      syncLabel();
      modal.classList.add("is-open");
      modal.setAttribute("aria-hidden", "false");
    }

    function closeCalendar() {
      modal.classList.remove("is-open");
      modal.setAttribute("aria-hidden", "true");
      trigger.focus();
    }

    trigger.addEventListener("click", openCalendar);
    $("[data-calendar-prev]", modal).addEventListener("click", () => { viewDate.setMonth(viewDate.getMonth() - 1); renderCalendar(); });
    $("[data-calendar-next]", modal).addEventListener("click", () => { viewDate.setMonth(viewDate.getMonth() + 1); renderCalendar(); });
    $("[data-calendar-clear]", modal).addEventListener("click", () => { input.value = ""; input.dispatchEvent(new Event("change", { bubbles: true })); syncLabel(); closeCalendar(); });
    $("[data-calendar-today]", modal).addEventListener("click", () => { input.value = dateToISO(new Date()); input.dispatchEvent(new Event("change", { bubbles: true })); syncLabel(); closeCalendar(); });
    $("[data-calendar-close]", modal).addEventListener("click", closeCalendar);
    modal.addEventListener("click", (event) => { if (event.target === modal) closeCalendar(); });
    input.addEventListener("change", syncLabel);
    syncLabel();
  }

  function formatDate(value) {
    if (!value) return "—";
    return new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short" }).format(new Date(`${value}T12:00:00`)).replace(".", "");
  }

  function formatCompact(value) {
    return value >= 1000 ? `R$ ${(value / 1000).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} mil` : money.format(value);
  }

  function totalSpent() {
    return Object.values(categoryBase).reduce((total, value) => total + value, 0) + state.entries.reduce((total, entry) => total + entry.value, 0);
  }

  function visibleEntries() {
    return state.entries.filter((entry) => {
      const matchesCategory = state.filter === "Todos" || entry.category === state.filter;
      const matchesPlot = !state.plot || entry.plot === state.plot;
      const query = state.search.toLowerCase();
      const matchesSearch = !query || `${entry.description} ${entry.category} ${entry.plot}`.toLowerCase().includes(query);
      return matchesCategory && matchesPlot && matchesSearch;
    });
  }

  function updateMetrics() {
    const total = totalSpent();
    const budgetPercent = Math.min(100, (total / state.budget) * 100);
    const remaining = Math.max(0, state.budget - total);
    const totalEl = $("[data-cost-total]");
    const hectareEl = $("[data-cost-hectare]");
    const spentEl = $("[data-budget-spent]");
    const percentEl = $("[data-budget-percent]");
    const remainingEl = $("[data-budget-remaining]");
    const progressEl = $("[data-budget-progress]");
    if (totalEl) totalEl.textContent = formatCompact(total);
    if (hectareEl) hectareEl.textContent = money.format(total / 1284);
    if (spentEl) spentEl.textContent = money.format(total);
    if (percentEl) percentEl.textContent = `${Math.round(budgetPercent)}% utilizado`;
    if (remainingEl) remainingEl.textContent = `${money.format(remaining)} restantes`;
    if (progressEl) progressEl.style.width = `${budgetPercent}%`;
    ["Insumos", "Mão de Obra"].forEach((category) => {
      const categoryTotal = categoryBase[category] + state.entries.filter((entry) => entry.category === category).reduce((sum, entry) => sum + entry.value, 0);
      const element = $(`[data-cost-category="${category}"]`);
      if (element) element.textContent = formatCompact(categoryTotal);
    });
  }

  function renderChart() {
    const chart = $("[data-cost-chart]");
    if (!chart) return;
    const categories = ["Insumos", "Mão de Obra", "Maquinário", "Combustível", "Outros"];
    const totals = categories.map((category) => state.entries.filter((entry) => entry.category === category).reduce((sum, entry) => sum + entry.value, 0));
    const baseTotals = categories.map((category) => categoryBase[category] || 0);
    const max = Math.max(...baseTotals.map((value, index) => value + totals[index]), 1);
    chart.innerHTML = categories.map((category, index) => {
      const value = baseTotals[index] + totals[index];
      const cssClass = { "Mão de Obra": "is-labor", Maquinário: "is-machinery", Combustível: "is-fuel", Outros: "is-other" }[category] || "";
      return `<div class="cost-bar-row"><div class="cost-bar-heading"><span>${category}</span><strong>${formatCompact(value)}</strong></div><div class="cost-bar-track"><span class="cost-bar-fill ${cssClass}" style="width:${Math.max(4, (value / max) * 100)}%"></span></div></div>`;
    }).join("");
  }

  function actionMarkup(entry) {
    return `<div class="action-menu-wrap"><button class="icon-btn icon-btn--sm" type="button" data-popover-trigger aria-controls="cost-menu-${entry.id}" aria-expanded="false" aria-label="Ações de ${entry.description}"><svg viewBox="0 0 24 24"><circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/></svg></button><div class="action-menu" id="cost-menu-${entry.id}" data-popover role="menu"><button class="action-menu-item" type="button" role="menuitem" data-edit-cost="${entry.id}"><span class="action-menu-icon tone-primary"><svg viewBox="0 0 24 24"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg></span><span class="action-menu-label">Editar</span></button><button class="action-menu-item is-danger" type="button" role="menuitem" data-delete-cost="${entry.id}"><span class="action-menu-icon tone-error"><svg viewBox="0 0 24 24"><path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="m19 6-1 14H6L5 6"/></svg></span><span class="action-menu-label">Excluir</span></button></div></div>`;
  }

  function renderTable() {
    const tbody = $("[data-cost-table] tbody");
    const table = $("[data-cost-table]");
    const empty = $("[data-cost-empty]");
    const count = $("[data-cost-count]");
    const context = $("[data-cost-context]");
    if (!tbody) return;
    const entries = visibleEntries();
    tbody.innerHTML = entries.map((entry) => `<tr><td><span class="cost-row-description">${entry.description}</span></td><td><span class="badge badge-neutral">${entry.category}</span></td><td><span class="cost-row-muted">${entry.plot}</span></td><td><span class="cost-row-muted">${formatDate(entry.date)}</span></td><td class="cost-value">${money.format(entry.value)}</td><td>${actionMarkup(entry)}</td></tr>`).join("");
    table.hidden = entries.length === 0;
    empty.hidden = entries.length > 0;
    if (count) count.textContent = `${entries.length} lançamento${entries.length === 1 ? "" : "s"}`;
    if (context) {
      context.hidden = !state.plot;
      context.textContent = state.plot ? `Talhão: ${state.plot}` : "";
    }
  }

  function clearErrors(form) {
    $$(".field-error", form).forEach((error) => { error.textContent = ""; });
    $$('[aria-invalid="true"]', form).forEach((control) => control.removeAttribute("aria-invalid"));
  }

  function setError(form, name, message) {
    const control = form.elements[name];
    const error = $(`[data-error-for="${name}"]`, form);
    if (control) control.setAttribute("aria-invalid", "true");
    if (error) error.textContent = message;
  }

  function openCostModal(entry = null) {
    const modal = $("[data-modal='cost-form']");
    const form = $("[data-cost-form]");
    if (!modal || !form) return;
    form.reset();
    clearErrors(form);
    form.dataset.editingId = entry ? String(entry.id) : "";
    if (entry) {
      form.elements.description.value = entry.description;
      form.elements.category.value = entry.category;
      form.elements.category.dispatchEvent(new Event("change"));
      form.elements.value.value = entry.value;
      form.elements.date.value = entry.date;
      form.elements.plot.value = entry.plot;
      form.elements.plot.dispatchEvent(new Event("change"));
      $("#cost-form-title").textContent = "Editar custo";
    } else {
      form.elements.date.value = new Date().toISOString().slice(0, 10);
      $("#cost-form-title").textContent = "Registrar custo";
    }
    form.elements.date.dispatchEvent(new Event("change", { bubbles: true }));
    modal.classList.add("is-open");
    modal.setAttribute("aria-hidden", "false");
    form.elements.description.focus();
  }

  function closeCostModal() {
    const modal = $("[data-modal='cost-form']");
    if (!modal) return;
    modal.classList.remove("is-open");
    modal.setAttribute("aria-hidden", "true");
  }

  function setModalState(name, open) {
    const modal = $(`[data-modal="${name}"]`);
    if (!modal) return;
    modal.classList.toggle("is-open", open);
    modal.setAttribute("aria-hidden", String(!open));
  }

  function initBudgetModal() {
    const form = $("[data-budget-form]");
    if (!form) return;
    $("[data-budget-edit]")?.addEventListener("click", () => {
      form.elements.budget.value = state.budget;
      $("[data-error-for='budget']", form).textContent = "";
      setModalState("budget-form", true);
      form.elements.budget.focus();
    });
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      const budget = Number(form.elements.budget.value);
      const error = $("[data-error-for='budget']", form);
      if (!budget || budget <= 0) {
        form.elements.budget.setAttribute("aria-invalid", "true");
        error.textContent = "Informe um valor maior que zero.";
        toast("error", "Orçamento inválido", "Revise o valor informado.");
        return;
      }
      form.elements.budget.removeAttribute("aria-invalid");
      state.budget = budget;
      $("[data-budget-total]").textContent = money.format(budget);
      updateMetrics();
      setModalState("budget-form", false);
      toast("success", "Orçamento atualizado", "O acompanhamento da safra foi recalculado.");
    });
  }

  function initPeriodModal() {
    const modal = $("[data-modal='period-form']");
    if (!modal) return;
    $("[data-open-period-modal]")?.addEventListener("click", () => {
      $$('[data-period-option]', modal).forEach((item) => {
        const selected = item.dataset.periodOption === state.period;
        item.classList.toggle("is-selected", selected);
        item.setAttribute("aria-selected", String(selected));
      });
      setModalState("period-form", true);
    });
    $$('[data-period-option]', modal).forEach((option) => option.addEventListener("click", () => {
      $$('[data-period-option]', modal).forEach((item) => {
        const selected = item === option;
        item.classList.toggle("is-selected", selected);
        item.setAttribute("aria-selected", String(selected));
      });
    }));
    $("[data-confirm-period]", modal)?.addEventListener("click", () => {
      const selected = $("[data-period-option].is-selected", modal);
      if (!selected) return;
      state.period = selected.dataset.periodOption;
      $("[data-period-label]").textContent = state.period;
      $("[data-cost-period]").textContent = state.period;
      setModalState("period-form", false);
      toast("success", "Período atualizado", `Exibindo ${state.period.toLowerCase()}.`);
    });
  }

  function initForm() {
    const form = $("[data-cost-form]");
    if (!form) return;
    $("[data-open-cost-modal]")?.addEventListener("click", () => openCostModal());
    $$('[data-close-modal]', $("[data-modal='cost-form']")).forEach((button) => button.addEventListener("click", closeCostModal));
    $("[data-modal='cost-form']")?.addEventListener("click", (event) => { if (event.target === event.currentTarget) closeCostModal(); });
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      clearErrors(form);
      const description = form.elements.description.value.trim();
      const category = form.elements.category.value;
      const value = Number(form.elements.value.value);
      const date = form.elements.date.value;
      const plot = form.elements.plot.value;
      let invalid = false;
      if (!description) { setError(form, "description", "Informe uma descrição."); invalid = true; }
      if (!category) { setError(form, "category", "Selecione uma categoria."); invalid = true; }
      if (!value || value <= 0) { setError(form, "value", "Informe um valor maior que zero."); invalid = true; }
      if (!date) { setError(form, "date", "Informe a data do lançamento."); invalid = true; }
      if (invalid) { toast("error", "Falha ao salvar", "Revise os campos destacados."); return; }
      const editingId = Number(form.dataset.editingId);
      const entry = { id: editingId || Date.now(), description, category, value, date, plot };
      if (editingId) {
        const index = state.entries.findIndex((item) => item.id === editingId);
        if (index >= 0) state.entries[index] = entry;
        toast("success", "Custo atualizado", `${description} foi atualizado.`);
      } else {
        state.entries.unshift(entry);
        toast("success", "Custo registrado", `${description} foi adicionado aos lançamentos.`);
      }
      closeCostModal();
      updateMetrics();
      renderChart();
      renderTable();
    });
  }

  function initFilters() {
    const search = $("[data-cost-search]");
    search?.addEventListener("input", () => { state.search = search.value.trim(); renderTable(); });
    $$('[data-cost-filter]').forEach((button) => button.addEventListener("click", () => {
      $$('[data-cost-filter]').forEach((item) => item.classList.remove("is-active"));
      button.classList.add("is-active");
      state.filter = button.dataset.costFilter;
      renderTable();
    }));
  }

  function initActions() {
    $("[data-cost-table]")?.addEventListener("click", (event) => {
      const edit = event.target.closest("[data-edit-cost]");
      const remove = event.target.closest("[data-delete-cost]");
      if (edit) {
        const entry = state.entries.find((item) => item.id === Number(edit.dataset.editCost));
        if (entry) openCostModal(entry);
      }
      if (remove) {
        const index = state.entries.findIndex((item) => item.id === Number(remove.dataset.deleteCost));
        if (index < 0) return;
        const [entry] = state.entries.splice(index, 1);
        toast("info", "Lançamento excluído", `${entry.description} foi removido nesta sessão.`);
        updateMetrics();
        renderChart();
        renderTable();
      }
    });
    $("[data-export-costs]")?.addEventListener("click", () => {
      const rows = [["Descrição", "Categoria", "Talhão", "Data", "Valor"], ...visibleEntries().map((entry) => [entry.description, entry.category, entry.plot, entry.date, entry.value.toFixed(2)])];
      const csv = rows.map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(";")).join("\n");
      const link = document.createElement("a");
      link.href = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" }));
      link.download = "agrogestao-custos.csv";
      link.click();
      URL.revokeObjectURL(link.href);
      toast("success", "Exportação concluída", "O CSV dos lançamentos filtrados foi baixado.");
    });
  }

  function initModalDismissals() {
    ["budget-form", "period-form"].forEach((name) => {
      const modal = $(`[data-modal="${name}"]`);
      if (!modal) return;
      $$('[data-close-modal]', modal).forEach((button) => button.addEventListener("click", () => setModalState(name, false)));
      modal.addEventListener("click", (event) => {
        if (event.target === modal) setModalState(name, false);
      });
    });
  }

  function init() {
    initDatePicker();
    initForm();
    initBudgetModal();
    initPeriodModal();
    initModalDismissals();
    initFilters();
    initActions();
    updateMetrics();
    renderChart();
    renderTable();
  }

  document.addEventListener("DOMContentLoaded", init);
})();
