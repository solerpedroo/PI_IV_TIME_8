/* Miguel — RA: 25003110 */
/**
 * Agro — interações da tela Atividades (híbrido com API e persistência local)
 * --------------------------------------------------------------------------
 * Fonte única de dados: os cards do Kanban (`.kanban-card`). A visualização
 * em Lista é derivada deles a cada mudança — não existe um segundo estado
 * para manter sincronizado manualmente.
 *
 * Cobre os fluxos de Atividades:
 * - Criação de atividades conectada à API `/api/atividades` e fallback local.
 * - Troca de status por menu, lista e arraste (Kanban Drag and Drop).
 * - Conclusão de atividades via `/api/atividades/{id}/concluir`.
 * - Cancelamento justificado via `/api/atividades/{id}/cancelar`.
 * - Estados terminais: atividades "Concluída" ou "Cancelada" não podem ter
 *   seu status alterado nem serem reabertas ou editadas.
 * - Histórico completo e linha do tempo de eventos via `/api/atividades/{id}/historico`.
 * - Persistência garantida após reload consultando a API ou cache seguro.
 */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

  const apiBase = window.location.port === "8080" ? window.location.origin : "http://127.0.0.1:8080";
  const getSessionToken = () => sessionStorage.getItem("agro.sessionToken");
  // Depois de uma leitura bem-sucedida, a API é a fonte de verdade. Antes
  // disso, o modo local continua atendendo a abertura isolada da interface.
  let apiDisponivel = false;

  const filters = {
    query: "",
    status: "",
    tipo: "",
    day: "",
    talhao: new URLSearchParams(window.location.search).get("talhao") || "",
  };

  /** Card em edição/conclusão/cancelamento/exclusão no momento (contexto dos modais). */
  let activeCard = null;
  let formMode = "create"; // "create" | "edit"
  let cardSeq = 100;

  let persist = () => true;
  function toast(type, title, description) {
    if (window.AgroToast) window.AgroToast.show({ type, title, description });
  }

  /**
   * Comunicação centralizada com a API REST do backend.
   * Envia o token de autenticação se disponível e trata respostas JSON e erros amigáveis.
   */
  async function apiRequest(path, options = {}) {
    const token = getSessionToken();
    if (!token) return null;
    const response = await fetch(apiBase + path, {
      ...options,
      headers: {
        Authorization: "Bearer " + token,
        "Content-Type": "application/json",
        ...(options.headers || {}),
      },
    });
    if (!response.ok) {
      const errorBody = await response.json().catch(() => ({}));
      const err = new Error(errorBody.erro || errorBody.mensagem || "Falha na comunicação com o servidor.");
      err.status = response.status;
      throw err;
    }
    return response.json();
  }

  /**
   * Verifica se o status informado é terminal.
   * Conforme modelo de dados e regras de negócio, atividades Concluídas ou Canceladas
   * encerram seu ciclo de vida operacional e não permitem novas alterações de status.
   */
  function isTerminalStatus(status) {
    return status === "Concluída" || status === "Cancelada";
  }

  /* ---------------------------------------------------------------------- */
  /* Modal helpers (genérico — reaproveitado pelos modais desta tela)       */
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

  const STATUS_ORDER = ["Pendente", "Agendada", "Em andamento", "Concluída", "Cancelada"];

  /**
   * Monta a linha da tabela na visualização em Lista.
   * Se a atividade estiver em estado terminal (Concluída ou Cancelada),
   * o select de status fica desabilitado para evitar transições inválidas.
   */
  function buildListRow(card) {
    const tr = document.createElement("tr");

    const nameTd = document.createElement("td");
    nameTd.textContent = card.dataset.activity;
    const tipoTd = document.createElement("td");
    tipoTd.textContent = card.dataset.tipo;
    const talhaoTd = document.createElement("td");
    talhaoTd.textContent = card.dataset.talhao;
    const dateTd = document.createElement("td");
    dateTd.textContent = card.dataset.date;

    const statusTd = document.createElement("td");
    statusTd.className = "col-status-select";
    const select = document.createElement("select");
    select.className = "status-select";
    select.setAttribute("aria-label", `Status de ${card.dataset.activity}`);

    const isTerminal = isTerminalStatus(card.dataset.status);

    STATUS_ORDER.forEach((status) => {
      const opt = document.createElement("option");
      opt.value = status;
      opt.textContent = status;
      if (status === card.dataset.status) opt.selected = true;
      select.appendChild(opt);
    });

    if (isTerminal) {
      select.disabled = true;
      select.title = "Atividades concluídas ou canceladas estão em estado terminal.";
    }

    select.addEventListener("change", () => {
      const novoStatus = select.value;
      if (isTerminalStatus(card.dataset.status)) {
        toast("warning", "Operação bloqueada", "Atividades concluídas ou canceladas não podem mudar de status.");
        select.value = card.dataset.status;
        return;
      }

      if (novoStatus === "Concluída") {
        select.value = card.dataset.status;
        openCompleteConfirm(card);
        return;
      }

      if (novoStatus === "Cancelada") {
        select.value = card.dataset.status;
        openCancelConfirm(card);
        return;
      }

      atualizarStatusAtividade(card, novoStatus);
    });

    statusTd.appendChild(select);
    tr.append(nameTd, tipoTd, talhaoTd, dateTd, statusTd);
    return tr;
  }

  /**
   * Move um card para outra coluna do Kanban e atualiza o badge e menu contextual.
   */
  function moveCardToStatus(card, newStatus) {
    if (card.dataset.status === newStatus) return;
    card.dataset.status = newStatus;

    const menuId = $("[data-popover]", card)?.id || `card-menu-${++cardSeq}`;
    const wrap = $(".action-menu-wrap", card);
    if (wrap) wrap.innerHTML = actionMenuMarkup(menuId, newStatus);

    const targetColumn = $(`.kanban-column[data-column="${newStatus}"] [data-column-cards]`);
    targetColumn?.appendChild(card);
  }

  /**
   * Atualiza o status de uma atividade intermediária (Pendente, Agendada, Em andamento)
   * sincronizando com a API do servidor e mantendo a consistência dos dados locais.
   */
  async function atualizarStatusAtividade(card, novoStatus) {
    const nomeAtividade = card.dataset.activity;
    const cardId = card.dataset.id;

    if (apiDisponivel && cardId) {
      try {
        await apiRequest(`/api/atividades/${encodeURIComponent(cardId)}/status`, {
          method: "POST", body: JSON.stringify({ status: novoStatus }),
        });
      } catch (err) {
        toast("error", "Status não alterado", err.message);
        return;
      }
    }

    moveCardToStatus(card, novoStatus);
    if (!persist()) return;
    recomputeMetrics();
    applyFilters();
    toast("success", "Status atualizado", `${nomeAtividade} agora está em "${novoStatus}".`);

  }

  /**
   * Recalcula os contadores de cabeçalho e os badges de contagem de cada coluna.
   */
  function recomputeMetrics() {
    const all = $$(".kanban-card");
    const counts = { Pendente: 0, Agendada: 0, "Em andamento": 0, Concluída: 0, Cancelada: 0 };
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
    const FADE_MS = 160;

    options.forEach((option) => {
      option.addEventListener("click", () => {
        if (option.getAttribute("aria-pressed") === "true") return;
        const isKanban = option.dataset.viewOption === "kanban";
        const showEl = isKanban ? kanbanRow : listCard;
        const hideEl = isKanban ? listCard : kanbanRow;
        if (!showEl || !hideEl) return;

        options.forEach((o) => o.setAttribute("aria-pressed", "false"));
        option.setAttribute("aria-pressed", "true");

        hideEl.classList.add("is-view-fading");
        window.setTimeout(() => {
          hideEl.hidden = true;
          showEl.hidden = false;
          showEl.classList.add("is-view-fading");
          void showEl.offsetWidth;
          requestAnimationFrame(() => showEl.classList.remove("is-view-fading"));
        }, FADE_MS);
      });
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Ações contextuais do card "⋮"                                          */
  /* ---------------------------------------------------------------------- */
  function actionMenuMarkup(id, status) {
    const isTerminal = isTerminalStatus(status);
    return `
      <button type="button" class="icon-btn icon-btn--sm" data-popover-trigger aria-controls="${id}" aria-expanded="false" aria-label="Mais ações">
        <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/></svg>
      </button>
      <div class="action-menu" id="${id}" data-popover role="menu">
        <button type="button" class="action-menu-item" role="menuitem" data-card-action="detail"><span class="action-menu-icon tone-info" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"/><circle cx="12" cy="12" r="3"/></svg></span><span class="action-menu-label">Ver detalhes</span></button>
        ${!isTerminal ? `<button type="button" class="action-menu-item" role="menuitem" data-card-action="edit"><span class="action-menu-icon tone-secondary" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg></span><span class="action-menu-label">Editar</span></button>` : ""}
        ${!isTerminal ? `<button type="button" class="action-menu-item" role="menuitem" data-card-action="complete"><span class="action-menu-icon tone-success" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M21.801 10A10 10 0 1 1 17 3.335"/><path d="m9 11 3 3L22 4"/></svg></span><span class="action-menu-label">Concluir</span></button>` : ""}
        ${!isTerminal ? `<button type="button" class="action-menu-item" role="menuitem" data-card-action="cancel"><span class="action-menu-icon tone-neutral" aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/></svg></span><span class="action-menu-label">Cancelar</span></button>` : ""}
        <button type="button" class="action-menu-item" role="menuitem" data-card-action="duplicate"><span class="action-menu-icon tone-primary" aria-hidden="true"><svg viewBox="0 0 24 24"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg></span><span class="action-menu-label">Duplicar</span></button>
        ${!apiDisponivel ? `<button type="button" class="action-menu-item is-danger" role="menuitem" data-card-action="delete"><span class="action-menu-icon tone-error" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg></span><span class="action-menu-label">Excluir</span></button>` : ""}
      </div>
    `;
  }

  function buildCard(values, seq) {
    const menuId = `card-menu-${seq}`;
    const raw = values;
    values = Object.fromEntries(Object.entries(values).map(([key, value]) => [key, window.AgroLocal.escape(value)]));
    const card = document.createElement("article");
    card.className = "kanban-card";
    card.dataset.id = raw.id || window.AgroLocal.id();
    card.tabIndex = 0;
    card.draggable = true;
    card.dataset.activity = raw.nome;
    card.dataset.tipo = raw.tipo;
    card.dataset.talhao = raw.talhao;
    card.dataset.date = raw.data;
    card.dataset.status = raw.status;

    const badgeTone = {
      Pendente: "badge-warning",
      Agendada: "badge-info",
      "Em andamento": "badge-warning",
      Concluída: "badge-success",
      Cancelada: "badge-neutral",
    }[raw.status] || "badge-neutral";

    card.innerHTML = `
      <div class="kanban-card-top">
        <span class="kanban-card-title">${values.nome}</span>
        <span class="badge ${badgeTone}">${values.tipo}</span>
      </div>
      <span class="kanban-card-meta"><svg viewBox="0 0 24 24"><path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"/><circle cx="12" cy="10" r="3"/></svg>${values.talhao}</span>
      <div class="kanban-card-footer">
        <span class="kanban-card-date"><svg viewBox="0 0 24 24"><path d="M8 2v4"/><path d="M16 2v4"/><rect width="18" height="18" x="3" y="4" rx="2"/><path d="M3 10h18"/></svg>${values.data}</span>
        <div class="action-menu-wrap">${actionMenuMarkup(menuId, raw.status)}</div>
      </div>
    `;
    return card;
  }

  /* ---------------------------------------------------------------------- */
  /* Arrastar card entre colunas (mover status pelo Kanban)                 */
  /* ---------------------------------------------------------------------- */
  let draggedCard = null;

  function initDragAndDrop() {
    const board = $("[data-kanban-board]");
    if (!board) return;

    board.addEventListener("dragstart", (event) => {
      const card = event.target.closest(".kanban-card");
      if (!card) return;
      draggedCard = card;
      card.classList.add("is-dragging");
      event.dataTransfer.effectAllowed = "move";
      event.dataTransfer.setData("text/plain", card.dataset.activity || "");
    });

    board.addEventListener("dragend", () => {
      draggedCard?.classList.remove("is-dragging");
      $$(".kanban-column", board).forEach((col) => col.classList.remove("is-drop-target"));
      draggedCard = null;
    });

    $$(".kanban-column", board).forEach((column) => {
      column.addEventListener("dragover", (event) => {
        if (!draggedCard) return;
        event.preventDefault();
        event.dataTransfer.dropEffect = "move";
        column.classList.add("is-drop-target");
      });

      column.addEventListener("dragleave", (event) => {
        if (!column.contains(event.relatedTarget)) column.classList.remove("is-drop-target");
      });

      column.addEventListener("drop", (event) => {
        event.preventDefault();
        column.classList.remove("is-drop-target");
        if (!draggedCard) return;

        const newStatus = column.dataset.column;
        const statusAnterior = draggedCard.dataset.status;
        if (statusAnterior === newStatus) return;

        if (isTerminalStatus(statusAnterior)) {
          toast("warning", "Operação bloqueada", "Atividades concluídas ou canceladas não podem mudar de status.");
          return;
        }

        if (newStatus === "Concluída") {
          activeCard = draggedCard;
          openCompleteConfirm(draggedCard);
          return;
        }

        if (newStatus === "Cancelada") {
          activeCard = draggedCard;
          openCancelConfirm(draggedCard);
          return;
        }

        atualizarStatusAtividade(draggedCard, newStatus);
      });
    });
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
      else if (action === "cancel") openCancelConfirm(card);
      else if (action === "duplicate") openDuplicateForm(card);
      else if (action === "delete") openDeleteConfirm(card);
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Detalhes e Linha do Tempo de Eventos (Histórico)                       */
  /* ---------------------------------------------------------------------- */
  async function openDetail(card) {
    const modal = $("[data-modal='activity-detail']");
    $("[data-detail-title]", modal).textContent = card.dataset.activity;
    $("[data-detail-subtitle]", modal).textContent = `${card.dataset.talhao} · ${card.dataset.date}`;
    $("[data-detail-meta]", modal).innerHTML = `
      <div class="meta-cell"><span class="meta-cell-label">Tipo</span><span class="meta-cell-value">${window.AgroLocal.escape(card.dataset.tipo)}</span></div>
      <div class="meta-cell"><span class="meta-cell-label">Talhão</span><span class="meta-cell-value">${window.AgroLocal.escape(card.dataset.talhao)}</span></div>
      <div class="meta-cell"><span class="meta-cell-label">Data</span><span class="meta-cell-value">${window.AgroLocal.escape(card.dataset.date)}</span></div>
      <div class="meta-cell"><span class="meta-cell-label">Status</span><span class="meta-cell-value">${window.AgroLocal.escape(card.dataset.status)}</span></div>
    `;

    const timelineContainer = $("[data-detail-timeline]", modal);
    if (timelineContainer) {
      timelineContainer.innerHTML = `<p style="font-size: 12px; color: var(--text-muted); padding: 8px 0;">Consultando histórico da atividade...</p>`;
      await renderActivityTimeline(card, timelineContainer);
    }

    openModal(modal);
  }

  /**
   * Consulta a API `/api/atividades/{id}/historico` e renderiza a linha do tempo.
   * Se a chamada falhar ou estiver em modo puramente local, sintetiza os eventos
   * registrados no ciclo de vida da atividade.
   */
  async function renderActivityTimeline(card, container) {
    const id = card.dataset.id;
    let eventos = [];

    if (getSessionToken() && id) {
      try {
        eventos = await apiRequest(`/api/atividades/${encodeURIComponent(id)}/historico`);
      } catch {
        eventos = [];
      }
    }

    if (!Array.isArray(eventos) || eventos.length === 0) {
      eventos = [
        {
          versao: 0,
          dataHora: card.dataset.date || "Data inicial",
          autor: "Sistema",
          statusAnterior: "",
          statusNovo: "Pendente",
          descricao: `Atividade "${card.dataset.activity}" planejada para o talhão ${card.dataset.talhao}.`,
        },
      ];
      if (card.dataset.status !== "Pendente") {
        eventos.push({
          versao: 1,
          dataHora: new Date().toISOString().replace("T", " ").substring(0, 16),
          autor: "Operador",
          statusAnterior: "Pendente",
          statusNovo: card.dataset.status,
          descricao: `Status atualizado para ${card.dataset.status}.`,
        });
      }
    }

    // Ordenação do mais recente para o mais antigo para leitura imediata do estado atual
    const eventosOrdenados = [...eventos].reverse();

    container.innerHTML = "";
    eventosOrdenados.forEach((ev, index) => {
      const isLatest = index === 0;
      const entry = document.createElement("div");
      entry.className = `timeline-entry${isLatest ? " is-latest" : ""}`;

      const rail = document.createElement("div");
      rail.className = "timeline-rail";
      const dot = document.createElement("div");
      dot.className = "timeline-dot";
      const line = document.createElement("div");
      line.className = "timeline-line";
      rail.append(dot, line);

      const body = document.createElement("div");
      body.className = "timeline-body";

      const time = document.createElement("span");
      time.className = "timeline-time";
      const dataHoraStr = ev.registradoEm || ev.dataHora || "";
      time.textContent = dataHoraStr ? dataHoraStr.replace("T", " ").substring(0, 16) : "Data/hora não registrada";

      const title = document.createElement("h4");
      title.className = "timeline-title";
      const statusFinal = ev.status || ev.statusNovo || "";
      const acao = ev.acao || "";
      let tituloTexto = "Atualização de status";
      if (ev.ordem === 0 || ev.versao === 0 || acao === "criacao") tituloTexto = "Atividade criada";
      else if (statusFinal === "Concluída" || acao === "conclusao") tituloTexto = "Atividade concluída";
      else if (statusFinal === "Cancelada" || acao === "cancelamento") tituloTexto = "Atividade cancelada";
      else if (statusFinal) tituloTexto = `Status: ${statusFinal}`;
      title.textContent = tituloTexto;

      const desc = document.createElement("p");
      desc.className = "timeline-desc";
      let descTexto = ev.descricao || "";
      if (ev.motivo) {
        descTexto += ` (Motivo informado: "${ev.motivo}")`;
      }
      if (ev.autor) {
        descTexto += ` · Por ${ev.autor}`;
      }
      desc.textContent = descTexto;

      body.append(time, title, desc);
      entry.append(rail, body);
      container.appendChild(entry);
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Confirmações de Conclusão e Cancelamento                               */
  /* ---------------------------------------------------------------------- */
  function openCompleteConfirm(card) {
    activeCard = card;
    const modal = $("[data-modal='activity-complete']");
    $("[data-complete-desc]", modal).textContent = `${card.dataset.activity} será marcada como concluída e não poderá ser reaberta.`;
    openModal(modal);
  }

  function initCompleteConfirm() {
    const modal = $("[data-modal='activity-complete']");
    $("[data-confirm-complete]", modal)?.addEventListener("click", async () => {
      if (!activeCard) return;
      const card = activeCard;
      const id = card.dataset.id;
      const name = card.dataset.activity;
      const talhao = card.dataset.talhao;

      if (apiDisponivel && id) {
        try {
          await apiRequest(`/api/atividades/${encodeURIComponent(id)}/concluir`, {
            method: "POST", body: JSON.stringify({}),
          });
        } catch (err) {
          toast("error", "Conclusão não confirmada", err.message);
          return;
        }
      }

      $(".kanban-card-top .badge", card).className = "badge badge-success";
      moveCardToStatus(card, "Concluída");
      if (!persist()) return;

      closeModal(modal);
      toast("success", "Atividade concluída", `${name} marcada como concluída em ${talhao}.`);
      document.dispatchEvent(new CustomEvent("agro:operation-saved", {
        detail: { tipo: "atividade", descricao: `Conclusão: ${name}`, talhao },
      }));
      applyFilters();
      activeCard = null;

    });
  }

  function openCancelConfirm(card) {
    activeCard = card;
    const modal = $("[data-modal='activity-cancel']");
    const reasonInput = $("[data-cancel-reason]", modal);
    if (reasonInput) reasonInput.value = "";
    $("[data-cancel-desc]", modal).textContent = `${card.dataset.activity} será cancelada e não poderá mais ser reaberta ou alterada.`;
    openModal(modal);
  }

  function initCancelConfirm() {
    const modal = $("[data-modal='activity-cancel']");
    $("[data-confirm-cancel]", modal)?.addEventListener("click", async () => {
      if (!activeCard) return;
      const card = activeCard;
      const id = card.dataset.id;
      const name = card.dataset.activity;
      const talhao = card.dataset.talhao;
      const reasonInput = $("[data-cancel-reason]", modal);
      const motivo = reasonInput ? reasonInput.value.trim() : "";

      if (apiDisponivel && id) {
        try {
          await apiRequest(`/api/atividades/${encodeURIComponent(id)}/cancelar`, {
            method: "POST", body: JSON.stringify({ motivo }),
          });
        } catch (err) {
          toast("error", "Cancelamento não confirmado", err.message);
          return;
        }
      }

      $(".kanban-card-top .badge", card).className = "badge badge-neutral";
      moveCardToStatus(card, "Cancelada");
      if (!persist()) return;

      closeModal(modal);
      toast("info", "Atividade cancelada", `${name} foi cancelada.${motivo ? ` Motivo: ${motivo}` : ""}`);
      document.dispatchEvent(new CustomEvent("agro:operation-saved", {
        detail: { tipo: "atividade", descricao: `Cancelamento: ${name}${motivo ? ` (${motivo})` : ""}`, talhao },
      }));
      applyFilters();
      activeCard = null;

    });
  }

  function openDeleteConfirm(card) {
    activeCard = card;
    openModal($("[data-modal='activity-delete']"));
  }

  function initDeleteConfirm() {
    const modal = $("[data-modal='activity-delete']");
    $("[data-confirm-delete]", modal)?.addEventListener("click", () => {
      if (!activeCard) return;
      const name = activeCard.dataset.activity;
      activeCard.remove();
      if (!persist()) return;
      closeModal(modal);
      toast("info", "Atividade excluída", `${name} foi removida.`);
      applyFilters();
      activeCard = null;
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Formulário de Criação / Edição / Duplicação                            */
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
    if (isTerminalStatus(card.dataset.status)) {
      toast("warning", "Edição não permitida", "Atividades concluídas ou canceladas não podem ser alteradas.");
      return;
    }
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

  function initActivityForm() {
    const modal = $("[data-modal='activity-form']");
    const form = $("[data-activity-form]", modal);
    if (!modal || !form) return;

    $$("[data-open-modal='activity-form']").forEach((btn) => btn.addEventListener("click", openCreateForm));

    form.addEventListener("submit", async (event) => {
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
        if (isTerminalStatus(activeCard.dataset.status)) {
          toast("error", "Edição bloqueada", "Atividades concluídas ou canceladas não podem ser alteradas.");
          closeModal(modal);
          return;
        }

        const cardId = activeCard.dataset.id;
        if (apiDisponivel && cardId) {
          try {
            await apiRequest(`/api/atividades/${encodeURIComponent(cardId)}`, {
              method: "PUT", body: JSON.stringify({ nome, tipo, talhao, data }),
            });
          } catch (err) {
            toast("error", "Edição não confirmada", err.message);
            return;
          }
        }
        activeCard.dataset.activity = nome;
        activeCard.dataset.tipo = tipo;
        activeCard.dataset.talhao = talhao;
        activeCard.dataset.date = data;
        $(".kanban-card-title", activeCard).textContent = nome;
        $(".kanban-card-top .badge", activeCard).textContent = tipo;
        $(".kanban-card-meta", activeCard).lastChild.textContent = talhao;
        $(".kanban-card-date", activeCard).lastChild.textContent = data;
        if (!persist()) return;
        toast("success", "Atividade atualizada", `${nome} foi salva com sucesso.`);

      } else {
        cardSeq += 1;
        const cardId = window.AgroLocal.id();
        let idConfirmado = cardId;
        if (apiDisponivel) {
          try {
            const criada = await apiRequest("/api/atividades", {
              method: "POST", body: JSON.stringify({ nome, tipo, talhao, data }),
            });
            idConfirmado = String(criada.id);
          } catch (err) {
            toast("error", "Atividade não criada", err.message);
            return;
          }
        }
        const card = buildCard({ id: idConfirmado, nome, tipo, talhao, data, status: "Pendente" }, cardSeq);
        $("[data-column='Pendente'] [data-column-cards]").appendChild(card);
        if (!persist()) return;
        toast("success", "Atividade criada", `${nome} agendada em ${talhao}.`);

      }

      document.dispatchEvent(new CustomEvent("agro:operation-saved", { detail: { tipo: "atividade", descricao: nome, talhao } }));
      closeModal(modal);
      form.reset();
      applyFilters();
    });
  }

  function initPrepToasts() {
    $$("[data-prep-toast]").forEach((el) => {
      el.addEventListener("click", () => {
        toast("info", "Módulo em preparação", el.dataset.prepToast);
      });
    });
  }

  /**
   * Recria os cards do Kanban e da Lista a partir dos registros recuperados.
   */
  function restoreActivities(records) {
    const activeId = activeCard?.dataset.id;
    $$("[data-column-cards]").forEach((column) => column.replaceChildren());
    records.forEach((record) => {
      const card = buildCard({
        id: record.id,
        nome: record.activity,
        tipo: record.tipo,
        talhao: record.talhao,
        data: record.date,
        status: record.status,
      }, ++cardSeq);
      $(`[data-column="${record.status}"] [data-column-cards]`)?.appendChild(card);
    });
    activeCard = $$(".kanban-card").find((card) => card.dataset.id === activeId) || null;
    applyFilters();
  }

  /**
   * Consulta a API do backend para sincronizar as atividades persistidas do usuário autenticado.
   * Se a consulta responder com dados, restaura o board com os estados reais.
   */
  async function carregarAtividadesRemotas() {
    if (!getSessionToken()) return;
    try {
      const lista = await apiRequest("/api/atividades");
      if (Array.isArray(lista)) {
        const records = lista.map((item) => ({
          id: String(item.id),
          activity: item.nome,
          tipo: item.tipo,
          talhao: item.talhao,
          date: item.data,
          status: item.status,
        }));
        apiDisponivel = true;
        restoreActivities(records);
        window.AgroLocal.save("activities", records);
      }
    } catch {
      // Mantém o cache local se o servidor estiver temporariamente indisponível
    }
  }

  function init() {
    persist = window.AgroLocal.connect("activities", () => $$(".kanban-card").map((row) => ({
      id: row.dataset.id,
      activity: row.dataset.activity,
      tipo: row.dataset.tipo,
      talhao: row.dataset.talhao,
      date: row.dataset.date,
      status: row.dataset.status,
    })), restoreActivities);

    initGenericModals();
    initPrepToasts();
    initTalhaoFilterChip();
    initFilterControls();
    initViewToggle();
    initDragAndDrop();
    initCardActions();
    initActivityForm();
    initCompleteConfirm();
    initCancelConfirm();
    initDeleteConfirm();
    applyFilters();

    carregarAtividadesRemotas();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
