/* Autor: Tomas de Paula Michelon Toniato - RA: 25004211 */
/* Colheita e produtividade (#225): a API (/api/colheitas) é a fonte dos registros,
 * do resumo do recorte e do comparativo de ciclos. Nada é calculado sobre valores
 * fixos: sem colheita ou sem área, a produtividade aparece como indisponível. */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const base = window.location.port === "8080" ? window.location.origin : "http://127.0.0.1:8080";
  const ETAPAS_ABERTAS = ["Plantio", "Manejo", "Colheita"];
  const UNIDADES = { kg: "kg", t: "t", sc: "sc" };
  const state = { safras: [], talhoes: [], lista: [], resumo: null, ciclos: [], cultura: "", safraId: "", excluindo: null, busy: false };
  let consulta = 0;
  let recarga = 0;

  const toast = (type, title, description) => window.AgroToast?.show({ type, title, description });
  const esc = (value) => String(value ?? "").replace(/[&<>"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[char]);
  const numero = (value, casas = 0) => Number(value).toLocaleString("pt-BR", { maximumFractionDigits: casas });
  const data = (value) => (value ? value.split("-").reverse().join("/") : "-");

  // O "hoje" segue o fuso da fazenda, igual ao servidor.
  function hoje() {
    const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(new Date());
    return ["year", "month", "day"].map((key) => parts.find((part) => part.type === key).value).join("-");
  }

  // Chamada autenticada; o erro devolve status, mensagem e campos da API.
  async function api(path, options = {}) {
    const token = sessionStorage.getItem("agro.sessionToken");
    if (!token) throw { error: "Sessão ausente. Entre novamente." };
    const response = await fetch(base + path, {
      ...options,
      headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
      signal: AbortSignal.timeout(15000),
    });
    const body = response.status === 204 ? null : await response.json().catch(() => ({}));
    if (!response.ok) throw { status: response.status, ...(body || {}) };
    return body;
  }

  function mensagem(texto, retry = false) {
    $("[data-harvest-message]").textContent = texto;
    $("[data-harvest-retry]").hidden = !retry;
  }

  /* ------------------------------------------------------------ consulta */

  function filtros() {
    const values = { cultura: state.cultura, safraId: state.safraId };
    return new URLSearchParams(Object.entries(values).filter(([, value]) => value)).toString();
  }

  // Lista, resumo e comparativo são lidos juntos; resposta antiga nunca substitui a nova.
  async function carregar() {
    const numeroConsulta = ++consulta;
    mensagem("Carregando colheitas…");
    try {
      const query = filtros();
      const comparar = state.cultura ? `?cultura=${encodeURIComponent(state.cultura)}` : "";
      const [lista, resumo, ciclos] = await Promise.all([
        api(`/api/colheitas?${query}`), api(`/api/colheitas/resumo?${query}`), api(`/api/colheitas/comparativo${comparar}`),
      ]);
      if (numeroConsulta !== consulta) return;
      Object.assign(state, { lista, resumo, ciclos });
      mensagem("");
    } catch (error) {
      if (numeroConsulta !== consulta) return;
      Object.assign(state, { lista: [], resumo: null, ciclos: [] });
      mensagem(`${error?.error || "Servidor indisponível."} Os indicadores de colheita não foram atualizados.`, true);
    }
    render();
  }

  async function carregarReferencias() {
    const [safras, talhoes] = await Promise.all([api("/api/safras"), api("/api/talhoes")]);
    state.safras = Array.isArray(safras) ? safras : [];
    state.talhoes = Array.isArray(talhoes) ? talhoes : [];
    preencherFiltros();
  }

  async function iniciar() {
    mensagem("Carregando safras e talhões…");
    try {
      await carregarReferencias();
    } catch (error) {
      mensagem(`${error?.error || "Servidor indisponível."} Não foi possível carregar safras e talhões.`, true);
      return;
    }
    await carregar();
  }

  /* ------------------------------------------------------------ renderização */

  const kg = (value) => `${numero(value, 2)} kg`;
  const nomeSafra = (safra) => `${safra.nome} · ${safra.cultura}`;

  function preencherFiltros() {
    const culturas = [...new Set(state.safras.map((safra) => safra.cultura).filter(Boolean))];
    const cultura = $("[data-harvest-culture]");
    cultura.innerHTML = `<option value="">Todas as culturas</option>${culturas.map((nome) => `<option value="${esc(nome)}">${esc(nome)}</option>`).join("")}`;
    cultura.value = culturas.includes(state.cultura) ? state.cultura : "";
    state.cultura = cultura.value;

    const safras = state.safras.filter((safra) => !state.cultura || safra.cultura === state.cultura);
    const safra = $("[data-harvest-season]");
    safra.innerHTML = `<option value="">Todas as safras</option>${safras.map((item) => `<option value="${esc(item.id)}">${esc(nomeSafra(item))}</option>`).join("")}`;
    safra.value = safras.some((item) => item.id === state.safraId) ? state.safraId : "";
    state.safraId = safra.value;
  }

  function renderMetricas() {
    const resumo = state.resumo;
    const escopo = state.safras.find((safra) => safra.id === state.safraId)?.nome || state.cultura || "todas as safras";
    $("[data-harvest-total]").textContent = resumo ? kg(resumo.totalKg) : "-";
    $("[data-harvest-total-sc]").textContent = resumo ? `${numero(resumo.totalSacas, 2)} sc de 60 kg` : "";
    $("[data-harvest-area]").textContent = resumo ? `${numero(resumo.areaHectares, 2)} ha` : "-";
    $("[data-harvest-area-desc]").textContent = resumo ? "área real dos talhões de cada ciclo" : "";
    $("[data-harvest-yield]").textContent = resumo?.produtividadeScHa == null ? "-" : `${numero(resumo.produtividadeScHa, 1)} sc/ha`;
    $("[data-harvest-yield-kg]").textContent = !resumo ? "" : resumo.produtividadeKgHa == null
      ? (resumo.registros ? "sem área de talhão no recorte" : "sem colheita no recorte")
      : `${numero(resumo.produtividadeKgHa, 1)} kg/ha`;
    $("[data-harvest-count]").textContent = resumo ? String(resumo.registros) : "-";
    $("[data-harvest-scope]").textContent = resumo ? `recorte: ${escopo}` : "";
  }

  function produtividade(ciclo) {
    if (ciclo.produtividadeScHa != null) return `${numero(ciclo.produtividadeScHa, 1)} sc/ha · ${numero(ciclo.produtividadeKgHa, 1)} kg/ha`;
    return ciclo.registros ? "Sem área de talhão" : "Sem colheita";
  }

  function variacao(ciclo) {
    if (ciclo.variacaoPercentual == null) return `<span class="harvest-muted">${ciclo.produtividadeKgHa == null ? "-" : "Sem ciclo anterior"}</span>`;
    const sinal = ciclo.variacaoPercentual > 0 ? "+" : "";
    const css = ciclo.variacaoPercentual > 0 ? "is-up" : ciclo.variacaoPercentual < 0 ? "is-down" : "";
    return `<span class="harvest-variation ${css}">${sinal}${numero(ciclo.variacaoPercentual, 1)}%</span>`;
  }

  function renderComparativo() {
    $("[data-harvest-compare] tbody").innerHTML = state.ciclos.map((ciclo) => `<tr data-harvest-cycle="${esc(ciclo.safraId)}"${ciclo.safraId === state.safraId ? ' class="is-selected"' : ""}><td><strong>${esc(ciclo.safraNome)}</strong><small class="harvest-muted">${esc(ciclo.etapa)}${ciclo.talhoesNaSafra ? ` · ${ciclo.talhoesColhidos}/${ciclo.talhoesNaSafra} talhões colhidos` : ""}</small></td><td>${esc(ciclo.cultura)}</td><td class="harvest-nowrap">${data(ciclo.dataInicio)} a ${data(ciclo.dataFim)}</td><td class="harvest-nowrap">${numero(ciclo.areaHectares, 2)} ha</td><td class="harvest-nowrap">${ciclo.registros ? kg(ciclo.totalKg) : "-"}</td><td data-harvest-yield-cell>${esc(produtividade(ciclo))}</td><td data-harvest-variation>${variacao(ciclo)}</td></tr>`).join("");
    $("[data-harvest-compare-empty]").hidden = state.ciclos.length > 0;
  }

  function renderLista() {
    $("[data-harvest-table] tbody").innerHTML = state.lista.map((item) => {
      const bloqueio = item.finalizada ? ' disabled title="Safra finalizada: somente leitura."' : "";
      return `<tr data-harvest-id="${esc(item.id)}"><td class="harvest-nowrap">${data(item.data)}</td><td>${esc(item.safraNome)}</td><td>${esc(item.talhaoNome)}</td><td class="harvest-nowrap">${numero(item.quantidade, 3)} ${esc(UNIDADES[item.unidade] || item.unidade)}</td><td class="harvest-nowrap">${kg(item.quantidadeKg)}</td><td>${esc(item.observacao || "-")}</td><td class="harvest-row-actions"><button class="btn btn-ghost btn-small" type="button" data-edit-harvest="${esc(item.id)}"${bloqueio}>Editar</button><button class="btn btn-ghost btn-small harvest-delete" type="button" data-delete-harvest="${esc(item.id)}"${bloqueio}>Excluir</button></td></tr>`;
    }).join("");
    $("[data-harvest-table]").hidden = state.lista.length === 0;
    $("[data-harvest-empty]").hidden = state.lista.length > 0 || !state.resumo;
  }

  function render() {
    renderMetricas();
    renderComparativo();
    renderLista();
  }

  /* ------------------------------------------------------------ formulário */

  function modal(name, open) {
    const target = $(`[data-modal='${name}']`);
    target.classList.toggle("is-open", open);
    target.setAttribute("aria-hidden", String(!open));
  }

  function limparErros(form) {
    $$("[data-error-for]", form).forEach((node) => { node.textContent = ""; });
    $$("[aria-invalid='true']", form).forEach((node) => node.removeAttribute("aria-invalid"));
    $("[data-harvest-form-error]", form).textContent = "";
  }

  function mostrarErros(form, fields) {
    let primeiro;
    Object.entries(fields).forEach(([name, texto]) => {
      const campo = form.elements[name];
      const saida = $(`[data-error-for='${name}']`, form);
      if (saida) saida.textContent = texto;
      if (campo) {
        campo.setAttribute("aria-invalid", "true");
        primeiro ||= campo;
      }
    });
    primeiro?.focus();
  }

  // Só safras do plantio em diante e não finalizadas recebem colheita nova.
  function preencherSafrasDoForm(atual = "") {
    const select = $("[data-harvest-form] [name='safraId']");
    select.innerHTML = `<option value="">Selecione</option>${state.safras.map((safra) => {
      const aberta = ETAPAS_ABERTAS.includes(safra.etapa) || safra.id === atual;
      const motivo = safra.etapa === "Finalização" ? " (finalizada)" : aberta ? "" : ` (${safra.etapa.toLowerCase()})`;
      return `<option value="${esc(safra.id)}"${aberta ? "" : " disabled"}>${esc(nomeSafra(safra) + motivo)}</option>`;
    }).join("")}`;
    select.value = atual;
  }

  function preencherTalhoesDoForm(talhaoAtual = "") {
    const form = $("[data-harvest-form]");
    const safra = state.safras.find((item) => item.id === form.elements.safraId.value);
    const nomes = new Map(state.talhoes.map((talhao) => [talhao.id, talhao]));
    const opcoes = (safra?.talhoes || []).map((id) => nomes.get(id)).filter(Boolean);
    form.elements.talhaoId.innerHTML = `<option value="">${safra ? "Selecione" : "Escolha a safra primeiro"}</option>${opcoes.map((talhao) => `<option value="${esc(talhao.id)}">${esc(talhao.nome)} · ${numero(talhao.hectares, 2)} ha</option>`).join("")}`;
    form.elements.talhaoId.value = opcoes.some((talhao) => talhao.id === talhaoAtual) ? talhaoAtual : opcoes.length === 1 ? opcoes[0].id : "";
  }

  function abrirFormulario(item = null) {
    const form = $("[data-harvest-form]");
    form.reset();
    limparErros(form);
    form.dataset.editingId = item ? item.id : "";
    $("#harvest-form-title").textContent = item ? "Editar colheita" : "Registrar colheita";
    // Colheita nova já nasce na safra filtrada, se ela estiver aberta.
    const filtrada = state.safras.find((safra) => safra.id === state.safraId && ETAPAS_ABERTAS.includes(safra.etapa));
    preencherSafrasDoForm(item ? item.safraId : filtrada?.id || "");
    preencherTalhoesDoForm(item ? item.talhaoId : "");
    form.elements.quantidade.value = item ? item.quantidade : "";
    form.elements.unidade.value = item ? item.unidade : "sc";
    form.elements.data.value = item ? item.data : hoje();
    form.elements.data.max = hoje();
    form.elements.observacao.value = item?.observacao || "";
    modal("harvest-form", true);
    (item ? form.elements.quantidade : form.elements.safraId).focus();
  }

  function validar(form, payload) {
    const erros = {};
    if (!payload.safraId) erros.safraId = "Selecione a safra.";
    if (!payload.talhaoId) erros.talhaoId = "Selecione o talhão.";
    if (!Number.isFinite(payload.quantidade) || payload.quantidade <= 0) erros.quantidade = "Informe uma quantidade maior que zero.";
    if (!payload.data) erros.data = "Informe a data da colheita.";
    else if (payload.data > hoje()) erros.data = "A data da colheita não pode estar no futuro.";
    if (Object.keys(erros).length) {
      mostrarErros(form, erros);
      return false;
    }
    return true;
  }

  async function salvar(form) {
    if (state.busy) return;
    limparErros(form);
    const editando = form.dataset.editingId;
    const quantidade = form.elements.quantidade.value === "" ? NaN : Number(form.elements.quantidade.value);
    const payload = {
      safraId: form.elements.safraId.value, talhaoId: form.elements.talhaoId.value, quantidade,
      unidade: form.elements.unidade.value, data: form.elements.data.value, observacao: form.elements.observacao.value.trim() || null,
    };
    if (!validar(form, payload)) { toast("error", "Colheita não salva", "Revise os campos destacados."); return; }
    state.busy = true;
    $$("button", form).forEach((button) => { button.disabled = true; });
    try {
      const salva = await api(editando ? `/api/colheitas/${encodeURIComponent(editando)}` : "/api/colheitas", { method: editando ? "PUT" : "POST", body: JSON.stringify(payload) });
      modal("harvest-form", false);
      toast("success", editando ? "Colheita atualizada" : "Colheita registrada", `${numero(salva.quantidade, 3)} ${salva.unidade} em ${salva.talhaoNome} (${salva.safraNome}), confirmado pelo servidor.`);
    } catch (error) {
      if (error?.fields) mostrarErros(form, error.fields);
      if (!error?.fields || error.status === 409) $("[data-harvest-form-error]", form).textContent = error?.error || "Servidor indisponível. Confira a lista antes de repetir o envio.";
      toast("error", "Colheita não salva", error?.error === "Validation failed" ? "Revise os campos destacados." : error?.error || "Servidor indisponível.");
    }
    state.busy = false;
    $$("button", form).forEach((button) => { button.disabled = false; });
    await carregar();
  }

  /* ------------------------------------------------------------ eventos */

  function iniciarEventos() {
    $("[data-new-harvest]").addEventListener("click", () => abrirFormulario());
    $("[data-close-harvest]").addEventListener("click", () => modal("harvest-form", false));
    $("[data-close-harvest-delete]").addEventListener("click", () => modal("harvest-delete", false));
    $("[data-harvest-retry]").addEventListener("click", () => (state.safras.length ? carregar() : iniciar()));
    ["harvest-form", "harvest-delete"].forEach((name) => {
      const alvo = $(`[data-modal='${name}']`);
      alvo.addEventListener("click", (event) => { if (event.target === alvo) modal(name, false); });
    });
    $("[data-harvest-form] [name='safraId']").addEventListener("change", () => preencherTalhoesDoForm());
    $("[data-harvest-form]").addEventListener("submit", (event) => { event.preventDefault(); salvar(event.currentTarget); });

    $("[data-harvest-culture]").addEventListener("change", (event) => {
      state.cultura = event.target.value;
      preencherFiltros();
      carregar();
    });
    $("[data-harvest-season]").addEventListener("change", (event) => {
      state.safraId = event.target.value;
      carregar();
    });
    $("[data-harvest-compare]").addEventListener("click", (event) => {
      const linha = event.target.closest("[data-harvest-cycle]");
      if (!linha) return;
      state.safraId = state.safraId === linha.dataset.harvestCycle ? "" : linha.dataset.harvestCycle;
      if (state.safraId && state.cultura && state.safras.find((safra) => safra.id === state.safraId)?.cultura !== state.cultura) state.cultura = "";
      preencherFiltros();
      carregar();
    });

    $("[data-harvest-table]").addEventListener("click", (event) => {
      const editar = event.target.closest("[data-edit-harvest]");
      const excluir = event.target.closest("[data-delete-harvest]");
      const item = state.lista.find((registro) => registro.id === (editar || excluir)?.dataset[editar ? "editHarvest" : "deleteHarvest"]);
      if (!item) return;
      if (editar) abrirFormulario(item);
      if (excluir) {
        state.excluindo = item;
        $("[data-harvest-delete-desc]").textContent = `${numero(item.quantidade, 3)} ${item.unidade} de ${data(item.data)} em ${item.talhaoNome} será removido no servidor e sairá da produção e da produtividade.`;
        modal("harvest-delete", true);
      }
    });
    $("[data-confirm-harvest-delete]").addEventListener("click", async (event) => {
      const item = state.excluindo;
      const botao = event.currentTarget;
      if (!item || state.busy) return;
      state.busy = true;
      botao.disabled = true;
      try {
        await api(`/api/colheitas/${encodeURIComponent(item.id)}`, { method: "DELETE" });
        modal("harvest-delete", false);
        state.excluindo = null;
        toast("info", "Colheita excluída", "O registro foi removido no servidor.");
      } catch (error) {
        toast("error", "Exclusão não confirmada", error?.error || "Servidor indisponível.");
      }
      state.busy = false;
      botao.disabled = false;
      await carregar();
    });

    // Criar ou avançar uma safra (lista de ciclos acima) muda as safras que recebem colheita.
    const ciclos = $("[data-safras-list]");
    if (ciclos) new MutationObserver(() => {
      clearTimeout(recarga);
      recarga = setTimeout(() => { carregarReferencias().then(carregar).catch(() => {}); }, 300);
    }).observe(ciclos, { childList: true });
  }

  document.addEventListener("DOMContentLoaded", () => {
    iniciarEventos();
    iniciar();
  });
})();
