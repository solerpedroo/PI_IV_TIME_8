/**
 * Agro — API de toasts (Flow E do design system)
 * --------------------------------------------------------------------------
 * Uso: window.AgroToast.show({ type, title, description, duration })
 * Tipos: success | error | warning | info | inverse
 */
(() => {
  "use strict";

  const ICONS = {
    success:
      '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/></svg>',
    error:
      '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="m15 9-6 6"/><path d="m9 9 6 6"/></svg>',
    warning:
      '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg>',
    info:
      '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>',
    inverse:
      '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>',
  };

  const CLOSE_ICON =
    '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>';

  let viewport = null;

  function ensureViewport() {
    if (viewport && document.body.contains(viewport)) return viewport;
    viewport = document.querySelector("[data-toast-viewport]");
    if (!viewport) {
      viewport = document.createElement("div");
      viewport.className = "toast-viewport";
      viewport.setAttribute("data-toast-viewport", "");
      viewport.setAttribute("aria-live", "polite");
      viewport.setAttribute("aria-relevant", "additions");
      document.body.appendChild(viewport);
    }
    return viewport;
  }

  /**
   * Exibe um toast alinhado ao catálogo do pen.dev.
   * @param {{ type?: string, title: string, description?: string, duration?: number }} options
   */
  function show(options = {}) {
    const type = options.type || "info";
    const title = options.title || "";
    const description = options.description || "";
    const duration = options.duration ?? 4200;
    const host = ensureViewport();

    const toast = document.createElement("div");
    toast.className = `toast toast-${type}`;
    toast.setAttribute("role", type === "error" || type === "warning" ? "alert" : "status");

    toast.innerHTML = `
      <span class="toast-icon">${ICONS[type] || ICONS.info}</span>
      <div class="toast-body">
        <p class="toast-title"></p>
        ${description ? '<p class="toast-desc"></p>' : ""}
      </div>
      <button type="button" class="toast-close" aria-label="Fechar notificação">${CLOSE_ICON}</button>
    `;

    toast.querySelector(".toast-title").textContent = title;
    const descEl = toast.querySelector(".toast-desc");
    if (descEl) descEl.textContent = description;

    const dismiss = () => {
      if (toast.classList.contains("is-leaving")) return;
      toast.classList.add("is-leaving");
      window.setTimeout(() => toast.remove(), 240);
    };

    toast.querySelector(".toast-close").addEventListener("click", dismiss);
    host.appendChild(toast);

    if (duration > 0) {
      window.setTimeout(dismiss, duration);
    }

    return { dismiss, el: toast };
  }

  window.AgroToast = { show };
})();
