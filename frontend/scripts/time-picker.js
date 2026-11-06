/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */
/**
 * Modal de horário no lugar do input nativo type="time".
 * Horas de 00 a 23 e minutos em intervalos de 15.
 */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  let sequence = 0;

  function publish(input) {
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.dispatchEvent(new Event("change", { bubbles: true }));
  }

  function mirrorValue(input, sync) {
    const descriptor = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value");
    Object.defineProperty(input, "value", {
      configurable: true,
      enumerable: true,
      get() { return descriptor.get.call(this); },
      set(next) {
        descriptor.set.call(this, next);
        sync();
      },
    });
  }

  function pad(value) {
    return String(value).padStart(2, "0");
  }

  function enhance(input) {
    if (!input || input.dataset.timeEnhanced === "true") return;
    input.dataset.timeEnhanced = "true";
    if (!input.id) input.id = `agro-time-${++sequence}`;
    const modalId = `time-picker-${input.id}`;
    const initial = input.value;
    input.type = "hidden";

    const trigger = document.createElement("button");
    trigger.type = "button";
    trigger.id = `${input.id}-trigger`;
    document.querySelector(`label[for="${input.id}"]`)?.setAttribute("for", trigger.id);
    trigger.className = "date-picker-trigger";
    trigger.dataset.openTimeModal = "";
    trigger.setAttribute("aria-haspopup", "dialog");
    trigger.setAttribute("aria-controls", modalId);
    trigger.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v6l4 2"/></svg><span data-time-label>Selecione o horário</span>';
    input.parentNode.insertBefore(trigger, input);
    input.focus = () => trigger.focus();

    const hours = Array.from({ length: 24 }, (_, hour) => `<button type="button" class="time-option" data-time-hour="${pad(hour)}">${pad(hour)}</button>`).join("");
    const minutes = ["00", "15", "30", "45"].map((minute) => `<button type="button" class="time-option" data-time-minute="${minute}">${minute}</button>`).join("");
    const modal = document.createElement("div");
    modal.className = "modal-backdrop";
    modal.id = modalId;
    modal.dataset.modal = modalId;
    modal.dataset.pickerLayer = "";
    modal.setAttribute("aria-hidden", "true");
    modal.setAttribute("role", "presentation");
    modal.innerHTML = `<div class="modal time-modal" role="dialog" aria-modal="true" aria-labelledby="${modalId}-title"><div class="calendar-modal-header"><div><h2 id="${modalId}-title">Selecionar horário</h2><p data-time-selected>Escolha hora e minuto.</p></div><button class="icon-btn icon-btn--sm" type="button" data-time-close aria-label="Fechar seletor"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"/></svg></button></div><div class="time-columns"><div><span>Hora</span><div class="time-options" data-time-hours role="listbox" aria-label="Hora">${hours}</div></div><div><span>Minuto</span><div class="time-options" data-time-minutes role="listbox" aria-label="Minuto">${minutes}</div></div></div><div class="modal-actions"><button class="btn btn-outline" type="button" data-time-clear>Limpar</button><button class="btn btn-primary" type="button" data-time-apply>Aplicar</button></div></div>`;
    document.body.appendChild(modal);
    let pendingHour = initial.slice(0, 2) || "08";
    let pendingMinute = ["00", "15", "30", "45"].includes(initial.slice(3, 5)) ? initial.slice(3, 5) : "00";

    function paint() {
      modal.querySelectorAll("[data-time-hour]").forEach((button) => button.classList.toggle("is-selected", button.dataset.timeHour === pendingHour));
      modal.querySelectorAll("[data-time-minute]").forEach((button) => button.classList.toggle("is-selected", button.dataset.timeMinute === pendingMinute));
      $("[data-time-selected]", modal).textContent = `Horário: ${pendingHour}:${pendingMinute}`;
      $("[data-time-label]", trigger).textContent = input.value ? input.value.slice(0, 5) : "Selecione o horário";
    }

    function close() {
      modal.classList.remove("is-open");
      modal.setAttribute("aria-hidden", "true");
      trigger.focus();
    }

    trigger.addEventListener("click", () => {
      if (input.value) {
        pendingHour = input.value.slice(0, 2);
        pendingMinute = input.value.slice(3, 5);
      }
      paint();
      modal.classList.add("is-open");
      modal.setAttribute("aria-hidden", "false");
    });
    modal.addEventListener("click", (event) => {
      const hour = event.target.closest("[data-time-hour]");
      const minute = event.target.closest("[data-time-minute]");
      if (hour) pendingHour = hour.dataset.timeHour;
      if (minute) pendingMinute = minute.dataset.timeMinute;
      if (hour || minute) paint();
      if (event.target === modal) close();
    });
    $("[data-time-close]", modal).addEventListener("click", close);
    $("[data-time-clear]", modal).addEventListener("click", () => {
      input.value = "";
      publish(input);
      close();
    });
    $("[data-time-apply]", modal).addEventListener("click", () => {
      input.value = `${pendingHour}:${pendingMinute}`;
      publish(input);
      close();
    });
    document.addEventListener("keydown", (event) => {
      if (event.key !== "Escape" || !modal.classList.contains("is-open")) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      close();
    });
    input.addEventListener("change", paint);
    mirrorValue(input, paint);
    paint();
  }

  function init() {
    document.querySelectorAll('input[type="time"]').forEach(enhance);
  }

  window.AgroTimePicker = { enhance, init };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
