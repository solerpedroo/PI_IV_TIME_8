/* Miguel — RA: 25003110 */

/* Persistência operacional compartilhada. Java continua no servidor; somente
 * JavaScript no navegador consegue acessar o localStorage deste dispositivo.
 * Guardamos dados, nunca HTML, senhas ou tokens. As telas recriam sua interface
 * pelos construtores existentes e calculam novamente os indicadores. */
(() => {
  "use strict";
  const defaults = {
  "plots": [
    {
      "id": "plots-0",
      "plot": "T-01 Norte",
      "crop": "soja",
      "hectares": 124,
      "status": "Ativo"
    },
    {
      "id": "plots-1",
      "plot": "T-02 Sul",
      "crop": "milho",
      "hectares": 98,
      "status": "Irrigação pendente"
    },
    {
      "id": "plots-2",
      "plot": "T-03 Leste",
      "crop": "soja",
      "hectares": 156,
      "status": "Ativo"
    },
    {
      "id": "plots-3",
      "plot": "T-04 Oeste",
      "crop": "trigo",
      "hectares": 72,
      "status": "Em preparo"
    },
    {
      "id": "plots-4",
      "plot": "T-05 Centro",
      "crop": "soja",
      "hectares": 110,
      "status": "Ativo"
    }
  ],
  "activities": [
    {
      "id": "activities-0",
      "activity": "Aplicação de fungicida",
      "tipo": "Aplicação",
      "talhao": "Talhão A3",
      "date": "08 ago",
      "status": "Pendente"
    },
    {
      "id": "activities-1",
      "activity": "Calagem do solo",
      "tipo": "Manutenção",
      "talhao": "Talhão D1",
      "date": "09 ago",
      "status": "Pendente"
    },
    {
      "id": "activities-2",
      "activity": "Irrigação por gotejamento",
      "tipo": "Irrigação",
      "talhao": "Talhão C2",
      "date": "06 ago",
      "status": "Agendada"
    },
    {
      "id": "activities-3",
      "activity": "Análise foliar",
      "tipo": "Aplicação",
      "talhao": "Talhão B4",
      "date": "07 ago",
      "status": "Agendada"
    },
    {
      "id": "activities-4",
      "activity": "Colheita parcial soja",
      "tipo": "Colheita",
      "talhao": "Talhão B1",
      "date": "05 ago",
      "status": "Em andamento"
    },
    {
      "id": "activities-5",
      "activity": "Manutenção trator JD",
      "tipo": "Manutenção",
      "talhao": "Oficina",
      "date": "04 ago",
      "status": "Em andamento"
    },
    {
      "id": "activities-6",
      "activity": "Aplicação herbicida",
      "tipo": "Aplicação",
      "talhao": "Talhão A1",
      "date": "03 ago",
      "status": "Concluída"
    },
    {
      "id": "activities-7",
      "activity": "Irrigação pivô central",
      "tipo": "Irrigação",
      "talhao": "Talhão C1",
      "date": "02 ago",
      "status": "Concluída"
    }
  ],
  "stock": [
    {
      "id": "stock-0",
      "product": "Glifosato 480 SL",
      "category": "herbicida",
      "qty": 85,
      "unit": "L",
      "min": 200,
      "movements": []
    },
    {
      "id": "stock-1",
      "product": "Ureia 45% N",
      "category": "fertilizante",
      "qty": 320,
      "unit": "kg",
      "min": 500,
      "movements": []
    },
    {
      "id": "stock-2",
      "product": "Semente Soja RR",
      "category": "semente",
      "qty": 42,
      "unit": "sc",
      "min": 80,
      "movements": []
    },
    {
      "id": "stock-3",
      "product": "Herbicida 2,4-D",
      "category": "herbicida",
      "qty": 156,
      "unit": "L",
      "min": 100,
      "movements": []
    },
    {
      "id": "stock-4",
      "product": "Fungicida Azoxistrobina",
      "category": "fungicida",
      "qty": 28,
      "unit": "L",
      "min": 50,
      "movements": []
    },
    {
      "id": "stock-5",
      "product": "MAP 11-52-00",
      "category": "fertilizante",
      "qty": 1240,
      "unit": "kg",
      "min": 800,
      "movements": []
    },
    {
      "id": "stock-6",
      "product": "Inseticida Lambda",
      "category": "inseticida",
      "qty": 64,
      "unit": "L",
      "min": 40,
      "movements": []
    }
  ],
  "occurrences": [
    {
      "id": "occurrences-0",
      "occ": "Infestação de lagarta-do-cartucho",
      "tipo": "Praga",
      "talhao": "Talhão B2",
      "date": "05 ago",
      "severity": "Alta",
      "status": "Aberta",
      "timeline": [
        {
          "time": "05 ago · registro",
          "title": "Ocorrência aberta",
          "desc": "Praga identificado(a) em Talhão B2."
        }
      ]
    },
    {
      "id": "occurrences-1",
      "occ": "Chuva excessiva — 85mm em 24h",
      "tipo": "Clima",
      "talhao": "Talhão A1",
      "date": "04 ago",
      "severity": "Média",
      "status": "Em análise",
      "timeline": [
        {
          "time": "04 ago · registro",
          "title": "Ocorrência aberta",
          "desc": "Clima identificado(a) em Talhão A1."
        }
      ]
    },
    {
      "id": "occurrences-2",
      "occ": "Falha no sistema de irrigação",
      "tipo": "Equipamento",
      "talhao": "Talhão C4",
      "date": "03 ago",
      "severity": "Alta",
      "status": "Aberta",
      "timeline": [
        {
          "time": "03 ago · registro",
          "title": "Ocorrência aberta",
          "desc": "Equipamento identificado(a) em Talhão C4."
        }
      ]
    },
    {
      "id": "occurrences-3",
      "occ": "Mancha-alvo na soja",
      "tipo": "Doença",
      "talhao": "Talhão A3",
      "date": "01 ago",
      "severity": "Média",
      "status": "Em análise",
      "timeline": [
        {
          "time": "01 ago · registro",
          "title": "Ocorrência aberta",
          "desc": "Doença identificado(a) em Talhão A3."
        }
      ]
    },
    {
      "id": "occurrences-4",
      "occ": "Geada leve registrada",
      "tipo": "Clima",
      "talhao": "Talhão D1",
      "date": "28 jul",
      "severity": "Baixa",
      "status": "Resolvida",
      "timeline": [
        {
          "time": "28 jul · registro",
          "title": "Ocorrência aberta",
          "desc": "Clima identificado(a) em Talhão D1."
        }
      ]
    },
    {
      "id": "occurrences-5",
      "occ": "Percevejo-marrom detectado",
      "tipo": "Praga",
      "talhao": "Talhão B4",
      "date": "26 jul",
      "severity": "Média",
      "status": "Resolvida",
      "timeline": [
        {
          "time": "26 jul · registro",
          "title": "Ocorrência aberta",
          "desc": "Praga identificado(a) em Talhão B4."
        }
      ]
    },
    {
      "id": "occurrences-6",
      "occ": "Trator MF 4275 — superaquecimento",
      "tipo": "Equipamento",
      "talhao": "Oficina",
      "date": "24 jul",
      "severity": "Alta",
      "status": "Resolvida",
      "timeline": [
        {
          "time": "24 jul · registro",
          "title": "Ocorrência aberta",
          "desc": "Equipamento identificado(a) em Oficina."
        }
      ]
    },
    {
      "id": "occurrences-7",
      "occ": "Ferrugem asiática — início",
      "tipo": "Doença",
      "talhao": "Talhão C2",
      "date": "22 jul",
      "severity": "Média",
      "status": "Em análise",
      "timeline": [
        {
          "time": "22 jul · registro",
          "title": "Ocorrência aberta",
          "desc": "Doença identificado(a) em Talhão C2."
        }
      ]
    }
  ]
};
  // Os mesmos valores iniciais das configurações também ficam disponíveis
  // para outras telas calcularem indicadores antes do primeiro salvamento.
  defaults.settings = {
    profile: {name: "João Silva", email: "joao@fazendasantarita.com", phone: "(19) 99999-1234", role: "Produtor rural"},
    property: {farm: "Fazenda Santa Rita", area: 1284, city: "Campinas, SP", harvest: "2025/26"},
    preferences: {stock: true, weather: true, activities: true, occurrences: true, reports: false}
  };
  const clone = (value) => JSON.parse(JSON.stringify(value));
  const text = (value) => typeof value === "string" && value.trim().length > 0;
  const string = (value) => typeof value === "string";
  const number = (value) => typeof value === "number" && Number.isFinite(value) && value >= 0;
  const positive = (value) => number(value) && value > 0;
  // Não aceitamos datas impossíveis ou movimentações sem instante válido.
  const isoDay = (value) => string(value) && /^\d{4}-\d{2}-\d{2}$/.test(value)
    && Number.isFinite(Date.parse(value + "T12:00:00Z"))
    && new Date(value + "T12:00:00Z").toISOString().slice(0, 10) === value;
  const isoInstant = (value) => string(value) && Number.isFinite(Date.parse(value))
    && new Date(value).toISOString() === value;
  const oneOf = (...values) => (value) => values.includes(value);
  const shape = (fields) => (value) => value && !Array.isArray(value) && typeof value === "object"
    && Object.keys(value).length === Object.keys(fields).length
    && Object.entries(fields).every(([key, valid]) => Object.hasOwn(value, key) && valid(value[key]));
  const list = (valid) => (value) => Array.isArray(value) && value.every(valid);
  const records = (fields) => (value) => list(shape({ id: text, ...fields }))(value)
    && new Set(value.map((item) => item.id)).size === value.length;
  const timeline = list(shape({time: text, title: text, desc: string}));
  const schemas = {
    plots: records({plot: text, crop: text, hectares: positive, status: oneOf("Ativo", "Irrigação pendente", "Em preparo")}),
    activities: records({activity: text, tipo: text, talhao: text, date: text,
      status: oneOf("Pendente", "Agendada", "Em andamento", "Concluída")}),
    stock: records({product: text, category: text, qty: number, unit: text, min: positive,
      movements: list(shape({id: text, type: oneOf("entrada", "saida"), qty: positive, date: isoInstant, note: string}))}),
    occurrences: records({occ: text, tipo: text, talhao: text, date: text,
      severity: oneOf("Alta", "Média", "Baixa"), status: oneOf("Aberta", "Em análise", "Resolvida"), timeline}),
    costs: shape({budget: positive, period: oneOf("Jan — Ago 2026", "Safra 2025/26"),
      entries: (value) => list(shape({id: positive, description: text,
        category: oneOf("Insumos", "Mão de Obra", "Maquinário", "Combustível", "Outros"),
        plot: string, date: isoDay, value: positive}))(value)
        && new Set(value.map((entry) => entry.id)).size === value.length}),
    notifications: list(text),
    settings: shape({profile: shape({name: text, email: text, phone: string, role: string}),
      property: shape({farm: text, area: positive, city: string, harvest: oneOf("2025/26", "2026/27", "2024/25")}),
      preferences: shape(Object.fromEntries(["stock", "weather", "activities", "occurrences", "reports"].map((key) => [key, (value) => typeof value === "boolean"])))})
  };
  const blocked = new Set();
  const known = new Map();
  const prefix = "agro.operational.v1.";
  const labels = {plots: "os talhões", activities: "as atividades", stock: "o estoque",
    occurrences: "as ocorrências", costs: "os custos", settings: "as configurações", notifications: "as notificações"};

  // Um aviso visível permanece na página: não depende da duração do toast.
  function report(message) {
    const show = () => {
      let banner = document.querySelector("[data-storage-error]");
      if (!banner) {
        banner = document.createElement("p");
        banner.dataset.storageError = "";
        banner.setAttribute("role", "alert");
        banner.style.cssText = "padding:12px;background:#fff1d6;color:#702d00;position:relative;z-index:10";
        (document.querySelector("main") || document.body).prepend(banner);
      }
      banner.textContent = message;
      window.AgroToast?.show({type: "error", title: "Dados locais não salvos", description: message});
    };
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", show, {once: true});
    else show();
  }

  function read(key, fallback = defaults[key], empty = []) {
    try {
      const raw = window.localStorage.getItem(prefix + key);
      const envelope = raw === null ? null : JSON.parse(raw);
      if (envelope && (envelope.version !== 1 || !schemas[key]?.(envelope.data))) throw new Error("invalid");
      if (raw !== null && !envelope) throw new Error("invalid");
      known.set(key, raw);
      return clone(envelope ? envelope.data : fallback);
    } catch (error) {
      blocked.add(key);
      report("Não foi possível recuperar " + labels[key] + ". O armazenamento está inválido ou indisponível. Nenhuma recuperação foi confirmada; os dados existentes não serão sobrescritos. Corrija o armazenamento e recarregue a página.");
      return clone(empty);
    }
  }

  function save(key, data) {
    try {
      if (blocked.has(key) || !schemas[key]?.(data)) throw new Error("invalid");
      // Impede que uma aba desatualizada apague silenciosamente alterações de outra.
      const current = window.localStorage.getItem(prefix + key);
      if (known.has(key) && current !== known.get(key)) throw new Error("conflict");
      const raw = JSON.stringify({version: 1, data});
      window.localStorage.setItem(prefix + key, raw);
      known.set(key, raw);
      return true;
    } catch (error) {
      report("Não foi possível salvar " + labels[key] + ". A alteração foi desfeita. Verifique o espaço ou a permissão do navegador e recarregue a página antes de tentar novamente.");
      return false;
    }
  }

  // Cada tela informa como extrair os registros e como remontar sua interface.
  // Se a gravação falhar, voltamos ao último estado confirmado antes do toast.
  function connect(key, capture, restore, fallback = defaults[key], empty = []) {
    let confirmed = read(key, fallback, empty);
    restore(clone(confirmed));
    return () => {
      const data = capture();
      if (!save(key, data)) { restore(clone(confirmed)); return false; }
      confirmed = clone(data);
      return true;
    };
  }

  const id = () => window.crypto?.randomUUID?.() || Date.now().toString(36) + Math.random().toString(36).slice(2);
  const escape = (value) => String(value).replace(/[&<>"']/g, (char) => ({"&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;"}[char]));
  window.AgroLocal = {read, save, connect, id, escape};
})();

/* Miguel — RA: 25003110 */
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
    // Acompanha o acionador durante a rolagem e fecha quando ele sai da área útil.
    document.addEventListener("scroll", (event) => {
      const inside = event.target instanceof Element && event.target.closest("[data-popover]");
      if (inside) return;
      $$("[data-popover].is-open").forEach((popover) => {
        const trigger = triggerOf(popover);
        if (!trigger) return closeAllPopovers();
        const rect = trigger.getBoundingClientRect();
        const clip = findScrollClipAncestor(trigger)?.getBoundingClientRect();
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
