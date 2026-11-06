/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */
/**
 * Calendário em modal no lugar do input nativo type="date".
 * O lançamento de custo mantém o id date-form, usado pelos testes de interface.
 */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  let sequence = 0;

  function dateToISO(date) {
    return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, "0"), String(date.getDate()).padStart(2, "0")].join("-");
  }

  function displayDate(value) {
    if (!value) return "Selecione a data";
    return new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric" }).format(new Date(`${value}T12:00:00`));
  }

  function enhance(input) {
    if (!input || input.dataset.dateEnhanced === "true" || input.dataset.keepNative === "true") return;
    input.dataset.dateEnhanced = "true";
    if (!input.id) input.id = `agro-date-${++sequence}`;
    const modalId = input.id === "cost-date" ? "date-form" : `date-picker-${input.id}`;
    input.type = "hidden";

    const trigger = document.createElement("button");
    trigger.type = "button";
    trigger.id = `${input.id}-trigger`;
    document.querySelector(`label[for="${input.id}"]`)?.setAttribute("for", trigger.id);
    trigger.className = "date-picker-trigger";
    trigger.dataset.openCalendar = "";
    if (input.id === "cost-date") trigger.dataset.openDateModal = "";
    trigger.setAttribute("aria-haspopup", "dialog");
    trigger.setAttribute("aria-controls", modalId);
    trigger.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><rect width="18" height="18" x="3" y="4" rx="2"/><path d="M16 2v4"/><path d="M8 2v4"/><path d="M3 10h18"/></svg><span data-date-label>Selecione a data</span>';
    input.parentNode.insertBefore(trigger, input);

    const modal = document.createElement("div");
    modal.className = "modal-backdrop";
    modal.id = modalId;
    modal.dataset.modal = modalId;
    modal.dataset.datePicker = "";
    modal.setAttribute("aria-hidden", "true");
    modal.setAttribute("role", "presentation");
    const hint = input.id === "cost-date" ? "Escolha uma data para o lançamento." : "Escolha uma data.";
    const clear = input.required ? "" : '<button class="btn btn-ghost" type="button" data-calendar-clear>Limpar</button>';
    modal.innerHTML = `<div class="modal calendar-modal" role="dialog" aria-modal="true" aria-labelledby="${modalId}-title"><div class="calendar-modal-header"><div><h2 id="${modalId}-title">Selecionar data</h2><p data-calendar-selected>${hint}</p></div><button class="icon-btn icon-btn--sm" type="button" data-calendar-close aria-label="Fechar calendário"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"/></svg></button></div><div class="calendar-toolbar"><button class="icon-btn icon-btn--sm" type="button" data-calendar-prev aria-label="Mês anterior"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 18-6-6 6-6"/></svg></button><strong data-calendar-month></strong><button class="icon-btn icon-btn--sm" type="button" data-calendar-next aria-label="Próximo mês"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg></button></div><div class="calendar-weekdays" aria-hidden="true"><span>Dom</span><span>Seg</span><span>Ter</span><span>Qua</span><span>Qui</span><span>Sex</span><span>Sáb</span></div><div class="calendar-grid" data-calendar-grid role="grid" aria-label="Calendário"></div><div class="calendar-footer">${clear}<button class="btn btn-outline" type="button" data-calendar-today>Hoje</button></div></div>`;
    document.body.appendChild(modal);
    let viewDate = input.value ? new Date(`${input.value}T12:00:00`) : new Date();

    function syncLabel() {
      $("[data-date-label]", trigger).textContent = displayDate(input.value);
      $("[data-calendar-selected]", modal).textContent = input.value ? `Data selecionada: ${displayDate(input.value)}` : hint;
    }

    function renderCalendar() {
      const monthLabel = new Intl.DateTimeFormat("pt-BR", { month: "long", year: "numeric" }).format(viewDate);
      $("[data-calendar-month]", modal).textContent = monthLabel.charAt(0).toUpperCase() + monthLabel.slice(1);
      const grid = $("[data-calendar-grid]", modal);
      const first = new Date(viewDate.getFullYear(), viewDate.getMonth(), 1);
      grid.replaceChildren();
      for (let index = 0; index < 42; index += 1) {
        const date = new Date(viewDate.getFullYear(), viewDate.getMonth(), index - first.getDay() + 1);
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
    $("[data-calendar-clear]", modal)?.addEventListener("click", () => {
      input.value = "";
      input.dispatchEvent(new Event("change", { bubbles: true }));
      syncLabel();
      closeCalendar();
    });
    $("[data-calendar-today]", modal).addEventListener("click", () => {
      input.value = dateToISO(new Date());
      input.dispatchEvent(new Event("change", { bubbles: true }));
      syncLabel();
      closeCalendar();
    });
    $("[data-calendar-close]", modal).addEventListener("click", closeCalendar);
    modal.addEventListener("click", (event) => { if (event.target === modal) closeCalendar(); });
    input.addEventListener("change", syncLabel);
    syncLabel();
  }

  function enhanceMonth(input) {
    if (!input || input.dataset.dateEnhanced === "true") return;
    input.dataset.dateEnhanced = "true";
    if (!input.id) input.id = `agro-month-${++sequence}`;
    const modalId = `month-picker-${input.id}`;
    input.type = "hidden";
    const trigger = document.createElement("button");
    trigger.type = "button";
    trigger.id = `${input.id}-trigger`;
    trigger.className = "date-picker-trigger";
    trigger.dataset.openMonthModal = "";
    trigger.setAttribute("aria-haspopup", "dialog");
    trigger.setAttribute("aria-controls", modalId);
    trigger.setAttribute("aria-label", input.getAttribute("aria-label") || "Selecionar mês");
    trigger.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><rect width="18" height="18" x="3" y="4" rx="2"/><path d="M16 2v4"/><path d="M8 2v4"/><path d="M3 10h18"/></svg><span data-month-label>Selecione o mês</span>';
    input.parentNode.insertBefore(trigger, input);
    const names = ["Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"];
    const modal = document.createElement("div");
    modal.className = "modal-backdrop";
    modal.id = modalId;
    modal.dataset.modal = modalId;
    modal.setAttribute("aria-hidden", "true");
    modal.setAttribute("role", "presentation");
    modal.innerHTML = `<div class="modal calendar-modal" role="dialog" aria-modal="true" aria-labelledby="${modalId}-title"><div class="calendar-modal-header"><div><h2 id="${modalId}-title">Selecionar mês</h2><p data-month-selected>Escolha o mês.</p></div><button class="icon-btn icon-btn--sm" type="button" data-month-close aria-label="Fechar"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"/></svg></button></div><div class="calendar-toolbar"><button class="icon-btn icon-btn--sm" type="button" data-month-prev aria-label="Ano anterior"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 18-6-6 6-6"/></svg></button><strong data-month-year></strong><button class="icon-btn icon-btn--sm" type="button" data-month-next aria-label="Próximo ano"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg></button></div><div class="month-grid" data-month-grid></div><div class="calendar-footer"><button class="btn btn-ghost" type="button" data-month-clear>Limpar</button></div></div>`;
    document.body.appendChild(modal);
    let year = Number((input.value || "").slice(0, 4)) || new Date().getFullYear();

    function label(value) {
      if (!/^\d{4}-\d{2}$/.test(value || "")) return "Selecione o mês";
      return `${names[Number(value.slice(5, 7)) - 1]} ${value.slice(0, 4)}`;
    }

    function render() {
      $("[data-month-year]", modal).textContent = String(year);
      const grid = $("[data-month-grid]", modal);
      grid.replaceChildren();
      names.forEach((name, index) => {
        const value = `${year}-${String(index + 1).padStart(2, "0")}`;
        const button = document.createElement("button");
        button.type = "button";
        button.className = "time-option";
        button.textContent = name;
        if (input.value === value) button.classList.add("is-selected");
        button.addEventListener("click", () => {
          input.value = value;
          input.dispatchEvent(new Event("change", { bubbles: true }));
          sync();
          close();
        });
        grid.appendChild(button);
      });
    }

    function sync() {
      $("[data-month-label]", trigger).textContent = label(input.value);
      $("[data-month-selected]", modal).textContent = input.value ? `Mês selecionado: ${label(input.value)}` : "Escolha o mês.";
    }

    function close() {
      modal.classList.remove("is-open");
      modal.setAttribute("aria-hidden", "true");
      trigger.focus();
    }

    trigger.addEventListener("click", () => {
      year = Number((input.value || "").slice(0, 4)) || new Date().getFullYear();
      render();
      sync();
      modal.classList.add("is-open");
      modal.setAttribute("aria-hidden", "false");
    });
    $("[data-month-prev]", modal).addEventListener("click", () => { year -= 1; render(); });
    $("[data-month-next]", modal).addEventListener("click", () => { year += 1; render(); });
    $("[data-month-clear]", modal).addEventListener("click", () => {
      input.value = "";
      input.dispatchEvent(new Event("change", { bubbles: true }));
      sync();
      close();
    });
    $("[data-month-close]", modal).addEventListener("click", close);
    modal.addEventListener("click", (event) => { if (event.target === modal) close(); });
    input.addEventListener("change", sync);
    sync();
  }

  function init() {
    document.querySelectorAll('input[type="date"]').forEach(enhance);
    document.querySelectorAll('input[type="month"]').forEach(enhanceMonth);
  }

  window.AgroDatePicker = { enhance, init };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
