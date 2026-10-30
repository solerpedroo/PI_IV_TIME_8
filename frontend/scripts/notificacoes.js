/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */
/**
 * Agro — alertas operacionais do sino (#26 / #166–#168)
 * --------------------------------------------------------------------------
 * Gera notificações a partir de registros reais (atraso, estoque, ocorrência
 * e atividades do dia), navega ao registro ao clicar, persiste leitura no
 * AgroLocal e respeita preferências gravadas em /api/perfil/preferencias.
 */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

  const API_BASE = window.location.port === "8080"
    ? window.location.origin
    : "http://127.0.0.1:8080";

  const MESES = {
    jan: 0, fev: 1, mar: 2, abr: 3, mai: 4, jun: 5,
    jul: 6, ago: 7, set: 8, out: 9, nov: 10, dez: 11,
  };
  const STATUS_ATIVOS = new Set(["Pendente", "Agendada", "Em andamento"]);
  // Limite visual do painel: evita listas gigantes no browser (MVP sem resumo no servidor).
  const LIMITE_ALERTAS = 40;

  const ICONS = {
    error: '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
    warning: '<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>',
    info: '<path d="M8 2v4"/><path d="M16 2v4"/><rect width="18" height="18" x="3" y="4" rx="2"/><path d="M3 10h18"/>',
  };

  let estado = { alerts: [], readIds: [], carregado: false };

  function token() {
    return sessionStorage.getItem("agro.sessionToken");
  }

  async function api(path) {
    const credencial = token();
    if (!credencial) throw new Error("Sessão ausente");
    const response = await fetch(API_BASE + path, {
      headers: { Authorization: "Bearer " + credencial },
      signal: AbortSignal.timeout(15000),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || body.erro || "Consulta indisponível");
    }
    return response.json();
  }

  // Rótulos sem ano (ex.: "12 jan") usam o ano corrente; se caírem no futuro,
  // assumem o ano anterior — evita sumir atraso de janeiro quando estamos em out–dez.
  function parseDataPlanejamento(valor, referencia = new Date()) {
    const texto = String(valor || "").trim();
    if (/^\d{4}-\d{2}-\d{2}$/.test(texto)) {
      const parsed = new Date(texto + "T12:00:00");
      return Number.isFinite(parsed.getTime()) ? parsed : null;
    }
    const match = texto.match(/^(\d{1,2})\s+(jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)$/i);
    if (!match) return null;
    const dia = Number(match[1]);
    const mes = MESES[match[2].toLowerCase()];
    const ano = referencia.getFullYear();
    let data = new Date(ano, mes, dia, 12);
    if (!Number.isFinite(data.getTime())) return null;
    const hoje = inicioDoDia(referencia);
    if (inicioDoDia(data).getTime() > hoje.getTime()) {
      data = new Date(ano - 1, mes, dia, 12);
    }
    return Number.isFinite(data.getTime()) ? data : null;
  }

  function inicioDoDia(date = new Date()) {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate());
  }

  function isoDia(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, "0");
    const d = String(date.getDate()).padStart(2, "0");
    return `${y}-${m}-${d}`;
  }

  function preferenciasPadrao() {
    return { stock: true, weather: true, activities: true, occurrences: true, reports: false };
  }

  function lerLidas() {
    const gravadas = window.AgroLocal?.read?.("notifications", []);
    return Array.isArray(gravadas) ? gravadas.filter((item) => typeof item === "string") : [];
  }

  function gravarLidas(ids) {
    if (!window.AgroLocal?.save) return false;
    return window.AgroLocal.save("notifications", [...new Set(ids)]);
  }

  function montarAlertas({ atividades, insumos, ocorrencias, prefs }) {
    const alertas = [];
    const hoje = inicioDoDia();
    const idHoje = `hoje:${isoDia(hoje)}`;

    if (prefs.stock) {
      for (const item of insumos) {
        const critico = item.status === "Crítico";
        const baixo = critico || item.status === "Baixo" || Number(item.saldo) < Number(item.minimo);
        if (!baixo) continue;
        alertas.push({
          id: `stock:${item.id}`,
          kind: "stock",
          tone: critico ? "error" : "warning",
          title: `Estoque de ${item.nome} abaixo do mínimo`,
          desc: `Restam ${item.saldo} ${item.unidade} · mínimo ${item.minimo}`,
          href: `insumos.html?insumoId=${encodeURIComponent(item.id)}`,
        });
      }
    }

    if (prefs.activities) {
      const atrasadas = [];
      const doDia = [];
      for (const atividade of atividades) {
        if (!STATUS_ATIVOS.has(atividade.status)) continue;
        const data = parseDataPlanejamento(atividade.data);
        if (!data) continue;
        const dia = inicioDoDia(data);
        if (dia.getTime() < hoje.getTime()) {
          atrasadas.push({ atividade, dias: Math.max(1, Math.round((hoje - dia) / 86400000)) });
        } else if (dia.getTime() === hoje.getTime()) {
          doDia.push(atividade);
        }
      }

      for (const { atividade, dias } of atrasadas) {
        alertas.push({
          id: `atraso:${atividade.id}`,
          kind: "atraso",
          tone: "warning",
          title: `${atividade.nome} atrasada`,
          desc: `${atividade.talhao || "Talhão"} · ${dias} dia(s) de atraso`,
          href: `atividades.html?atividadeId=${encodeURIComponent(atividade.id)}`,
        });
      }

      if (doDia.length > 0) {
        const nomes = doDia.map((item) => item.nome).filter(Boolean);
        alertas.push({
          id: idHoje,
          kind: "hoje",
          tone: "info",
          title: doDia.length === 1 ? "1 atividade para hoje" : `${doDia.length} atividades para hoje`,
          desc: nomes.slice(0, 3).join(", ") + (nomes.length > 3 ? "…" : ""),
          href: "atividades.html?hoje=1",
        });
      }
    }

    if (prefs.occurrences) {
      for (const ocorrencia of ocorrencias) {
        if (ocorrencia.status === "RESOLVIDA") continue;
        const statusLabel = ocorrencia.status === "ABERTA" ? "Aberta" : "Em acompanhamento";
        alertas.push({
          id: `ocorrencia:${ocorrencia.id}`,
          kind: "ocorrencia",
          tone: "warning",
          title: ocorrencia.titulo || "Ocorrência em acompanhamento",
          desc: `${ocorrencia.talhaoNome || "Propriedade"} · ${statusLabel}`,
          href: `ocorrencias.html?ocorrenciaId=${encodeURIComponent(ocorrencia.id)}`,
        });
      }
    }

    return alertas.slice(0, LIMITE_ALERTAS);
  }

  function mensagemVazia(empty, texto) {
    if (!empty) return;
    empty.hidden = false;
    empty.textContent = texto;
  }

  function atualizarIndicadores(list, empty, dot) {
    const unread = $$(".notif-item.is-unread", list).length;
    if (dot) dot.classList.toggle("is-hidden", unread === 0);
    const hasItems = list.children.length > 0;
    list.hidden = !hasItems;
    if (!empty) return;
    if (hasItems) {
      empty.hidden = true;
      return;
    }
    mensagemVazia(empty, "Nenhuma notificação no momento.");
  }

  function setMarkAllEnabled(markAll, enabled) {
    if (!markAll) return;
    markAll.disabled = !enabled;
    markAll.setAttribute("aria-disabled", String(!enabled));
  }

  function renderLista(list, empty, dot, alertas, lidas, markAll) {
    list.replaceChildren();
    for (const alerta of alertas) {
      const item = document.createElement("button");
      item.type = "button";
      item.className = "notif-item" + (lidas.includes(alerta.id) ? "" : " is-unread");
      item.dataset.notifId = alerta.id;
      item.dataset.notifHref = alerta.href;
      item.dataset.notifKind = alerta.kind;

      const icon = document.createElement("span");
      icon.className = `notif-item-icon tone-${alerta.tone}`;
      icon.setAttribute("aria-hidden", "true");
      icon.innerHTML = `<svg viewBox="0 0 24 24">${ICONS[alerta.tone] || ICONS.info}</svg>`;

      const body = document.createElement("span");
      const title = document.createElement("span");
      title.className = "notif-item-title";
      title.textContent = alerta.title;
      const desc = document.createElement("span");
      desc.className = "notif-item-desc";
      desc.textContent = alerta.desc;
      body.append(title, desc);

      item.append(icon, body);
      list.appendChild(item);
    }
    estado = { alerts: alertas, readIds: lidas, carregado: true };
    setMarkAllEnabled(markAll, true);
    atualizarIndicadores(list, empty, dot);
  }

  async function carregarFontes() {
    const vazia = [];
    const [perfil, atividades, insumos, ocorrencias] = await Promise.all([
      // Sem preferências confirmadas, falhamos fechados: não é aceitável
      // reativar alertas que a conta já desabilitou por uma falha transitória.
      api("/api/perfil"),
      api("/api/atividades").catch(() => vazia),
      api("/api/insumos").catch(() => vazia),
      api("/api/ocorrencias").catch(() => vazia),
    ]);

    if (!perfil?.preferencias || typeof perfil.preferencias !== "object") {
      throw new Error("Preferências indisponíveis");
    }
    const prefs = { ...preferenciasPadrao(), ...(perfil?.preferencias || {}) };
    return {
      prefs,
      atividades: Array.isArray(atividades) ? atividades : vazia,
      insumos: Array.isArray(insumos) ? insumos : vazia,
      ocorrencias: Array.isArray(ocorrencias) ? ocorrencias : vazia,
    };
  }

  async function sincronizar(list, empty, dot, markAll) {
    if (!token()) {
      renderLista(list, empty, dot, [], lerLidas(), markAll);
      return;
    }
    const fontes = await carregarFontes();
    const alertas = montarAlertas(fontes);
    const idsAtivos = new Set(alertas.map((item) => item.id));
    // Mantém só leituras ainda relevantes (e o "hoje" do dia corrente, se existir).
    const lidas = lerLidas().filter((id) => idsAtivos.has(id));
    if (lidas.length !== lerLidas().length) gravarLidas(lidas);
    renderLista(list, empty, dot, alertas, lidas, markAll);
  }

  function fecharPainel() {
    window.AgroPopover?.closeAll?.();
  }

  function init() {
    const list = $("[data-notif-list]");
    const empty = $("[data-notif-empty]");
    const markAll = $("[data-notif-mark-all]");
    const dot = $("[data-notif-dot]");
    if (!list) return;

    list.replaceChildren();
    estado = { alerts: [], readIds: [], carregado: false };
    setMarkAllEnabled(markAll, false);
    mensagemVazia(empty, "Carregando alertas…");
    list.hidden = true;
    if (dot) dot.classList.add("is-hidden");

    list.addEventListener("click", (event) => {
      const item = event.target.closest(".notif-item");
      if (!item) return;

      const id = item.dataset.notifId;
      const href = item.dataset.notifHref;
      if (!id) return;

      const next = [...new Set([...lerLidas(), id])];
      if (!gravarLidas(next)) return;
      item.classList.remove("is-unread");
      estado.readIds = next;
      atualizarIndicadores(list, empty, dot);
      fecharPainel();

      if (href) {
        window.location.href = href;
        return;
      }

      if (window.AgroToast) {
        window.AgroToast.show({
          type: "info",
          title: "Notificação aberta",
          description: "Registro relacionado indisponível neste alerta.",
        });
      }
    });

    markAll?.addEventListener("click", () => {
      if (!estado.carregado) return;
      const ids = estado.alerts.map((item) => item.id);
      if (ids.length === 0) {
        if (window.AgroToast) {
          window.AgroToast.show({
            type: "info",
            title: "Nada para marcar",
            description: "Não há notificações neste momento.",
          });
        }
        return;
      }
      const next = [...new Set([...lerLidas(), ...ids])];
      if (!gravarLidas(next)) return;
      estado.readIds = next;
      $$(".notif-item.is-unread", list).forEach((item) => item.classList.remove("is-unread"));
      atualizarIndicadores(list, empty, dot);
      if (window.AgroToast) {
        window.AgroToast.show({
          type: "success",
          title: "Tudo lido",
          description: "Todas as notificações foram marcadas como lidas.",
        });
      }
    });

    sincronizar(list, empty, dot, markAll).catch(() => {
      estado.carregado = true;
      setMarkAllEnabled(markAll, false);
      mensagemVazia(empty, "Não foi possível carregar os alertas.");
      list.hidden = true;
      if (dot) dot.classList.add("is-hidden");
    });
  }

  window.AgroNotificacoes = {
    init,
    refresh: () => {
      const list = $("[data-notif-list]");
      if (!list) return Promise.resolve();
      return sincronizar(list, $("[data-notif-empty]"), $("[data-notif-dot]"), $("[data-notif-mark-all]"));
    },
  };
})();
