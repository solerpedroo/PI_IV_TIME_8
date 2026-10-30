/* Miguel — RA: 25003110 */
/* Autor secundário: Tomas de Paula Michelon Toniato - RA: 25004211 */
/* Custos: a API é a fonte dos lançamentos e dos indicadores. Lista, cards,
 * gráfico, cultura, evolução e CSV saem da mesma consulta filtrada; só o
 * orçamento e o período escolhido ficam guardados neste navegador. */
(() => {
  "use strict";

  /* Atalhos pequenos mantêm a leitura das funções de UI objetiva. */
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const money = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL", maximumFractionDigits: 2 });
  const base = location.port === "8080" ? location.origin : "http://127.0.0.1:8080";
  const CATEGORIES = ["Insumos", "Mão de Obra", "Maquinário", "Combustível", "Outros"];
  const params = new URLSearchParams(location.search);

  /* entries e summary são sempre a última resposta confirmada pelo servidor. */
  const state = {
    budget: window.AgroLocal.initial("costs").budget,
    period: "todo",
    from: "",
    to: "",
    filter: "Todos",
    search: "",
    talhaoId: params.get("talhaoId") || "",
    safraId: params.get("safraId") || "",
    entries: [],
    summary: null,
    talhoes: [],
    safras: []
  };

  let persist = () => true;
  let loadNumber = 0;
  let busy = false;
  let searchTimer = 0;
  function toast(type, title, description) {
    window.AgroToast?.show({ type, title, description });
  }

  async function request(path, options = {}) {
    const token = sessionStorage.getItem("agro.sessionToken");
    if (!token) { location.replace("login.html"); throw new Error("Entre novamente."); }
    const response = await fetch(base + path, { ...options, headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" }, signal: AbortSignal.timeout(15000) });
    const data = await response.json().catch(() => ({}));
    if (response.status === 401) { sessionStorage.removeItem("agro.sessionToken"); location.replace("login.html"); }
    if (!response.ok) {
      const error = new Error(data.error === "Validation failed" ? Object.values(data.fields || {}).join(" ") || "Revise os campos informados." : data.error || "Falha na API.");
      error.fields = data.fields;
      throw error;
    }
    return data;
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
    trigger.id = `${input.id}-trigger`;
    // O rótulo continua acionando o controle visível após trocar o input nativo.
    document.querySelector(`label[for="${input.id}"]`)?.setAttribute('for', trigger.id);
    trigger.className = "date-picker-trigger";
    trigger.dataset.openDateModal = "";
    trigger.setAttribute("aria-haspopup", "dialog");
    trigger.setAttribute("aria-controls", "date-form");
    trigger.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><rect width="18" height="18" x="3" y="4" rx="2"/><path d="M16 2v4"/><path d="M8 2v4"/><path d="M3 10h18"/></svg><span data-date-label>Selecione a data</span>';
    input.parentNode.insertBefore(trigger, input);

    const modal = document.createElement("div");
    modal.className = "modal-backdrop";
    modal.id = "date-form";
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
    if (!value) return "-";
    return new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short" }).format(new Date(`${value}T12:00:00`)).replace(".", "");
  }

  function formatCompact(value) {
    return value >= 1000 ? `R$ ${(value / 1000).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} mil` : money.format(value);
  }

  function percent(part, total) {
    return `${(total > 0 ? (part / total) * 100 : 0).toLocaleString("pt-BR", { maximumFractionDigits: 1 })}%`;
  }

  /* ------------------------------------------------------------ período */

  // O "hoje" segue o fuso da fazenda, igual ao servidor.
  function today() {
    const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(new Date());
    return ["year", "month", "day"].map((key) => parts.find((part) => part.type === key).value).join("-");
  }

  const PERIODS = [
    { key: "todo", label: "Todo o período", hint: "Todos os lançamentos da propriedade" },
    { key: "mes", label: "Mês atual", hint: "Do primeiro ao último dia do mês" },
    { key: "trimestre", label: "Últimos 3 meses", hint: "Mês atual e os dois anteriores" },
    { key: "ano", label: "Ano atual", hint: "De janeiro a dezembro" },
    { key: "personalizado", label: "Personalizado", hint: "Escolha as datas inicial e final" }
  ];

  // Intervalo {de, ate} em AAAA-MM-DD; campos vazios não restringem a consulta.
  function periodRange(key = state.period, from = state.from, to = state.to) {
    const [year, month] = today().split("-").map(Number);
    const lastDay = (y, m) => dateToISO(new Date(y, m, 0));
    if (key === "mes") return { de: dateToISO(new Date(year, month - 1, 1)), ate: lastDay(year, month) };
    if (key === "trimestre") return { de: dateToISO(new Date(year, month - 3, 1)), ate: lastDay(year, month) };
    if (key === "ano") return { de: `${year}-01-01`, ate: `${year}-12-31` };
    if (key === "personalizado") return { de: from, ate: to };
    return { de: "", ate: "" };
  }

  function periodLabel() {
    const { de, ate } = periodRange();
    const short = (value) => value.split("-").reverse().join("/");
    if (state.period !== "personalizado") return PERIODS.find((item) => item.key === state.period).label;
    if (de && ate) return `${short(de)} a ${short(ate)}`;
    return de ? `A partir de ${short(de)}` : `Até ${short(ate)}`;
  }

  // Guardado como "mes" ou "personalizado:2026-01-01:2026-03-31".
  function periodToken() {
    return state.period === "personalizado" ? `personalizado:${state.from}:${state.to}` : state.period;
  }

  function readPeriod(token) {
    const [key, from = "", to = ""] = String(token || "").split(":");
    const known = PERIODS.some((item) => item.key === key) && (key !== "personalizado" || from || to);
    state.period = known ? key : "todo";
    state.from = state.period === "personalizado" ? from : "";
    state.to = state.period === "personalizado" ? to : "";
  }

  /* ------------------------------------------------------------ consulta */

  function query() {
    const { de, ate } = periodRange();
    const values = { category: state.filter === "Todos" ? "" : state.filter, talhaoId: state.talhaoId, safraId: state.safraId, de, ate, q: state.search };
    return new URLSearchParams(Object.entries(values).filter(([, value]) => value)).toString();
  }

  function message(text, retry = false) {
    $("[data-cost-message]").textContent = text;
    $("[data-cost-retry]").hidden = !retry;
  }

  // Lista e resumo usam os mesmos filtros; uma resposta antiga nunca substitui a mais recente.
  async function load() {
    const number = ++loadNumber;
    message("Carregando lançamentos…");
    try {
      const filters = query();
      const [entries, summary] = await Promise.all([request(`/api/custos?${filters}`), request(`/api/custos/resumo?${filters}`)]);
      if (number !== loadNumber) return;
      state.entries = entries;
      state.summary = summary;
      message("");
    } catch (error) {
      if (number !== loadNumber) return;
      state.entries = [];
      state.summary = null;
      message(`${error.message} Os valores exibidos não foram atualizados.`, true);
    }
    renderAll();
  }

  /* ------------------------------------------------------------ renderização */

  function renderMetrics() {
    const summary = state.summary;
    const total = summary ? summary.total : 0;
    const categoryTotal = (name) => summary?.porCategoria.find((item) => item.categoria === name)?.total || 0;
    $("[data-cost-total]").textContent = summary ? formatCompact(total) : "-";
    $("[data-cost-total-desc]").textContent = summary ? `${summary.quantidade} lançamento${summary.quantidade === 1 ? "" : "s"} no recorte` : "lançamentos do recorte";
    $("[data-cost-hectare]").textContent = summary?.custoPorHectare == null ? "-" : money.format(summary.custoPorHectare);
    $("[data-cost-hectare-desc]").textContent = summary?.areaHectares > 0
      ? `sobre ${summary.areaHectares.toLocaleString("pt-BR", { maximumFractionDigits: 2 })} ha de talhões`
      : "sem área de talhão no recorte";
    ["Insumos", "Mão de Obra"].forEach((category) => {
      const element = $(`[data-cost-category="${category}"]`);
      element.textContent = summary ? formatCompact(categoryTotal(category)) : "-";
      element.closest(".metric-card").querySelector(".metric-card-desc").textContent = summary ? `${percent(categoryTotal(category), total)} do custo total` : "do custo total";
    });

    const usage = state.budget > 0 ? (total / state.budget) * 100 : 0;
    const remaining = state.budget - total;
    $("[data-budget-total]").textContent = money.format(state.budget);
    $("[data-budget-spent]").textContent = summary ? money.format(total) : "-";
    $("[data-budget-percent]").textContent = summary ? `${Math.round(usage)}% utilizado` : "-";
    $("[data-budget-remaining]").textContent = !summary ? "" : remaining >= 0 ? `${money.format(remaining)} restantes` : `${money.format(-remaining)} acima do orçamento`;
    $("[data-budget-progress]").style.width = `${summary ? Math.min(100, usage) : 0}%`;
  }

  function bars(rows) {
    const max = Math.max(...rows.map((row) => row.value), 1);
    return rows.map((row) => `<div class="cost-bar-row"><div class="cost-bar-heading"><span>${window.AgroLocal.escape(row.label)}</span><strong>${formatCompact(row.value)}</strong></div><div class="cost-bar-track"><span class="cost-bar-fill ${row.css || ""}" style="width:${row.value ? Math.max(4, (row.value / max) * 100) : 0}%"></span></div></div>`).join("");
  }

  function renderChart() {
    const css = { "Mão de Obra": "is-labor", Maquinário: "is-machinery", Combustível: "is-fuel", Outros: "is-other" };
    const totals = state.summary?.porCategoria || [];
    $("[data-cost-chart]").innerHTML = bars(CATEGORIES.map((category) => ({ label: category, css: css[category], value: totals.find((item) => item.categoria === category)?.total || 0 })));
  }

  /* A cultura vem da safra do lançamento ou, sem safra, do talhão. Gastos sem
   * nenhum dos dois não são atribuídos a uma cultura por suposição. */
  function renderCropCosts() {
    const list = $("[data-crop-costs]");
    const groups = state.summary?.porCultura || [];
    const total = state.summary?.total || 0;
    const colors = { soja: "crop-soja", milho: "crop-milho", trigo: "crop-trigo", pastagem: "crop-pastagem" };
    if (!groups.length) {
      list.innerHTML = '<p class="cost-row-muted crop-cost-empty">Nenhum custo no recorte.</p>';
      return;
    }
    list.innerHTML = groups.map((group) => {
      const label = group.cultura || "Sem vínculo com cultura";
      const color = colors[String(group.cultura).toLowerCase()] || "";
      return `<div class="crop-cost-item"><span class="crop-cost-dot ${color}" aria-hidden="true"></span><span class="crop-cost-info"><strong>${window.AgroLocal.escape(label)}</strong><small>${percent(group.total, total)} do recorte</small></span><strong>${money.format(group.total)}</strong></div>`;
    }).join("");
  }

  function renderEvolution() {
    const host = $("[data-cost-evolution]");
    const months = state.summary?.evolucao || [];
    if (!months.length) {
      host.innerHTML = '<p class="cost-row-muted">Sem lançamentos no recorte.</p>';
      return;
    }
    const name = (month) => new Intl.DateTimeFormat("pt-BR", { month: "short", year: "numeric" }).format(new Date(`${month}-15T12:00:00`)).replace(".", "");
    host.innerHTML = bars(months.map((item) => ({ label: name(item.mes), value: item.total })));
  }

  function actionMarkup(entry) {
    const id = window.AgroLocal.escape(entry.id);
    return `<div class="action-menu-wrap"><button class="icon-btn icon-btn--sm" type="button" data-popover-trigger aria-controls="cost-menu-${id}" aria-expanded="false" aria-label="Ações de ${window.AgroLocal.escape(entry.description)}"><svg viewBox="0 0 24 24"><circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/></svg></button><div class="action-menu" id="cost-menu-${id}" data-popover role="menu"><button class="action-menu-item" type="button" role="menuitem" data-edit-cost="${id}"><span class="action-menu-icon tone-primary"><svg viewBox="0 0 24 24"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg></span><span class="action-menu-label">Editar</span></button><button class="action-menu-item is-danger" type="button" role="menuitem" data-delete-cost="${id}"><span class="action-menu-icon tone-error"><svg viewBox="0 0 24 24"><path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="m19 6-1 14H6L5 6"/></svg></span><span class="action-menu-label">Excluir</span></button></div></div>`;
  }

  // Custo de consumo (#156) só muda pela movimentação do insumo: no lugar do menu,
  // um atalho para o produto no estoque.
  function originMarkup(entry) {
    const insumo = encodeURIComponent(entry.origem.insumoId || "");
    return `<a class="icon-btn icon-btn--sm cost-origin-link" href="insumos.html?insumoId=${insumo}" title="Gerado pelo consumo de estoque. Corrija pela movimentação do insumo." aria-label="Ver consumo de ${window.AgroLocal.escape(entry.description)} no estoque"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z"/><path d="M12 22V12"/><path d="m3.3 7 8.7 5 8.7-5"/></svg></a>`;
  }

  function renderTable() {
    const escape = window.AgroLocal.escape;
    const entries = state.entries;
    $("[data-cost-table] tbody").innerHTML = entries.map((entry) => `<tr data-cost-id="${escape(entry.id)}"><td><span class="cost-row-description">${escape(entry.description)}</span></td><td><span class="badge badge-neutral">${escape(entry.category)}</span>${entry.origem ? ' <span class="badge badge-info cost-origin">Consumo</span>' : ""}</td><td><span class="cost-row-muted">${escape(entry.talhaoNome || "Propriedade")}</span></td><td><span class="cost-row-muted">${escape(entry.safraNome || "-")}</span></td><td><span class="cost-row-muted">${formatDate(entry.date)}</span></td><td class="cost-value">${money.format(entry.value)}</td><td>${entry.origem ? originMarkup(entry) : actionMarkup(entry)}</td></tr>`).join("");
    $("[data-cost-table]").hidden = entries.length === 0;
    $("[data-cost-empty]").hidden = entries.length > 0 || !state.summary;
    $("[data-cost-count]").textContent = state.summary ? `${entries.length} lançamento${entries.length === 1 ? "" : "s"}` : "-";

    const context = $("[data-cost-context]");
    const plot = state.talhoes.find((item) => item.id === state.talhaoId)?.nome || (state.talhaoId === "propriedade" ? "somente propriedade" : "");
    const season = state.safras.find((item) => item.id === state.safraId)?.nome || (state.safraId === "nenhuma" ? "sem safra" : "");
    const parts = [plot && `Talhão: ${plot}`, season && `Safra: ${season}`].filter(Boolean);
    context.hidden = parts.length === 0;
    context.textContent = parts.join(" · ");
  }

  function renderAll() {
    $("[data-period-label]").textContent = periodLabel();
    $("[data-cost-period]").textContent = periodLabel();
    renderMetrics();
    renderChart();
    renderCropCosts();
    renderEvolution();
    renderTable();
  }

  /* ------------------------------------------------------------ vínculos */

  function fillSelect(select, fixed, records, label, disabled = () => false) {
    const current = select.value;
    select.replaceChildren(...fixed.map(([value, text]) => new Option(text, value)), ...records.map((record) => {
      const option = new Option(label(record), record.id);
      option.disabled = disabled(record);
      return option;
    }));
    select.value = [...select.options].some((option) => option.value === current) ? current : fixed[0][0];
  }

  const seasonLabel = (safra) => `${safra.nome} · ${safra.cultura}${safra.etapa === "Finalização" ? " (finalizada)" : ""}`;

  function fillFilters() {
    fillSelect($("[data-cost-filter-plot]"), [["", "Todos os talhões"], ["propriedade", "Somente propriedade"]], state.talhoes, (talhao) => talhao.nome);
    fillSelect($("[data-cost-filter-season]"), [["", "Todas as safras"], ["nenhuma", "Sem safra"]], state.safras, seasonLabel);
    $("[data-cost-filter-plot]").value = state.talhaoId;
    $("[data-cost-filter-season]").value = state.safraId;
    // Um vínculo que não existe mais na propriedade deixa de filtrar.
    state.talhaoId = $("[data-cost-filter-plot]").value;
    state.safraId = $("[data-cost-filter-season]").value;
    $$("[data-cost-filter-plot], [data-cost-filter-season]").forEach((select) => select.dispatchEvent(new Event("change")));
  }

  // Safra finalizada não recebe lançamento novo; a edição de um antigo mantém a opção visível.
  function fillFormSelects(entry = null) {
    const form = $("[data-cost-form]");
    fillSelect(form.elements.talhaoId, [["", "Propriedade (sem talhão)"]], state.talhoes, (talhao) => talhao.nome);
    fillSelect(form.elements.safraId, [["", "Sem safra"]], state.safras, seasonLabel,
      (safra) => safra.etapa === "Finalização" && safra.id !== entry?.safraId);
  }

  async function loadReferences() {
    const [me, talhoes, safras] = await Promise.all([request("/api/me"), request("/api/talhoes"), request("/api/safras")]);
    state.talhoes = talhoes;
    state.safras = safras;
    // Links de Áreas de Cultivo e Safras chegam com ?talhao=<nome>.
    const byName = params.get("talhao") && talhoes.find((talhao) => talhao.nome === params.get("talhao"));
    if (byName && !state.talhaoId) state.talhaoId = byName.id;
    const property = me.propriedade?.nome || me.propriedade?.farm;
    if (property) $("[data-cost-property]").textContent = `Acompanhamento financeiro · ${property}`;
    fillFilters();
  }

  /* ------------------------------------------------------------ formulário */

  function clearErrors(form) {
    $$(".field-error", form).forEach((error) => { error.textContent = ""; });
    $$('[aria-invalid="true"]', form).forEach((control) => control.removeAttribute("aria-invalid"));
  }

  function setError(form, name, message) {
    const control = form.elements[name];
    const error = $(`[data-error-for="${name}"]`, form);
    if (control) {
      if (message) control.setAttribute("aria-invalid", "true");
      else control.removeAttribute("aria-invalid");
    }
    if (error) error.textContent = message;
  }

  function openCostModal(entry = null) {
    const modal = $("[data-modal='cost-form']");
    const form = $("[data-cost-form]");
    form.reset();
    clearErrors(form);
    fillFormSelects(entry);
    form.dataset.editingId = entry ? entry.id : "";
    if (entry) {
      form.elements.description.value = entry.description;
      form.elements.category.value = entry.category;
      form.elements.value.value = entry.value;
      form.elements.date.value = entry.date;
      form.elements.talhaoId.value = entry.talhaoId || "";
      form.elements.safraId.value = entry.safraId || "";
      $("#cost-form-title").textContent = "Editar custo";
    } else {
      form.elements.date.value = today();
      // Um lançamento novo já nasce no vínculo que está sendo consultado.
      if (state.talhoes.some((talhao) => talhao.id === state.talhaoId)) form.elements.talhaoId.value = state.talhaoId;
      if (state.safras.some((safra) => safra.id === state.safraId && safra.etapa !== "Finalização")) form.elements.safraId.value = state.safraId;
      $("#cost-form-title").textContent = "Registrar custo";
    }
    ["category", "talhaoId", "safraId"].forEach((name) => form.elements[name].dispatchEvent(new Event("change")));
    form.elements.date.dispatchEvent(new Event("change", { bubbles: true }));
    modal.classList.add("is-open");
    modal.setAttribute("aria-hidden", "false");
    form.elements.description.focus();
  }

  function closeCostModal() {
    const modal = $("[data-modal='cost-form']");
    modal.classList.remove("is-open");
    modal.setAttribute("aria-hidden", "true");
  }

  function setModalState(name, open) {
    const modal = $(`[data-modal="${name}"]`);
    if (!modal) return;
    modal.classList.toggle("is-open", open);
    modal.setAttribute("aria-hidden", String(!open));
  }

  function setBusy(value) {
    busy = value;
    $$("[data-cost-form] button[type=submit], [data-delete-cost], [data-confirm-delete-cost]").forEach((button) => { button.disabled = value; });
  }

  // O sucesso só é anunciado depois da resposta do servidor; a lista é relida em seguida.
  async function saveCost(form, payload) {
    if (busy) return;
    const editingId = form.dataset.editingId;
    const errorHost = $("[data-cost-form-error]", form);
    errorHost.textContent = "";
    setBusy(true);
    try {
      await request(editingId ? `/api/custos/${encodeURIComponent(editingId)}` : "/api/custos", { method: editingId ? "PUT" : "POST", body: JSON.stringify(payload) });
    } catch (error) {
      if (error.fields) Object.entries(error.fields).forEach(([name, text]) => setError(form, name, text));
      else errorHost.textContent = `${error.message} Confira a lista antes de repetir o envio.`;
      toast("error", "Falha ao salvar", error.message);
      setBusy(false);
      return;
    }
    const talhao = state.talhoes.find((item) => item.id === payload.talhaoId);
    toast("success", editingId ? "Custo atualizado" : "Custo registrado", `${payload.description} foi confirmado pelo servidor.`);
    document.dispatchEvent(new CustomEvent("agro:operation-saved", { detail: { tipo: "custo", descricao: payload.description, talhao: talhao?.nome || "Propriedade" } }));
    closeCostModal();
    setBusy(false);
    await load();
  }

  function initForm() {
    const form = $("[data-cost-form]");
    // Uma correção válida remove o aviso antigo daquele campo, sem limpar os demais.
    ["input", "change"].forEach((type) => form.addEventListener(type, (event) => {
      const control = event.target;
      if (control.matches("input, select") && control.getAttribute("aria-invalid") === "true" && control.value.trim() && control.checkValidity()) setError(form, control.name, "");
    }));
    $("[data-open-cost-modal]").addEventListener("click", () => openCostModal());
    $$("[data-close-modal]", $("[data-modal='cost-form']")).forEach((button) => button.addEventListener("click", closeCostModal));
    $("[data-modal='cost-form']").addEventListener("click", (event) => { if (event.target === event.currentTarget) closeCostModal(); });
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      clearErrors(form);
      const description = form.elements.description.value.trim();
      const category = form.elements.category.value;
      const value = Number(form.elements.value.value);
      const date = form.elements.date.value;
      let invalid = false;
      if (!description) { setError(form, "description", "Informe uma descrição."); invalid = true; }
      if (!category) { setError(form, "category", "Selecione uma categoria."); invalid = true; }
      if (!Number.isFinite(value) || value <= 0) { setError(form, "value", "Informe um valor maior que zero."); invalid = true; }
      if (!date) { setError(form, "date", "Informe a data do lançamento."); invalid = true; }
      if (invalid) { toast("error", "Falha ao salvar", "Revise os campos destacados."); return; }
      saveCost(form, { description, category, value, date, talhaoId: form.elements.talhaoId.value || null, safraId: form.elements.safraId.value || null });
    });
  }

  function initBudgetModal() {
    const form = $("[data-budget-form]");
    $("[data-budget-edit]").addEventListener("click", () => {
      form.elements.budget.value = state.budget;
      $("[data-error-for='budget']", form).textContent = "";
      setModalState("budget-form", true);
      form.elements.budget.focus();
    });
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      const budget = Number(form.elements.budget.value);
      const error = $("[data-error-for='budget']", form);
      if (!Number.isFinite(budget) || budget <= 0) {
        form.elements.budget.setAttribute("aria-invalid", "true");
        error.textContent = "Informe um valor maior que zero.";
        toast("error", "Orçamento inválido", "Revise o valor informado.");
        return;
      }
      form.elements.budget.removeAttribute("aria-invalid");
      state.budget = budget;
      if (!persist()) return;
      renderMetrics();
      setModalState("budget-form", false);
      toast("success", "Orçamento atualizado", "O acompanhamento foi recalculado sobre o recorte atual.");
    });
  }

  function initPeriodModal() {
    const modal = $("[data-modal='period-form']");
    const options = $("[data-period-options]");
    const custom = $("[data-period-custom]");
    const from = $("[data-period-from]");
    const to = $("[data-period-to]");
    options.innerHTML = PERIODS.map((item) => `<button class="period-option" type="button" data-period-option="${item.key}" role="option" aria-selected="false"><span><strong>${item.label}</strong><small>${item.hint}</small></span><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg></button>`).join("");
    const select = (key) => {
      $$("[data-period-option]", modal).forEach((item) => {
        const selected = item.dataset.periodOption === key;
        item.classList.toggle("is-selected", selected);
        item.setAttribute("aria-selected", String(selected));
      });
      custom.hidden = key !== "personalizado";
    };
    const errors = (de, ate) => {
      $("[data-error-for='de']", modal).textContent = de;
      $("[data-error-for='ate']", modal).textContent = ate;
    };
    $("[data-open-period-modal]").addEventListener("click", () => {
      select(state.period);
      from.value = state.from;
      to.value = state.to;
      errors("", "");
      setModalState("period-form", true);
    });
    options.addEventListener("click", (event) => {
      const option = event.target.closest("[data-period-option]");
      if (option) select(option.dataset.periodOption);
    });
    $("[data-confirm-period]", modal).addEventListener("click", () => {
      const key = $("[data-period-option].is-selected", modal)?.dataset.periodOption;
      if (!key) return;
      if (key === "personalizado") {
        if (!from.value && !to.value) { errors("Informe ao menos uma das datas.", ""); return; }
        if (from.value && to.value && to.value < from.value) { errors("", "A data final deve ser igual ou posterior à inicial."); return; }
      }
      const previous = periodToken();
      state.period = key;
      state.from = key === "personalizado" ? from.value : "";
      state.to = key === "personalizado" ? to.value : "";
      if (!persist()) { readPeriod(previous); return; }
      setModalState("period-form", false);
      toast("success", "Período atualizado", `Exibindo ${periodLabel().toLowerCase()}.`);
      load();
    });
  }

  function initFilters() {
    const search = $("[data-cost-search]");
    search.addEventListener("input", () => {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => { state.search = search.value.trim(); load(); }, 300);
    });
    $$("[data-cost-filter]").forEach((button) => button.addEventListener("click", () => {
      $$("[data-cost-filter]").forEach((item) => item.classList.toggle("is-active", item === button));
      state.filter = button.dataset.costFilter;
      load();
    }));
    $("[data-cost-filter-plot]").addEventListener("change", (event) => {
      if (event.target.value === state.talhaoId) return;
      state.talhaoId = event.target.value;
      load();
    });
    $("[data-cost-filter-season]").addEventListener("change", (event) => {
      if (event.target.value === state.safraId) return;
      state.safraId = event.target.value;
      load();
    });
    $("[data-cost-clear-filters]").addEventListener("click", () => {
      state.filter = "Todos";
      state.search = "";
      state.talhaoId = "";
      state.safraId = "";
      readPeriod("todo");
      persist();
      search.value = "";
      $$("[data-cost-filter]").forEach((item) => item.classList.toggle("is-active", item.dataset.costFilter === "Todos"));
      $$("[data-cost-filter-plot], [data-cost-filter-season]").forEach((select) => { select.value = ""; select.dispatchEvent(new Event("change")); });
      load();
    });
  }

  function initActions() {
    let pendingDelete = null;
    $("[data-cost-table]").addEventListener("click", (event) => {
      const edit = event.target.closest("[data-edit-cost]");
      const remove = event.target.closest("[data-delete-cost]");
      if (edit) {
        const entry = state.entries.find((item) => item.id === edit.dataset.editCost);
        if (entry) openCostModal(entry);
      }
      if (remove && !busy) {
        const entry = state.entries.find((item) => item.id === remove.dataset.deleteCost);
        if (!entry) return;
        pendingDelete = entry;
        $("[data-delete-cost-desc]").textContent =
          `"${entry.description}" será removido no servidor e sairá da lista e dos indicadores.`;
        setModalState("cost-delete", true);
      }
    });
    $("[data-confirm-delete-cost]").addEventListener("click", async () => {
      const entry = pendingDelete;
      if (!entry || busy) return;
      setBusy(true);
      try {
        await request(`/api/custos/${encodeURIComponent(entry.id)}`, { method: "DELETE" });
        toast("info", "Lançamento excluído", `${entry.description} foi removido no servidor.`);
        pendingDelete = null;
        setModalState("cost-delete", false);
      } catch (error) {
        toast("error", "Exclusão não confirmada", error.message);
      }
      setBusy(false);
      await load();
    });
    // O CSV é exatamente o recorte exibido: mesma consulta da lista e dos indicadores.
    $("[data-export-costs]").addEventListener("click", () => {
      if (!state.summary) { toast("error", "Nada para exportar", "A consulta atual não foi confirmada pelo servidor."); return; }
      const { de, ate } = periodRange();
      const rows = [["Descrição", "Categoria", "Talhão", "Safra", "Cultura", "Data", "Valor", "Origem"],
        ...state.entries.map((entry) => [entry.description, entry.category, entry.talhaoNome || "Propriedade", entry.safraNome || "", entry.cultura || "", entry.date, entry.value.toFixed(2), entry.origem ? "Consumo de estoque" : "Lançamento manual"])];
      const csv = rows.map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(";")).join("\n");
      const link = document.createElement("a");
      link.href = URL.createObjectURL(new Blob([String.fromCharCode(0xFEFF) + csv], { type: "text/csv;charset=utf-8" }));
      link.download = `agrogestao-custos${de ? `-${de}` : ""}${ate ? `-a-${ate}` : ""}.csv`;
      link.click();
      URL.revokeObjectURL(link.href);
      toast("success", "Exportação concluída", `${state.entries.length} lançamento${state.entries.length === 1 ? "" : "s"} do recorte atual.`);
    });
  }

  function initModalDismissals() {
    ["budget-form", "period-form", "cost-delete"].forEach((name) => {
      const modal = $(`[data-modal="${name}"]`);
      $$("[data-close-modal]", modal).forEach((button) => button.addEventListener("click", () => setModalState(name, false)));
      modal.addEventListener("click", (event) => {
        if (event.target === modal) setModalState(name, false);
      });
    });
  }

  // Sem talhões e safras confirmados não há como montar filtros nem o formulário.
  async function start() {
    message("Carregando vínculos da propriedade…");
    try {
      await loadReferences();
    } catch (error) {
      message(`${error.message} Não foi possível carregar talhões e safras.`, true);
      return;
    }
    await load();
  }

  function init() {
    const initial = window.AgroLocal.initial("costs");
    persist = window.AgroLocal.connect("costs", () => ({ budget: state.budget, period: periodToken(), entries: [] }),
      (records) => { state.budget = records.budget; readPeriod(records.period); },
      initial, { budget: initial.budget, period: "todo", entries: [] });
    initDatePicker();
    initForm();
    initBudgetModal();
    initPeriodModal();
    initModalDismissals();
    initFilters();
    initActions();
    $("[data-cost-retry]").addEventListener("click", () => (state.talhoes.length || state.safras.length ? load() : start()));
    renderAll();
    start();
  }

  document.addEventListener("DOMContentLoaded", init);
})();
