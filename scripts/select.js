/**
 * Agro — Select / Dropdown do design system (Forest Sage)
 * --------------------------------------------------------------------------
 * Substitui o <select> nativo (destaque azul do SO) pelo padrão do pen.dev:
 * Closed · Open (borda $primary) · Selected ($primary-subtle) · Hover.
 *
 * Uso: envolva um <select data-select-native> ou marque [data-select].
 * enhanceSelects() transforma automaticamente selects com data-enhance-select.
 */
(() => {
  "use strict";

  const CHEVRON =
    '<svg class="select-chevron" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>';
  const CHECK =
    '<svg class="select-option-check" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>';

  function closeSelect(root) {
    root.classList.remove("is-open");
    const trigger = root.querySelector(".select-trigger");
    if (trigger) trigger.setAttribute("aria-expanded", "false");
  }

  function closeAll(except) {
    document.querySelectorAll(".select.is-open").forEach((el) => {
      if (except && el === except) return;
      closeSelect(el);
    });
  }

  /**
   * Constrói o dropdown custom a partir de um <select> nativo.
   * @param {HTMLSelectElement} native
   */
  function enhance(native) {
    if (!native || native.dataset.selectEnhanced === "true") return;
    native.dataset.selectEnhanced = "true";

    const placeholder =
      native.dataset.placeholder ||
      native.options[0]?.textContent?.trim() ||
      "Selecione…";

    const root = document.createElement("div");
    root.className = "select";
    root.dataset.select = "";

    const trigger = document.createElement("button");
    trigger.type = "button";
    trigger.className = "select-trigger";
    trigger.setAttribute("aria-haspopup", "listbox");
    trigger.setAttribute("aria-expanded", "false");
    if (native.id) {
      trigger.id = `${native.id}-trigger`;
      // Reassocia labels que ainda apontam para o select nativo
      document.querySelectorAll(`label[for="${native.id}"]`).forEach((labelEl) => {
        labelEl.htmlFor = trigger.id;
      });
    }

    const valueSpan = document.createElement("span");
    valueSpan.className = "select-value is-placeholder";
    valueSpan.textContent = placeholder;

    trigger.appendChild(valueSpan);
    trigger.insertAdjacentHTML("beforeend", CHEVRON);

    const menu = document.createElement("ul");
    menu.className = "select-menu";
    menu.setAttribute("role", "listbox");
    if (native.id) menu.id = `${native.id}-listbox`;
    trigger.setAttribute("aria-controls", menu.id);

    [...native.options].forEach((opt, index) => {
      // Pula o option vazio do placeholder na lista visual? Mantém como no nativo.
      const li = document.createElement("li");
      li.setAttribute("role", "none");

      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "select-option";
      btn.setAttribute("role", "option");
      btn.dataset.value = opt.value;
      btn.setAttribute("aria-selected", "false");
      if (index === 0 && !opt.value) btn.dataset.placeholder = "true";

      const label = document.createElement("span");
      label.textContent = opt.textContent;
      btn.appendChild(label);
      btn.insertAdjacentHTML("beforeend", CHECK);

      btn.addEventListener("click", () => {
        native.value = opt.value;
        native.dispatchEvent(new Event("change", { bubbles: true }));
        syncFromNative();
        closeSelect(root);
        trigger.focus();
      });

      li.appendChild(btn);
      menu.appendChild(li);
    });

    function syncFromNative() {
      const selected = native.options[native.selectedIndex];
      const empty = !selected || !selected.value;
      valueSpan.textContent = empty ? placeholder : selected.textContent;
      valueSpan.classList.toggle("is-placeholder", empty);

      menu.querySelectorAll(".select-option").forEach((btn) => {
        const isSelected = btn.dataset.value === native.value;
        btn.classList.toggle("is-selected", isSelected);
        btn.setAttribute("aria-selected", String(isSelected));
      });

      root.classList.toggle("is-invalid", native.getAttribute("aria-invalid") === "true");
    }

    trigger.addEventListener("click", (event) => {
      event.preventDefault();
      const willOpen = !root.classList.contains("is-open");
      closeAll(willOpen ? root : null);
      root.classList.toggle("is-open", willOpen);
      trigger.setAttribute("aria-expanded", String(willOpen));
    });

    trigger.addEventListener("keydown", (event) => {
      if (event.key === "ArrowDown" || event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        if (!root.classList.contains("is-open")) {
          root.classList.add("is-open");
          trigger.setAttribute("aria-expanded", "true");
        }
        menu.querySelector(".select-option")?.focus();
      }
    });

    menu.addEventListener("keydown", (event) => {
      const options = [...menu.querySelectorAll(".select-option")];
      const index = options.indexOf(document.activeElement);
      if (event.key === "ArrowDown") {
        event.preventDefault();
        options[Math.min(index + 1, options.length - 1)]?.focus();
      } else if (event.key === "ArrowUp") {
        event.preventDefault();
        options[Math.max(index - 1, 0)]?.focus();
      } else if (event.key === "Escape") {
        closeSelect(root);
        trigger.focus();
      } else if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        document.activeElement?.click();
      }
    });

    native.classList.add("select-native");
    native.setAttribute("tabindex", "-1");
    native.setAttribute("aria-hidden", "true");

    native.parentNode.insertBefore(root, native);
    root.appendChild(trigger);
    root.appendChild(menu);
    root.appendChild(native);

    native.addEventListener("change", syncFromNative);
    syncFromNative();
  }

  function init() {
    document.querySelectorAll("select[data-enhance-select]").forEach(enhance);

    document.addEventListener("click", (event) => {
      if (!event.target.closest(".select")) closeAll();
    });

    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") closeAll();
    });
  }

  window.AgroSelect = { enhance, init };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
