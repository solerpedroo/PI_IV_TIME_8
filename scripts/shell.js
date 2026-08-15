/**
 * Agro — chrome do app shell
 * --------------------------------------------------------------------------
 * Responsabilidades:
 * - Abrir/fechar painel de notificações
 * - Dropdown de perfil (sidebar)
 * - Modal de logout → login.html
 * - Fechar popovers ao clicar fora / Escape
 */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

  const STORAGE = {
    flashSuccess: "agro.flashSuccess",
  };

  /** Itens navegáveis de um popover com role="menu". */
  const menuItems = (popover) => $$('[role="menuitem"]', popover).filter((item) => !item.hidden);

  function triggerOf(popover) {
    return document.querySelector(`[aria-controls="${popover.id}"]`);
  }

  function closeAllPopovers(except) {
    $$("[data-popover]").forEach((popover) => {
      if (except && popover === except) return;
      popover.classList.remove("is-open");
      const trigger = triggerOf(popover);
      if (trigger) trigger.setAttribute("aria-expanded", "false");
    });
  }

  function togglePopover(trigger, { focusFirstItem = false } = {}) {
    const id = trigger.getAttribute("aria-controls");
    const popover = id ? document.getElementById(id) : null;
    if (!popover) return;
    const willOpen = !popover.classList.contains("is-open");
    closeAllPopovers(willOpen ? popover : null);
    popover.classList.toggle("is-open", willOpen);
    trigger.setAttribute("aria-expanded", String(willOpen));
    if (willOpen && focusFirstItem) menuItems(popover)[0]?.focus();
  }

  function initPopoverTriggers() {
    $$("[data-popover-trigger]").forEach((trigger) => {
      trigger.addEventListener("click", (event) => {
        event.stopPropagation();
        togglePopover(trigger);
      });

      // Abertura por teclado já foca o primeiro item do menu (padrão de menu button).
      trigger.addEventListener("keydown", (event) => {
        if (event.key !== "ArrowDown" && event.key !== "ArrowUp") return;
        event.preventDefault();
        const id = trigger.getAttribute("aria-controls");
        const popover = id ? document.getElementById(id) : null;
        if (!popover) return;
        if (!popover.classList.contains("is-open")) {
          togglePopover(trigger, { focusFirstItem: true });
          return;
        }
        const items = menuItems(popover);
        (event.key === "ArrowDown" ? items[0] : items[items.length - 1])?.focus();
      });
    });

    // Setas / Home / End navegam entre os itens do menu aberto.
    $$('[data-popover][role="menu"]').forEach((popover) => {
      popover.addEventListener("keydown", (event) => {
        const items = menuItems(popover);
        const index = items.indexOf(document.activeElement);
        if (index === -1) return;

        const moves = {
          ArrowDown: (index + 1) % items.length,
          ArrowUp: (index - 1 + items.length) % items.length,
          Home: 0,
          End: items.length - 1,
        };

        if (event.key in moves) {
          event.preventDefault();
          items[moves[event.key]].focus();
        }
      });
    });

    document.addEventListener("click", (event) => {
      const inside = event.target.closest("[data-popover], [data-popover-trigger]");
      if (!inside) closeAllPopovers();
    });

    // Tab para fora do popover encerra o overlay (não prende o foco).
    document.addEventListener("focusin", (event) => {
      $$("[data-popover].is-open").forEach((popover) => {
        if (popover.contains(event.target) || triggerOf(popover) === event.target) return;
        closeAllPopovers();
      });
    });

    document.addEventListener("keydown", (event) => {
      if (event.key !== "Escape") return;
      const open = $("[data-popover].is-open");
      const trigger = open ? triggerOf(open) : null;
      closeAllPopovers();
      closeModal($("[data-modal].is-open"));
      trigger?.focus();
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Notificações                                                           */
  /* ---------------------------------------------------------------------- */
  function initNotifications() {
    const list = $("[data-notif-list]");
    const empty = $("[data-notif-empty]");
    const markAll = $("[data-notif-mark-all]");
    const dot = $("[data-notif-dot]");
    if (!list) return;

    function refreshUnread() {
      const unread = $$(".notif-item.is-unread", list);
      if (dot) dot.classList.toggle("is-hidden", unread.length === 0);
      if (empty) empty.hidden = list.children.length > 0;
      list.hidden = list.children.length === 0;
    }

    list.addEventListener("click", (event) => {
      const item = event.target.closest(".notif-item");
      if (!item) return;
      item.classList.remove("is-unread");
      refreshUnread();
      if (window.AgroToast) {
        window.AgroToast.show({
          type: "info",
          title: "Notificação aberta",
          description: item.dataset.toast || "Destino do módulo ainda em preparação.",
        });
      }
    });

    if (markAll) {
      markAll.addEventListener("click", () => {
        $$(".notif-item.is-unread", list).forEach((item) => item.classList.remove("is-unread"));
        refreshUnread();
        if (window.AgroToast) {
          window.AgroToast.show({
            type: "success",
            title: "Tudo lido",
            description: "Todas as notificações foram marcadas como lidas.",
          });
        }
      });
    }

    refreshUnread();
  }

  /* ---------------------------------------------------------------------- */
  /* Modal logout                                                           */
  /* ---------------------------------------------------------------------- */
  function openModal(modal) {
    if (!modal) return;
    modal.classList.add("is-open");
    modal.setAttribute("aria-hidden", "false");
    const focusable = modal.querySelector("button, [href], input, select, textarea");
    focusable?.focus();
  }

  function closeModal(modal) {
    if (!modal) return;
    modal.classList.remove("is-open");
    modal.setAttribute("aria-hidden", "true");
  }

  function initLogout() {
    const modal = $("[data-modal='logout']");
    if (!modal) return;

    $$("[data-open-logout]").forEach((btn) => {
      btn.addEventListener("click", () => {
        closeAllPopovers();
        openModal(modal);
      });
    });

    $$("[data-close-modal]", modal).forEach((btn) => {
      btn.addEventListener("click", () => closeModal(modal));
    });

    modal.addEventListener("click", (event) => {
      if (event.target === modal) closeModal(modal);
    });

    const confirm = $("[data-confirm-logout]", modal);
    if (confirm) {
      confirm.addEventListener("click", () => {
        sessionStorage.setItem(
          STORAGE.flashSuccess,
          "Você saiu com segurança."
        );
        window.location.href = "login.html";
      });
    }
  }

  /* ---------------------------------------------------------------------- */
  /* Links preparatórios dos módulos                                        */
  /* ---------------------------------------------------------------------- */
  function initPrepLinks() {
    $$("[data-prep-link]").forEach((link) => {
      link.addEventListener("click", (event) => {
        const href = link.getAttribute("href");
        if (href && href !== "#" && !href.startsWith("#")) return;
        event.preventDefault();
        closeAllPopovers();
        if (window.AgroToast) {
          window.AgroToast.show({
            type: "info",
            title: "Módulo em preparação",
            description: link.dataset.prepLink || "Esta tela será implementada na próxima onda.",
          });
        }
      });
    });
  }

  function init() {
    initPopoverTriggers();
    initNotifications();
    initLogout();
    initPrepLinks();
    consumeWelcomeFlash();
  }

  /** Toast de boas-vindas após login (sessionStorage). */
  function consumeWelcomeFlash() {
    const flash = sessionStorage.getItem(STORAGE.flashSuccess);
    if (!flash || !window.AgroToast) return;
    sessionStorage.removeItem(STORAGE.flashSuccess);
    window.AgroToast.show({
      type: "success",
      title: "Bem-vindo",
      description: flash,
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
