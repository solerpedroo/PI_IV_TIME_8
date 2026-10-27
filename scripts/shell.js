/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */
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
    sessionToken: "agro.sessionToken",
    sessionUser: "agro.sessionUser",
  };

  // Mantém a mesma origem do servidor Java e permite desenvolvimento local.
  const API_BASE = window.location.port === "8080"
    ? window.location.origin
    : "http://127.0.0.1:8080";

  /** Remove qualquer dado de sessão que não possa mais ser usado. */
  function clearSession() {
    sessionStorage.removeItem(STORAGE.sessionToken);
    sessionStorage.removeItem(STORAGE.sessionUser);
  }

  /**
   * Protege páginas internas também no cliente. A API continua sendo a fonte
   * de verdade: o token é validado em GET /api/me antes de inicializar a tela.
   */
  async function ensureAuthenticated() {
    const token = sessionStorage.getItem(STORAGE.sessionToken);
    if (!token) {
      window.location.replace("login.html");
      return false;
    }

    try {
      const response = await fetch(`${API_BASE}/api/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!response.ok) throw new Error("Sessão inválida");
      const data = await response.json();
      sessionStorage.setItem(STORAGE.sessionUser, JSON.stringify(data.usuario));
      fillProfile(data.usuario, data.propriedade?.farm);
      return true;
    } catch {
      clearSession();
      sessionStorage.setItem(STORAGE.flashSuccess, "Sua sessão expirou. Entre novamente.");
      window.location.replace("login.html");
      return false;
    }
  }

  // Usa sempre a conta autenticada, sem nomes fixos do protótipo na navegação.
  function fillProfile(user, property) {
    $$(".sidebar-user-name").forEach(el => { el.textContent = user.name; });
    if (property) $$(".sidebar-user-property").forEach(el => { el.textContent = property; });
    $$(".sidebar-avatar").forEach(el => { el.textContent = (user.name || "").split(/\s+/).slice(0, 2).map(part => part[0]).join(""); });
  }
  document.addEventListener("agro:profile-updated", e => fillProfile(e.detail));

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
  // O ancestral que corta overflow não muda enquanto o menu está aberto.
  const clipByPopover = new WeakMap();

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
    // Menus altos continuam dentro da janela, mesmo quando falta espaço acima.
    top = Math.max(8, top);

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
      clipByPopover.delete(popover);
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
      clipByPopover.set(popover, clipAncestor);
      if (clipAncestor) positionPopoverFixed(popover, trigger);
      else resetPopoverPosition(popover);
      if (focusFirstItem) menuItems(popover)[0]?.focus();
    } else {
      clipByPopover.delete(popover);
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
    // Acompanha o acionador durante a rolagem e fecha quando ele sai da área útil.
    document.addEventListener("scroll", (event) => {
      const inside = event.target instanceof Element && event.target.closest("[data-popover]");
      if (inside) return;
      $$("[data-popover].is-open").forEach((popover) => {
        const trigger = triggerOf(popover);
        if (!trigger) return closeAllPopovers();
        const rect = trigger.getBoundingClientRect();
        if (!clipByPopover.has(popover)) clipByPopover.set(popover, findScrollClipAncestor(trigger));
        const clip = clipByPopover.get(popover)?.getBoundingClientRect();
        const x = rect.left + rect.width / 2;
        const y = rect.top + rect.height / 2;
        const visible = x >= Math.max(0, clip?.left || 0)
          && x <= Math.min(window.innerWidth, clip?.right ?? window.innerWidth)
          && y >= Math.max(0, clip?.top || 0)
          && y <= Math.min(window.innerHeight, clip?.bottom ?? window.innerHeight);
        if (visible) positionPopoverFixed(popover, trigger);
        else closeAllPopovers();
      });
    }, true);
    window.addEventListener("resize", () => closeAllPopovers());
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
      if ($(".select.is-open")) return;
      const open = $("[data-popover].is-open");
      const trigger = open ? triggerOf(open) : null;
      closeAllPopovers();
      // O calendário fica sobre o formulário de custo; fecha só a última camada.
      const modals = $$("[data-modal].is-open");
      closeModal(modals[modals.length - 1]);
      trigger?.focus();
    });
  }

  /* As telas já controlam a abertura. Este cuidado compartilhado mantém o foco
   * no diálogo ativo e o devolve ao acionador, inclusive em modais empilhados. */
  function initModalFocus() {
    const opened = new Map();
    let source = null;
    const controls = (modal) => $$("button, [href], input, select, textarea, [tabindex]", modal)
      .filter((item) => !item.disabled && item.tabIndex >= 0 && item.getClientRects().length && !item.closest("[inert]"));
    document.addEventListener("click", (event) => {
      source = event.target.closest("button, a, input, select, textarea");
    }, true);

    new MutationObserver((records) => {
      if (!records.some((record) => record.target.matches?.("[data-modal]"))) return;
      const current = $$("[data-modal].is-open");
      for (const [modal, trigger] of opened) {
        if (current.includes(modal)) continue;
        opened.delete(modal);
        if (trigger?.isConnected && (modal.contains(document.activeElement) || document.activeElement === document.body)) trigger.focus();
      }
      for (const modal of current) {
        if (opened.has(modal)) continue;
        opened.set(modal, source);
        if (!modal.contains(document.activeElement)) controls(modal)[0]?.focus();
      }
    }).observe(document.body, { subtree: true, attributes: true, attributeFilter: ["class"] });

    document.addEventListener("keydown", (event) => {
      if (event.key !== "Tab") return;
      const modal = $$("[data-modal].is-open").at(-1);
      if (!modal) return;
      const items = controls(modal);
      const first = items[0];
      const last = items.at(-1);
      if (!items.length) return;
      if (!modal.contains(document.activeElement) || (event.shiftKey && document.activeElement === first) || (!event.shiftKey && document.activeElement === last)) {
        event.preventDefault();
        (event.shiftKey ? last : first).focus();
      }
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

    // A leitura de uma notificação também é uma alteração local. Usamos o
    // texto como chave porque esses itens estáticos ainda não têm IDs da API.
    let readItems = window.AgroLocal.read("notifications", []);
    const notificationKey = (item) => item.textContent.replace(/\s+/g, " ").trim();
    $$(".notif-item", list).forEach((item) => {
      if (readItems.includes(notificationKey(item))) item.classList.remove("is-unread");
    });

    function refreshUnread() {
      const unread = $$(".notif-item.is-unread", list);
      if (dot) dot.classList.toggle("is-hidden", unread.length === 0);
      if (empty) empty.hidden = list.children.length > 0;
      list.hidden = list.children.length === 0;
    }

    list.addEventListener("click", (event) => {
      const item = event.target.closest(".notif-item");
      if (!item) return;
      const next = [...new Set([...readItems, notificationKey(item)])];
      if (!window.AgroLocal.save("notifications", next)) return;
      readItems = next;
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
        const next = [...new Set([...readItems, ...$$(".notif-item", list).map(notificationKey)])];
        if (!window.AgroLocal.save("notifications", next)) return;
        readItems = next;
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
      confirm.addEventListener("click", async () => {
        confirm.disabled = true;
        const token = sessionStorage.getItem(STORAGE.sessionToken);
        let revoked = !token;

        // Mesmo que a rede caia, limpamos o token local para não manter uma
        // sessão aparentemente ativa no navegador.
        if (token) {
          try {
            const response = await fetch(`${API_BASE}/api/auth/logout`, {
              method: "POST",
              headers: { Authorization: `Bearer ${token}` },
              signal: AbortSignal.timeout(10000),
            });
            revoked = response.ok;
          } catch {
            // O redirecionamento abaixo encerra a sessão local com segurança.
          }
        }

        clearSession();
        sessionStorage.setItem(
          STORAGE.flashSuccess,
          revoked ? "Você saiu com segurança." : "Você saiu deste navegador. Não foi possível confirmar o encerramento no servidor."
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
      if (/meu perfil/i.test(link.textContent)) {
        link.href = "configuracoes.html#settings-profile";
        link.removeAttribute("data-prep-link");
        return;
      }
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

  async function init() {
    if (!(await ensureAuthenticated())) return;
    // O texto da navegação fica oculto na sidebar compacta, mas preserva seu nome.
    $$(".sidebar-link").forEach((link) => {
      const label = link.textContent.trim();
      link.setAttribute("aria-label", label);
      link.title = label;
    });
    initPopoverTriggers();
    initModalFocus();
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
