/* Miguel — RA: 25003110 */
// Tela e CSV compartilham o mesmo resultado consultado, incluindo pendências.
(() => {
  "use strict";
  const modal = document.querySelector('[data-modal="safra-report"]');
  const content = modal.querySelector('[data-report-content]');
  const message = modal.querySelector('[data-report-message]');
  const exportButton = modal.querySelector('[data-export-report]');
  const base = location.port === "8080" ? location.origin : "http://127.0.0.1:8080";
  const money = cents => new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(cents / 100);
  let snapshot = null, generation = 0, loadedContext = "", opener = null;
  const { escape, context } = window.AgroRelatorio;
  function url(id) {
    const target = new URL(location.href);
    if (id) target.searchParams.set("relatorio", id);
    else target.searchParams.delete("relatorio");
    history.replaceState({}, "", target);
  }
  function clear(text) {
    snapshot = null;
    content.replaceChildren();
    exportButton.disabled = true;
    message.textContent = text;
  }
  function close() {
    generation++;
    clear("");
    modal.classList.remove("is-open");
    modal.setAttribute("aria-hidden", "true");
    url("");
    opener?.focus();
  }
  function rows(items, pending = false) {
    if (!items.length) return `<p>${pending ? "Nenhuma pendência." : "Nenhum registro no período."}</p>`;
    return items.map(item => `<article class="meta-cell" data-report-item="${escape(item.tipo)}"><strong>${escape(item.tipo)} · ${escape(item.descricao)}</strong><p>${escape(item.id)} · ${escape(item.data || "Sem data completa")} · ${escape(item.talhao || "Sem talhão")} · ${escape(item.status || "")}</p>${item.valorCentavos != null ? `<p>${escape(item.categoria)} · ${escape(money(item.valorCentavos))}</p>` : ""}${pending ? `<p>${escape(item.motivo)}</p>` : ""}</article>`).join("");
  }
  function render(report) {
    content.innerHTML = `<section class="history-section"><h3>${escape(report.safra.nome)}</h3><p>${escape(report.propriedade.nome)} · ${escape(report.safra.cultura)} · ${escape(report.safra.etapa)}</p><p>Período inclusivo: ${escape(report.periodo.inicio)} a ${escape(report.periodo.fim)}</p><p>${escape(report.origemArea)}: ${escape(report.areaHectares)}</p><p>${report.talhoes.map(item => `${escape(item.nome)} (${escape(item.hectares)} ha)`).join(" · ")}</p><p>${escape(report.regraOcorrencias)}</p><p>Atividades: ${report.contagens.atividade} · Ocorrências: ${report.contagens.ocorrencia} · Custos: ${report.contagens.custo}</p><p>Total de custos: <strong data-report-total>${escape(money(report.totalCentavos))}</strong></p><h3>Registros no período</h3>${rows(report.registros)}<h3>Pendências (${report.pendencias.length})</h3>${rows(report.pendencias, true)}</section>`;
  }
  async function open(id, trigger) {
    const requestGeneration = ++generation;
    loadedContext = context();
    const requestContext = loadedContext;
    opener = trigger || document.querySelector(`[data-report="${CSS.escape(id)}"]`);
    clear("Consultando relatório…");
    modal.classList.add("is-open");
    modal.setAttribute("aria-hidden", "false");
    modal.querySelector('[data-close-report]').focus();
    url(id);
    try {
      const response = await fetch(`${base}/api/relatorios/safras/${encodeURIComponent(id)}`, {
        headers: { Authorization: "Bearer " + sessionStorage.getItem("agro.sessionToken") }, cache: "no-store"
      });
      const report = await response.json();
      if (requestGeneration !== generation) return;
      if (requestContext !== context()) throw new Error("A sessão ou propriedade mudou. Consulte novamente.");
      if (!response.ok) throw new Error(report.error || "Não foi possível consultar o relatório.");
      const user = JSON.parse(sessionStorage.getItem("agro.sessionUser") || "null");
      if (report.propriedade.id !== user?.propriedadeId || report.safra.id !== id) throw new Error("O contexto da consulta mudou. Recarregue a página.");
      snapshot = report;
      render(report);
      message.textContent = "Consulta concluída. O CSV corresponde a este resultado.";
      exportButton.disabled = false;
    } catch (error) {
      if (requestGeneration === generation) clear(error.message || "Relatório indisponível. Tente novamente.");
    }
  }

  function csv(report) {
    const rows = [
      ["Propriedade", report.propriedade.nome, report.propriedade.id],
      ["Safra", report.safra.nome, report.safra.id], ["Cultura", report.safra.cultura], ["Etapa", report.safra.etapa],
      ["Período inclusivo", report.periodo.inicio, report.periodo.fim], [report.origemArea, report.areaHectares],
      ["Regra das ocorrências", report.regraOcorrencias],
      ...report.talhoes.map(item => ["Talhão", item.id, item.nome, item.hectares, "ha"]),
      ["Atividades", report.contagens.atividade], ["Ocorrências", report.contagens.ocorrencia], ["Custos", report.contagens.custo],
      ["Total de custos (BRL)", (report.totalCentavos / 100).toFixed(2)],
      ["Conjunto", "Tipo", "ID", "Descrição", "Data", "Talhão", "Status", "Categoria", "Valor (BRL)", "Motivo"]
    ];
    for (const [label, items] of [["Registro", report.registros], ["Pendência", report.pendencias]]) {
      for (const item of items) rows.push([label, item.tipo, item.id, item.descricao, item.data, item.talhao, item.status,
        item.categoria, item.valorCentavos == null ? "" : (item.valorCentavos / 100).toFixed(2), item.motivo]);
    }
    return rows;
  }
  exportButton.addEventListener("click", () => {
    if (loadedContext !== context()) { clear("A sessão ou propriedade mudou. Consulte novamente."); return; }
    if (!snapshot) return;
    window.AgroRelatorio.download(csv(snapshot), `agrogestao-safra-${snapshot.safra.id}.csv`);
  });
  document.addEventListener("click", event => {
    const trigger = event.target.closest("[data-report]");
    if (trigger) open(trigger.dataset.report, trigger);
    if (event.target.closest("[data-close-report]") || event.target === modal) close();
  });
  // O foco fica no diálogo aberto; Escape também permite sair durante carregamento.
  modal.addEventListener("keydown", event => {
    if (event.key === "Escape") { event.preventDefault(); event.stopPropagation(); close(); }
    if (event.key === "Tab") {
      const buttons = [...modal.querySelectorAll("button:not(:disabled)")];
      const first = buttons[0], last = buttons.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
  });
  window.addEventListener("focus", () => {
    if (snapshot && loadedContext !== context()) clear("A sessão ou propriedade mudou. Consulte novamente.");
  });
  const initial = new URL(location.href).searchParams.get("relatorio");
  if (initial) open(initial);
})();
