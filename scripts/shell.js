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

  /**
   * Menus de ação "⋮" (Áreas de Cultivo, Atividades, Insumos, Ocorrências)
   * abrem dentro de tabelas/colunas com `overflow: auto` (rolagem própria).
   * Um popover `position: absolute` nessas condições é cortado pelo
   * clipping do ancestral com scroll assim que a linha/card fica perto do
   * fim da área visível — o menu "existe" no DOM mas não aparece.
   *
   * Para resolver sem duplicar lógica de posicionamento em cada tela,
   * detectamos aqui (no popover genérico compartilhado por todo o shell)
   * se o trigger está dentro de um ancestral que corta overflow e, se
   * estiver, promovemos o popover para `position: fixed` com coordenadas
   * calculadas a partir do trigger — escapando do clipping. Popovers que
   * já vivem fora de containers com scroll (notificações, perfil) não são
   * afetados: a checagem só age quando encontra um ancestral com overflow.
   */
  function findScrollClipAncestor(el) {
    let node = el.parentElement;
    while (node && node !== document.body) {
      const style = window.getComputedStyle(node);
      if (/(auto|scroll|hidden)/.test(style.overflowY) || /(auto|scroll|hidden)/.test(style.overflowX)) {
        return node;
      }
      node = node.parentElement;
    }
    return null;
  }

  function positionPopoverFixed(popover, trigger) {
    const rect = trigger.getBoundingClientRect();
    const menuWidth = popover.offsetWidth || rect.width;
    const menuHeight = popover.offsetHeight || 0;

    let left = rect.right - menuWidth;
    left = Math.max(8, Math.min(left, window.innerWidth - menuWidth - 8));

    let top = rect.bottom + 6;
    if (menuHeight && top + menuHeight > window.innerHeight - 8) {
      // Sem espaço abaixo: abre para cima do trigger (mesma ideia do
      // .sidebar-popover, mas calculada em runtime para qualquer contexto).
      top = rect.top - menuHeight - 6;
    }

    popover.style.position = "fixed";
    popover.style.top = `${top}px`;
    popover.style.left = `${left}px`;
    popover.style.right = "auto";
    popover.style.bottom = "auto";
  }

  function resetPopoverPosition(popover) {
    popover.style.position = "";
    popover.style.top = "";
    popover.style.left = "";
    popover.style.right = "";
    popover.style.bottom = "";
  }

  function closeAllPopovers(except) {
    $$("[data-popover]").forEach((popover) => {
      if (except && popover === except) return;
      popover.classList.remove("is-open");
      resetPopoverPosition(popover);
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
    if (willOpen) {
      const clipAncestor = findScrollClipAncestor(trigger);
      if (clipAncestor) positionPopoverFixed(popover, trigger);
      else resetPopoverPosition(popover);
      if (focusFirstItem) menuItems(popover)[0]?.focus();
    }
  }

  /**
   * Delegação no `document` (em vez de `addEventListener` por elemento):
   * Áreas de Cultivo, Atividades e Ocorrências criam linhas/cards novos em
   * runtime (novo talhão, nova atividade, nova ocorrência) — cada um com
   * seu próprio botão "⋮". Ligar o listener direto no elemento no boot da
   * página deixaria esses triggers futuros sem clique funcional; delegando
   * no `document`, qualquer trigger — presente no load ou criado depois —
   * funciona sem precisar chamar uma função de "re-inicialização".
   */
  function initPopoverTriggers() {
    document.addEventListener("click", (event) => {
      const trigger = event.target.closest("[data-popover-trigger]");
      if (!trigger) return;
      event.stopPropagation();
      togglePopover(trigger);
    });

    document.addEventListener("keydown", (event) => {
      // Abertura por teclado já foca o primeiro item do menu (padrão de menu button).
      const trigger = event.target.closest("[data-popover-trigger]");
      if (trigger && (event.key === "ArrowDown" || event.key === "ArrowUp")) {
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
        return;
      }

      // Setas / Home / End navegam entre os itens do menu aberto.
      const popover = event.target.closest('[data-popover][role="menu"]');
      if (popover) {
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
      }
    });

    document.addEventListener("click", (event) => {
      const inside = event.target.closest("[data-popover], [data-popover-trigger]");
      if (!inside) closeAllPopovers();
    });

    /**
     * Clicar em um item de menu (Ver detalhes, Concluir, Excluir…) sempre
     * fecha o popover — mesmo quando a ação resulta só em um toast (ex.:
     * exclusão bloqueada por regra em Insumos) em vez de abrir um modal.
     * Sem isso, o dropdown continuaria "aberto" e, sendo reposicionado em
     * `position: fixed` quando dentro de uma área com scroll (ver
     * `positionPopoverFixed`), ficaria flutuando sobre a tela interceptando
     * cliques nas linhas seguintes. Roda depois do handler específico de
     * cada tela (registrado só na inicialização do respectivo script), o
     * que não é um problema: fechar o popover só limpa classe/estilo — a
     * linha/card que o handler já leu via `closest()` continua no DOM.
     */
    document.addEventListener("click", (event) => {
      const item = event.target.closest('[role="menuitem"]');
      if (item && !item.hasAttribute("data-popover-trigger")) closeAllPopovers();
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

  /**
   * Exposto para os módulos (áreas de cultivo, atividades, insumos,
   * ocorrências): um item de menu "⋮" pode levar a um toast (ex.: "Excluir"
   * bloqueado por regra) em vez de abrir um modal — nesse caso nada mais
   * fecharia o popover, e o dropdown continuaria flutuando (fixed) sobre a
   * tela, interceptando cliques nas linhas abaixo dele. Cada handler de
   * ação deve chamar `AgroPopover.closeAll()` antes de decidir o que fazer.
   */
  window.AgroPopover = { closeAll: closeAllPopovers };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
