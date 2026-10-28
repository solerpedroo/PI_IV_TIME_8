/* Miguel — RA: 25003110 */
/* Autor Secundário: Pedro Henrique Contardi Soler - 25005592 */
/* Ocorrências: a API é a fonte dos dados. Nenhum registro mock é importado. */
(() => {
  "use strict";
  const $ = (s, root = document) => root.querySelector(s);
  const $$ = (s, root = document) => [...root.querySelectorAll(s)];
  const base = location.port === "8080" ? location.origin : "http://127.0.0.1:8080";
  const labels = { ABERTA: "Aberta", EM_ACOMPANHAMENTO: "Em acompanhamento", RESOLVIDA: "Resolvida" };
  const titles = { registro: "Ocorrência registrada", editar: "Cadastro editado", acompanhamento: "Acompanhamento iniciado", atualizacao: "Atualização publicada", resolucao: "Ocorrência resolvida", excluir: "Ocorrência excluída" };
  let propertyId = "", active = null, editing = null, busy = false, ready = false, loadNumber = 0;
  const form = $("[data-occ-form]");
  const field = (name) => form.elements.namedItem(name);
  const toast = (type, title, description) => window.AgroToast?.show({ type, title, description });

  // Textos do usuário entram como conteúdo, inclusive descrição, títulos e timeline.
  function node(tag, text, className) {
    const el = document.createElement(tag);
    if (text != null) el.textContent = text;
    if (className) el.className = className;
    return el;
  }
  const date = (value) => value ? value.split("-").reverse().join("/") : "—";
  const timestamp = (value) => value ? new Date(value).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo" }) : "—";
  function today() {
    const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(new Date());
    return ["year", "month", "day"].map((key) => parts.find((p) => p.type === key).value).join("-");
  }

  async function request(path, options = {}) {
    const token = sessionStorage.getItem("agro.sessionToken");
    if (!token) { location.replace("login.html"); throw new Error("Entre novamente."); }
    const response = await fetch(base + path, { ...options, headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" }, signal: AbortSignal.timeout(15000) });
    const data = await response.json();
    if (response.status === 401) { sessionStorage.removeItem("agro.sessionToken"); location.replace("login.html"); }
    if (!response.ok) {
      const error = new Error(data.error === "Validation failed" ? Object.values(data.fields || {}).join(" ") || "Revise os campos informados." : data.error || "Falha na API.");
      error.fields = data.fields; throw error;
    }
    return data;
  }

  function openModal(name) {
    $$("[data-modal^='occ-']").forEach((modal) => {
      const open = modal.dataset.modal === name;
      modal.classList.toggle("is-open", open); modal.setAttribute("aria-hidden", String(!open));
      if (open) modal.querySelector("input, select, textarea, button")?.focus();
    });
  }
  const closeModals = () => openModal("");
  function setBusy(value) {
    busy = value;
    $$("[data-modal^='occ-'] button, [data-open-modal='occ-form']").forEach((b) => { b.disabled = value || !ready; });
  }
  function message(text, retry = false) { $("[data-occ-message]").textContent = text; $("[data-occ-retry]").hidden = !retry; }
  function clearList() {
    $("[data-occ-table] tbody").replaceChildren(); $("[data-occ-empty]").hidden = true;
    $$("[data-metric]").forEach((e) => { e.textContent = "—"; });
  }
  function parameters() {
    return "?" + new URLSearchParams({ mes: $("[data-occ-month]").value, q: $("[data-occ-search]").value.trim(), talhaoId: $("[data-occ-plot-filter]").value, incluirExcluidas: $("[data-occ-archives]").checked });
  }

  // O resultado de um filtro antigo não pode substituir uma consulta mais recente.
  async function load() {
    const number = ++loadNumber; clearList(); message("Carregando ocorrências…");
    try {
      const params = parameters();
      const [records, summary] = await Promise.all([request("/api/ocorrencias" + params), request("/api/ocorrencias/resumo" + params)]);
      if (number !== loadNumber) return;
      $("[data-occ-table] tbody").replaceChildren(...records.map(row)); $("[data-occ-empty]").hidden = records.length > 0;
      const values = { abertas: summary.abertas, "em-analise": summary.emAcompanhamento, resolvidas: summary.resolvidasMes, "severidade-alta": summary.prioridadeAlta };
      Object.entries(values).forEach(([key, value]) => { $(`[data-metric='${key}']`).textContent = value; });
      $("[data-occ-month-label]").textContent = `Resoluções em ${summary.mes.split("-").reverse().join("/")} · Brasília`;
      message(`${records.length} ocorrência(s) no contexto consultado.`);
    } catch (error) {
      if (number !== loadNumber) return;
      clearList(); message("Não foi possível consultar as ocorrências. " + error.message, true); throw error;
    }
  }
  function badge(text, kind) {
    const tone = kind === "status" ? ({ ABERTA: "warning", EM_ACOMPANHAMENTO: "info", RESOLVIDA: "success" }[text]) : ({ Alta: "error", Média: "warning", Baixa: "neutral" }[text]);
    return node("span", labels[text] || text, "badge badge-" + tone);
  }
  const reportDetail = (error) => toast("error", "Consulta não concluída", error.message);
  function row(record) {
    const tr = node("tr"); tr.dataset.id = record.id; tr.dataset.occ = record.titulo; tr.dataset.status = labels[record.status]; tr.dataset.talhao = record.talhaoNome || "Propriedade";
    const name = node("td", null, "col-occ-name"), details = node("button", record.titulo, "occ-detail-link");
    details.type = "button"; details.dataset.rowAction = "detail";
    details.addEventListener("click", () => showDetail(record.id).catch(reportDetail)); name.append(details);
    tr.append(name, node("td", record.tipo, "col-muted"), node("td", record.talhaoNome || "Geral da propriedade", "col-muted"), node("td", date(record.data), "col-muted"));
    const priority = node("td"), status = node("td"); priority.append(badge(record.prioridade)); status.append(badge(record.status, "status"));
    if (record.excluida) status.append(node("span", "Excluída", "badge badge-neutral"));
    tr.append(priority, status, node("td", "Abrir pelo título", "col-muted")); return tr;
  }

  async function showDetail(id) {
    const [record, events] = await Promise.all([request("/api/ocorrencias/" + id), request("/api/ocorrencias/" + id + "/timeline")]);
    active = record;
    $("[data-detail-title]").textContent = record.titulo; $("[data-detail-subtitle]").textContent = `${record.talhaoNome || "Geral da propriedade"} · ${date(record.data)}`;
    $("[data-detail-description]").textContent = record.descricao || "Sem descrição informada.";
    $("[data-detail-badges]").replaceChildren(badge(record.prioridade), badge(record.status, "status"));
    const metadata = { Tipo: record.tipo, Propriedade: $(".sidebar-user-property").textContent, Talhão: record.talhaoNome || "Geral da propriedade", Safra: record.safra || "Sem vínculo", "Data da ocorrência": date(record.data), "Registrada em": timestamp(record.criadaEm), "Resolvida em": timestamp(record.resolvidaEm), Status: record.excluida ? "Excluída (consulta)" : labels[record.status] };
    $("[data-detail-meta]").replaceChildren(...Object.entries(metadata).map(([key, value]) => {
      const cell = node("div", null, "meta-cell"); cell.append(node("span", key, "meta-cell-label"), node("span", value, "meta-cell-value")); return cell;
    }));
    $("[data-detail-timeline]").replaceChildren(...events.map((event) => {
      const entry = node("div", null, "timeline-entry"), rail = node("div", null, "timeline-rail"), body = node("div", null, "timeline-body");
      rail.append(node("span", null, "timeline-dot"), node("span", null, "timeline-line"));
      body.append(node("span", `${event.ordem} · ${timestamp(event.data)}`, "timeline-time"), node("p", titles[event.acao], "timeline-title"), node("p", event.descricao || "Sem descrição informada.", "timeline-desc occ-text"));
      if (event.acao === "editar") {
        const d = event.dados;
        body.append(node("p", `${d.titulo} · ${d.tipo} · ${date(d.data)} · ${d.prioridade} · ${d.talhaoNome || "Geral da propriedade"} · ${d.safra || "Sem safra"}`, "timeline-desc"), node("p", d.descricao, "occ-text"));
      }
      entry.append(rail, body); return entry;
    }));
    const readonly = record.excluida || record.status === "RESOLVIDA";
    $("[data-detail-follow]").hidden = readonly || record.status !== "ABERTA";
    $("[data-detail-publish]").hidden = readonly || record.status !== "EM_ACOMPANHAMENTO";
    for (const key of ["resolve", "edit"]) $(`[data-detail-${key}]`).hidden = readonly;
    $("[data-detail-delete]").hidden = record.excluida;
    $("[data-update-text]").closest(".textarea-group").hidden = readonly;
    $("[data-occ-detail-error]").textContent = ""; openModal("occ-detail");
  }
  function priority(value) {
    field("prioridade").value = value;
    $$("[data-priority]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.priority === value)));
  }
  function edit(record) {
    editing = record; form.reset(); $$(".field-error", form).forEach((el) => { el.textContent = ""; });
    $$("[aria-invalid]", form).forEach((el) => el.removeAttribute("aria-invalid"));
    for (const key of ["titulo", "tipo", "talhaoId", "data", "descricao"]) field(key).value = record?.[key] || "";
    if (!record) { field("data").value = today(); field("talhaoId").value = $("[data-occ-plot-filter]").value; }
    priority(record?.prioridade || "Baixa"); $("#occ-form-title").textContent = record ? "Editar ocorrência" : "Registrar ocorrência";
    $("button[type=submit]", form).textContent = record ? "Salvar alterações" : "Registrar ocorrência"; openModal("occ-form");
  }

  // Sucesso depende da confirmação da escrita. Uma falha posterior de consulta é
  // informada separadamente para não incentivar a duplicação de um registro já salvo.
  async function write(path, method, payload, errorElement, onSaved) {
    if (busy) return;
    errorElement.textContent = ""; setBusy(true);
    let result;
    try { result = await request(path, { method, body: JSON.stringify({ ...payload, contextoPropriedadeId: propertyId }) }); }
    catch (error) {
      errorElement.textContent = error.message + " Consulte o registro antes de repetir uma operação sem confirmação.";
      if (error.fields) Object.entries(error.fields).forEach(([key, text]) => {
        const host = $(`[data-error-for='${key}']`, form); if (host) host.textContent = text; field(key)?.setAttribute("aria-invalid", "true");
      });
      setBusy(false); return;
    }
    toast("success", "Alteração salva", "Registro e histórico confirmados pelo servidor.");
    try { await onSaved(result); await load(); }
    catch (error) { toast("error", "Alteração salva; consulta pendente", "A gravação foi confirmada. Atualize a consulta. " + error.message); }
    finally { setBusy(false); }
  }
  function publish(action) {
    if (!active) return;
    return write("/api/ocorrencias/" + active.id + "/timeline", "POST", { versao: active.versao, acao: action, descricao: $("[data-update-text]").value }, $("[data-occ-detail-error]"), async (saved) => {
      active = saved; $("[data-update-text]").value = ""; await showDetail(saved.id);
    });
  }

  async function initialize() {
    clearList();
    try {
      const [me, plots] = await Promise.all([request("/api/me"), request("/api/talhoes")]); propertyId = me.usuario.propriedadeId;
      const property = me.propriedade?.nome || me.propriedade?.farm || "Propriedade ativa";
      $("[data-occ-property]").textContent = "Registro de eventos e problemas · " + property;
      $(".sidebar-user-name").textContent = me.usuario.name || me.usuario.nome || "Usuário"; $(".sidebar-user-property").textContent = property;
      $(".sidebar-avatar").textContent = (me.usuario.name || me.usuario.nome || "U").split(/\s+/).slice(0, 2).map((s) => s[0]).join("");
      field("talhaoId").replaceChildren(new Option("Geral da propriedade", ""), ...plots.map((p) => new Option(p.nome, p.id)));
      $("[data-occ-plot-filter]").replaceChildren(new Option("Toda a propriedade", ""), ...plots.map((p) => new Option(p.nome, p.id)));
      const params = new URLSearchParams(location.search), requested = params.get("talhaoId") || params.get("talhao");
      if (requested) {
        $("[data-talhao-filter-bar]").hidden = false; $("[data-talhao-filter-label]").textContent = "Talhão: " + requested;
        const plot = plots.find((p) => p.id === requested || p.nome === requested);
        if (!plot) throw new Error("O talhão solicitado não pertence à propriedade ativa. Remova o filtro para consultar.");
        $("[data-occ-plot-filter]").value = plot.id; $("[data-talhao-filter-label]").textContent = "Talhão: " + plot.nome;
      }
      ready = true; setBusy(false); await load();
      if (params.get("ocorrenciaId")) await showDetail(params.get("ocorrenciaId")).catch(reportDetail);
    } catch (error) { clearList(); message(error.message, true); }
  }

  // Eventos da tela: fechar uma confirmação nunca modifica o registro.
  function init() {
    $("[data-occ-month]").value = today().slice(0, 7);
    $$("[data-modal^='occ-']").forEach((modal) => {
      $$("[data-close-modal]", modal).forEach((b) => b.addEventListener("click", () => { if (!busy) closeModals(); }));
      modal.addEventListener("click", (e) => { if (e.target === modal && !busy) closeModals(); });
    });
    document.addEventListener("keydown", (e) => { if (e.key === "Escape" && !busy) closeModals(); });
    $("[data-open-modal='occ-form']").addEventListener("click", () => edit(null));
    $$("[data-priority]").forEach((b) => b.addEventListener("click", () => priority(b.dataset.priority)));
    form.addEventListener("submit", (e) => {
      e.preventDefault(); $$(".field-error", form).forEach((el) => { el.textContent = ""; });
      const payload = Object.fromEntries(new FormData(form)); if (editing) payload.versao = editing.versao;
      write("/api/ocorrencias" + (editing ? "/" + editing.id : ""), editing ? "PUT" : "POST", payload, $("[data-occ-form-error]"), async (saved) => { closeModals(); await showDetail(saved.id); });
    });
    $("[data-detail-edit]").addEventListener("click", () => edit(active));
    $("[data-detail-follow]").addEventListener("click", () => publish("acompanhamento"));
    $("[data-detail-publish]").addEventListener("click", () => publish("atualizacao"));
    $("[data-detail-reload]").addEventListener("click", () => showDetail(active.id).catch(reportDetail));
    $("[data-detail-resolve]").addEventListener("click", () => {
      $("[data-resolve-desc]").textContent = active.titulo + " será marcada como resolvida. O histórico será preservado.";
      $("[data-resolution-text]").value = ""; $("[data-resolve-error]").textContent = ""; openModal("occ-resolve");
    });
    $("[data-confirm-resolve]").addEventListener("click", () => write("/api/ocorrencias/" + active.id + "/timeline", "POST", { versao: active.versao, acao: "resolucao", descricao: $("[data-resolution-text]").value }, $("[data-resolve-error]"), async (saved) => { active = saved; closeModals(); await showDetail(saved.id); }));
    $("[data-detail-delete]").addEventListener("click", () => { $("[data-delete-error]").textContent = ""; openModal("occ-delete"); });
    $("[data-confirm-delete]").addEventListener("click", () => write("/api/ocorrencias/" + active.id, "DELETE", { versao: active.versao }, $("[data-delete-error]"), async () => { closeModals(); active = null; }));
    let timer;
    $("[data-occ-search]").addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(() => load().catch(() => {}), 200); });
    $$("[data-occ-month], [data-occ-plot-filter], [data-occ-archives]").forEach((el) => el.addEventListener("change", () => load().catch(() => {})));
    $("[data-clear-talhao-filter]").addEventListener("click", () => {
      const url = new URL(location.href); url.searchParams.delete("talhao"); url.searchParams.delete("talhaoId"); history.replaceState({}, "", url);
      $("[data-talhao-filter-bar]").hidden = true; $("[data-occ-plot-filter]").value = ""; if (!ready) initialize(); else load().catch(() => {});
    });
    $("[data-occ-retry]").addEventListener("click", () => ready ? load().catch(() => {}) : initialize()); initialize();
  }
  document.addEventListener("DOMContentLoaded", init);
})();
