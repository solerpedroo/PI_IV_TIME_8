/* Autor: Pedro André do Carmo Chavier - RA: 25018639 */
/**
 * Agro: Manutenção de Equipamentos (#237).
 * --------------------------------------------------------------------------
 * - Tudo vem da API: resumo, agenda de preventivas (situação calculada no
 *   servidor), equipamentos, histórico. Sem dados de exemplo; "-" até a API responder.
 * - Formulários de equipamento, preventiva e intervenção: erro do servidor volta
 *   no campo e o toast de sucesso só aparece depois da confirmação.
 * - Intervenção com valor gera o custo em Custos (Maquinário); a exclusão avisa
 *   que o custo também sai.
 * - ?equipamentoId= abre o histórico daquele equipamento (link que vem de Custos).
 * - Não usa o AgroLocal: equipamentos e manutenção não ficam no cache local.
 */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const API_BASE = location.port === "8080" ? location.origin : "http://127.0.0.1:8080";
  const money = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL", maximumFractionDigits: 2 });
  const FINALIZACAO = "Finalização";
  const SITUACOES = { atrasada: ["Atrasada", "badge-error"], proxima: ["Próxima", "badge-warning"], futura: ["Futura", "badge-neutral"] };

  const state = {
    resumo: null,
    equipamentos: [],
    preventivas: [],
    talhoes: [],
    safras: [],
    filtro: "todas",
    editandoEquipamento: null,
    editandoPreventiva: null,
    editandoIntervencao: null,
    historicoDe: null,   // id do equipamento aberto no histórico
    historico: [],
    excluindo: null,     // intervenção a excluir
    cancelando: null,    // preventiva a cancelar
    pronto: false,
  };
  let consulta = 0;
  let consultaHistorico = 0;

  function toast(type, title, description) {
    if (window.AgroToast) window.AgroToast.show({ type, title, description });
  }

  async function api(path, method = "GET", body) {
    const token = sessionStorage.getItem("agro.sessionToken");
    if (!token) throw new Error("Entre novamente para acessar a manutenção.");
    const response = await fetch(API_BASE + path, {
      method,
      headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(15000),
    });
    const data = response.status === 204 ? {} : await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = new Error(data.error === "Validation failed" ? "Revise os campos destacados." : data.error || "Falha na comunicação com o servidor.");
      error.fields = data.fields || {};
      error.status = response.status;
      throw error;
    }
    return data;
  }

  function node(tag, text, className) {
    const el = document.createElement(tag);
    if (text != null) el.textContent = text;
    if (className) el.className = className;
    return el;
  }
  const date = (value) => value ? value.split("-").reverse().join("/") : "-";
  const numero = (value, casas = 1) => Number(value).toLocaleString("pt-BR", { maximumFractionDigits: casas });
  function hoje() {
    const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(new Date());
    return ["year", "month", "day"].map((key) => parts.find((p) => p.type === key).value).join("-");
  }
  const equipamentoPorId = (id) => state.equipamentos.find((e) => e.id === id) || null;

  /* ---------------------------------------------------------------------- */
  /* Modais e formulários                                                   */
  /* ---------------------------------------------------------------------- */
  function abrir(nome) {
    const modal = $(`[data-modal='${nome}']`);
    modal.classList.add("is-open");
    modal.setAttribute("aria-hidden", "false");
    modal.querySelector("input:not([type=hidden]), select, textarea, button")?.focus();
  }
  function fechar(nome) {
    const modal = $(`[data-modal='${nome}']`);
    modal.classList.remove("is-open");
    modal.setAttribute("aria-hidden", "true");
  }
  function limpar(form) {
    $$(".field-error", form).forEach((el) => { el.textContent = ""; });
    $$("[aria-invalid]", form).forEach((el) => el.removeAttribute("aria-invalid"));
  }
  function erroNoCampo(form, campo, texto) {
    const alvo = $(`[data-error-for="${campo}"]`, form);
    if (alvo) alvo.textContent = texto;
    form.elements.namedItem(campo)?.setAttribute("aria-invalid", "true");
  }
  function ocupado(form, valor) {
    if (valor) form.setAttribute("aria-busy", "true");
    else form.removeAttribute("aria-busy");
    $$("button", form).forEach((botao) => { botao.disabled = valor; });
  }
  // texto vazio vira null; número vazio vira null (o servidor valida faixas e formatos)
  const texto = (form, nome) => form.elements.namedItem(nome).value.trim() || null;
  function numeroDoCampo(form, nome) {
    const valor = form.elements.namedItem(nome).value.trim();
    return valor === "" ? null : Number(valor);
  }

  async function enviar(form, path, method, body, sucesso) {
    if (form.getAttribute("aria-busy") === "true") return null;
    limpar(form);
    ocupado(form, true);
    let salvo;
    try {
      salvo = await api(path, method, body);
    } catch (error) {
      Object.entries(error.fields).forEach(([campo, mensagem]) => erroNoCampo(form, campo, mensagem));
      $("[data-form-error]", form).textContent = error.message;
      ocupado(form, false);
      return null;
    }
    ocupado(form, false);
    sucesso(salvo);
    return salvo;
  }

  function preencherSelects() {
    const opcoesEquipamentos = (placeholder) => [new Option(placeholder, ""),
      ...state.equipamentos.map((e) => new Option(e.nome + (e.status === "Inativo" ? " (inativo)" : ""), e.id))];
    for (const select of [$("#preventive-equipamento"), $("#intervention-equipamento")]) {
      const atual = select.value;
      select.replaceChildren(...opcoesEquipamentos("Selecione"));
      select.value = atual;
    }
    const talhao = $("#intervention-talhao");
    talhao.replaceChildren(new Option("Propriedade (sem talhão)", ""), ...state.talhoes.map((t) => new Option(t.nome, t.id)));
    const safra = $("#intervention-safra");
    safra.replaceChildren(new Option("Sem safra", ""), ...state.safras.map((s) => {
      const opcao = new Option(s.nome + (s.etapa === FINALIZACAO ? " (finalizada)" : ""), s.id);
      opcao.disabled = s.etapa === FINALIZACAO; // como em Custos: safra finalizada fica só para consulta
      return opcao;
    }));
  }

  /* ---------------------------------------------------------------------- */
  /* Desenho                                                                */
  /* ---------------------------------------------------------------------- */
  function metricas() {
    const r = state.resumo;
    $("[data-maint-metric='ativos']").textContent = r ? String(r.ativos) : "-";
    $("[data-maint-metric-desc='ativos']").textContent = r ? `de ${r.equipamentos} cadastrado(s)` : "de - cadastrados";
    $("[data-maint-metric='atrasadas']").textContent = r ? String(r.preventivasAtrasadas) : "-";
    $("[data-maint-metric='proximas']").textContent = r ? String(r.preventivasProximas) : "-";
    $("[data-maint-metric='gasto']").textContent = r ? money.format(r.gastoNoAno) : "-";
  }

  function botao(texto, atributo, valor, classe = "btn btn-ghost maint-action") {
    const b = node("button", texto, classe);
    b.type = "button";
    b.dataset[atributo] = valor;
    return b;
  }

  function agenda() {
    const programadas = state.preventivas.filter((p) => SITUACOES[p.situacao]);
    const visiveis = programadas.filter((p) => state.filtro === "todas" || p.situacao === state.filtro);
    $("[data-agenda-count]").textContent = state.pronto ? `${visiveis.length} preventiva(s) programada(s)` : "-";
    $("[data-agenda-table] tbody").replaceChildren(...visiveis.map((p) => {
      const tr = node("tr");
      tr.dataset.preventiveId = p.id;
      tr.dataset.situacao = p.situacao;
      const [rotulo, tom] = SITUACOES[p.situacao];
      const situacao = node("td");
      const quando = p.diasAte < 0 ? `${-p.diasAte} dia(s) atrás` : p.diasAte === 0 ? "hoje" : `em ${p.diasAte} dia(s)`;
      situacao.append(node("span", rotulo, "badge " + tom), node("span", " " + quando, "maint-muted"));
      const acoes = node("td", null, "maint-actions");
      acoes.append(botao("Registrar como feita", "doPreventive", p.id, "btn btn-outline maint-action"),
        botao("Editar", "editPreventive", p.id), botao("Cancelar", "cancelPreventive", p.id));
      tr.append(node("td", date(p.dataPrevista)), node("td", p.equipamentoNome || "-", "maint-strong"),
        node("td", p.descricao + (p.intervaloDias ? ` (a cada ${p.intervaloDias} dias)` : "")), situacao, acoes);
      return tr;
    }));
    $("[data-agenda-empty]").hidden = !state.pronto || visiveis.length > 0;
  }

  function equipamentos() {
    $("[data-equipment-count]").textContent = state.pronto ? `${state.equipamentos.length} equipamento(s)` : "-";
    $("[data-equipment-table] tbody").replaceChildren(...state.equipamentos.map((e) => {
      const tr = node("tr");
      tr.dataset.equipmentId = e.id;
      const status = node("td");
      status.append(node("span", e.status, "badge " + (e.status === "Ativo" ? "badge-success" : e.status === "Inativo" ? "badge-neutral" : "badge-warning")));
      const acoes = node("td", null, "maint-actions");
      acoes.append(botao("Histórico", "historyEquipment", e.id, "btn btn-outline maint-action"), botao("Editar", "editEquipment", e.id));
      const marca = [e.marca, e.modelo].filter(Boolean).join(" ") || "-";
      const proxima = e.proximaPreventiva ? `${date(e.proximaPreventiva.dataPrevista)} · ${e.proximaPreventiva.descricao}` : "Sem preventiva";
      const ultima = e.ultimaIntervencao ? `${date(e.ultimaIntervencao.data)} · ${e.ultimaIntervencao.descricao}` : "Sem intervenção";
      tr.append(node("td", e.nome, "maint-strong"), node("td", e.tipo), node("td", marca, "maint-muted"),
        node("td", e.horimetro == null ? "-" : `${numero(e.horimetro)} h`), status,
        node("td", proxima, "maint-muted"), node("td", ultima, "maint-muted"), acoes);
      return tr;
    }));
    $("[data-equipment-empty]").hidden = !state.pronto || state.equipamentos.length > 0;
  }

  function mensagem(textoMensagem, erro = false) {
    $("[data-maint-message]").textContent = textoMensagem;
    $("[data-maint-retry]").hidden = !erro;
  }

  function prontos(valor) {
    state.pronto = valor;
    $$("[data-open-equipment], [data-open-preventive], [data-open-intervention]").forEach((b) => { b.disabled = !valor; });
  }

  // Uma resposta antiga nunca substitui a mais nova.
  async function carregar() {
    const numeroDaConsulta = ++consulta;
    mensagem("Carregando manutenção…");
    try {
      const [resumo, lista, preventivas, talhoes, safras] = await Promise.all([
        api("/api/manutencao/resumo"), api("/api/equipamentos"), api("/api/preventivas"), api("/api/talhoes"), api("/api/safras")]);
      if (numeroDaConsulta !== consulta) return false;
      Object.assign(state, { resumo, equipamentos: lista, preventivas, talhoes, safras });
      prontos(true);
      preencherSelects();
      metricas(); agenda(); equipamentos();
      mensagem("");
      return true;
    } catch (error) {
      if (numeroDaConsulta !== consulta) return false;
      Object.assign(state, { resumo: null, equipamentos: [], preventivas: [] });
      prontos(false);
      metricas(); agenda(); equipamentos();
      mensagem("Não foi possível consultar a manutenção. " + error.message, true);
      return false;
    }
  }

  /* ---------------------------------------------------------------------- */
  /* Equipamento                                                            */
  /* ---------------------------------------------------------------------- */
  function editarEquipamento(equipamento) {
    const form = $("[data-equipment-form]");
    state.editandoEquipamento = equipamento;
    form.reset(); limpar(form);
    $("#equipment-form-title").textContent = equipamento ? "Editar equipamento" : "Novo equipamento";
    if (equipamento) {
      for (const campo of ["nome", "tipo", "status", "marca", "modelo", "identificacao", "ano", "horimetro"]) {
        form.elements.namedItem(campo).value = equipamento[campo] ?? "";
      }
    }
    abrir("equipment-form");
  }

  function salvarEquipamento(event) {
    event.preventDefault();
    const form = event.currentTarget; // guardado antes do primeiro await
    const editando = state.editandoEquipamento;
    const corpo = {
      nome: texto(form, "nome"), tipo: texto(form, "tipo"), status: texto(form, "status"),
      marca: texto(form, "marca"), modelo: texto(form, "modelo"), identificacao: texto(form, "identificacao"),
      ano: numeroDoCampo(form, "ano"), horimetro: numeroDoCampo(form, "horimetro"),
    };
    const caminho = editando ? `/api/equipamentos/${encodeURIComponent(editando.id)}` : "/api/equipamentos";
    return enviar(form, caminho, editando ? "PUT" : "POST", corpo, async (salvo) => {
      fechar("equipment-form");
      toast("success", editando ? "Equipamento atualizado" : "Equipamento cadastrado", `${salvo.nome} foi salvo no servidor.`);
      await carregar();
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Preventiva                                                             */
  /* ---------------------------------------------------------------------- */
  function editarPreventiva(preventiva) {
    const form = $("[data-preventive-form]");
    state.editandoPreventiva = preventiva;
    form.reset(); limpar(form);
    $("#preventive-form-title").textContent = preventiva ? "Editar preventiva" : "Programar preventiva";
    if (preventiva) {
      form.elements.namedItem("equipamentoId").value = preventiva.equipamentoId;
      form.elements.namedItem("descricao").value = preventiva.descricao;
      form.elements.namedItem("dataPrevista").value = preventiva.dataPrevista;
      form.elements.namedItem("intervaloDias").value = preventiva.intervaloDias ?? "";
    }
    abrir("preventive-form");
  }

  function salvarPreventiva(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const editando = state.editandoPreventiva;
    const corpo = {
      equipamentoId: texto(form, "equipamentoId"), descricao: texto(form, "descricao"),
      dataPrevista: texto(form, "dataPrevista"), intervaloDias: numeroDoCampo(form, "intervaloDias"),
    };
    const caminho = editando ? `/api/preventivas/${encodeURIComponent(editando.id)}` : "/api/preventivas";
    return enviar(form, caminho, editando ? "PUT" : "POST", corpo, async () => {
      fechar("preventive-form");
      toast("success", editando ? "Preventiva atualizada" : "Preventiva programada", `${corpo.descricao} em ${date(corpo.dataPrevista)}.`);
      await carregar();
    });
  }

  async function cancelarPreventiva() {
    const preventiva = state.cancelando;
    const erro = $("[data-preventive-cancel-error]");
    if (!preventiva) return;
    erro.textContent = "";
    try {
      await api(`/api/preventivas/${encodeURIComponent(preventiva.id)}`, "PUT", {
        descricao: preventiva.descricao, dataPrevista: preventiva.dataPrevista,
        intervaloDias: preventiva.intervaloDias ?? null, status: "cancelada",
      });
    } catch (error) {
      erro.textContent = error.message;
      return;
    }
    fechar("preventive-cancel");
    state.cancelando = null;
    toast("success", "Preventiva cancelada", `${preventiva.descricao} saiu da agenda.`);
    await carregar();
  }

  /* ---------------------------------------------------------------------- */
  /* Intervenção                                                            */
  /* ---------------------------------------------------------------------- */
  function travarVinculo(form, travado) {
    form.elements.namedItem("equipamentoId").disabled = travado;
  }

  // origem: { equipamentoId, preventiva } para registrar como feita, ou a intervenção a editar
  function editarIntervencao({ intervencao = null, equipamentoId = "", preventiva = null } = {}) {
    const form = $("[data-intervention-form]");
    state.editandoIntervencao = intervencao;
    form.reset(); limpar(form);
    const titulo = intervencao ? "Editar intervenção" : preventiva ? "Registrar preventiva como feita" : "Registrar intervenção";
    $("#intervention-form-title").textContent = titulo;
    $("[data-intervention-hint]").textContent = preventiva
      ? `${preventiva.descricao} (prevista para ${date(preventiva.dataPrevista)}). Com valor, o custo é lançado em Custos (Maquinário).`
      : "Registre o que foi feito no equipamento. Com valor, o custo é lançado em Custos (Maquinário).";
    const campo = (nome) => form.elements.namedItem(nome);
    if (intervencao) {
      for (const nome of ["equipamentoId", "tipo", "descricao", "data", "responsavel", "horimetro", "valor", "talhaoId", "safraId"]) {
        campo(nome).value = intervencao[nome] ?? "";
      }
      campo("preventivaId").value = intervencao.preventivaId || "";
    } else {
      campo("equipamentoId").value = equipamentoId;
      campo("tipo").value = preventiva ? "preventiva" : "corretiva";
      campo("preventivaId").value = preventiva ? preventiva.id : "";
      campo("descricao").value = preventiva ? preventiva.descricao : "";
      campo("data").value = hoje();
    }
    // o equipamento e a preventiva de uma intervenção não mudam depois de registrados
    travarVinculo(form, Boolean(intervencao || preventiva));
    campo("tipo").disabled = Boolean(preventiva || intervencao?.preventivaId);
    abrir("intervention-form");
  }

  function salvarIntervencao(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const editando = state.editandoIntervencao;
    const corpo = {
      equipamentoId: form.elements.namedItem("equipamentoId").value || null,
      tipo: form.elements.namedItem("tipo").value,
      preventivaId: form.elements.namedItem("preventivaId").value || null,
      descricao: texto(form, "descricao"), data: texto(form, "data"), responsavel: texto(form, "responsavel"),
      horimetro: numeroDoCampo(form, "horimetro"), valor: numeroDoCampo(form, "valor"),
      talhaoId: texto(form, "talhaoId"), safraId: texto(form, "safraId"),
    };
    const caminho = editando ? `/api/intervencoes/${encodeURIComponent(editando.id)}` : "/api/intervencoes";
    return enviar(form, caminho, editando ? "PUT" : "POST", corpo, async (salvo) => {
      fechar("intervention-form");
      const custo = salvo.custoPendente ? " O custo ficou pendente: edite a intervenção para tentar de novo."
        : salvo.custoId ? ` Custo de ${money.format(salvo.custoValor)} lançado em Custos.` : " Sem valor: nenhum custo lançado.";
      toast(salvo.custoPendente ? "warning" : "success", editando ? "Intervenção atualizada" : "Intervenção registrada", `${salvo.equipamentoNome}: ${salvo.descricao}.${custo}`);
      await carregar();
      if (state.historicoDe) await historico(state.historicoDe);
    });
  }

  async function excluirIntervencao() {
    const intervencao = state.excluindo;
    const erro = $("[data-intervention-delete-error]");
    if (!intervencao) return;
    erro.textContent = "";
    try {
      await api(`/api/intervencoes/${encodeURIComponent(intervencao.id)}`, "DELETE");
    } catch (error) {
      erro.textContent = error.message;
      return;
    }
    fechar("intervention-delete");
    state.excluindo = null;
    toast("success", "Intervenção excluída", intervencao.custoId ? "O custo lançado em Custos também foi excluído." : "O histórico foi atualizado.");
    await carregar();
    if (state.historicoDe) await historico(state.historicoDe);
  }

  /* ---------------------------------------------------------------------- */
  /* Histórico                                                              */
  /* ---------------------------------------------------------------------- */
  async function historico(equipamentoId) {
    const numeroDaConsulta = ++consultaHistorico;
    const equipamento = equipamentoPorId(equipamentoId);
    state.historicoDe = equipamentoId;
    $("#history-title").textContent = "Histórico: " + (equipamento ? equipamento.nome : "equipamento");
    $("[data-history-subtitle]").textContent = equipamento ? [equipamento.tipo, equipamento.marca, equipamento.modelo].filter(Boolean).join(" · ") : "-";
    $("[data-history-message]").textContent = "Carregando histórico…";
    $("[data-history-table] tbody").replaceChildren();
    abrir("equipment-history");
    let lista;
    try {
      lista = await api(`/api/equipamentos/${encodeURIComponent(equipamentoId)}/historico`);
    } catch (error) {
      if (numeroDaConsulta !== consultaHistorico) return;
      $("[data-history-message]").textContent = "Não foi possível consultar o histórico. " + error.message;
      return;
    }
    if (numeroDaConsulta !== consultaHistorico) return;
    state.historico = lista;
    $("[data-history-message]").textContent = lista.length ? `${lista.length} intervenção(ões), da mais recente para a mais antiga.` : "Nenhuma intervenção registrada para este equipamento.";
    $("[data-history-table] tbody").replaceChildren(...lista.map((i) => {
      const tr = node("tr");
      tr.dataset.interventionId = i.id;
      const custo = node("td");
      if (i.custoId) {
        const link = node("a", money.format(i.custoValor), "card-link");
        link.href = "custos.html";
        link.title = "Ver o lançamento em Custos (Maquinário)";
        link.dataset.historyCost = i.custoId;
        custo.append(link);
      } else custo.textContent = "Sem custo";
      const acoes = node("td", null, "maint-actions");
      acoes.append(botao("Editar", "editIntervention", i.id), botao("Excluir", "deleteIntervention", i.id, "btn btn-ghost maint-action maint-danger"));
      const descricao = node("td");
      descricao.append(node("span", i.descricao, "maint-strong"));
      if (i.responsavel || i.talhaoNome || i.safraNome) descricao.append(node("span", [i.responsavel, i.talhaoNome, i.safraNome].filter(Boolean).join(" · "), "maint-muted maint-block"));
      tr.append(node("td", date(i.data)), node("td", i.tipo === "preventiva" ? "Preventiva" : "Corretiva"), descricao,
        node("td", i.horimetro == null ? "-" : `${numero(i.horimetro)} h`), custo, acoes);
      return tr;
    }));
  }

  /* ---------------------------------------------------------------------- */
  /* Eventos                                                                */
  /* ---------------------------------------------------------------------- */
  function init() {
    $("[data-open-equipment]").addEventListener("click", () => editarEquipamento(null));
    $("[data-open-preventive]").addEventListener("click", () => editarPreventiva(null));
    $("[data-open-intervention]").addEventListener("click", () => editarIntervencao());
    $("[data-history-new]").addEventListener("click", () => { fechar("equipment-history"); editarIntervencao({ equipamentoId: state.historicoDe || "" }); });
    $("[data-maint-retry]").addEventListener("click", () => carregar());
    $("[data-equipment-form]").addEventListener("submit", salvarEquipamento);
    $("[data-preventive-form]").addEventListener("submit", salvarPreventiva);
    $("[data-intervention-form]").addEventListener("submit", salvarIntervencao);
    $("[data-confirm-intervention-delete]").addEventListener("click", excluirIntervencao);
    $("[data-confirm-preventive-cancel]").addEventListener("click", cancelarPreventiva);

    $$("[data-agenda-filter]").forEach((chip) => chip.addEventListener("click", () => {
      state.filtro = chip.dataset.agendaFilter;
      $$("[data-agenda-filter]").forEach((c) => { c.classList.toggle("is-active", c === chip); c.setAttribute("aria-pressed", String(c === chip)); });
      agenda();
    }));

    // Ações das tabelas por delegação (as linhas são recriadas a cada consulta).
    document.addEventListener("click", (event) => {
      const alvo = event.target.closest("[data-do-preventive], [data-edit-preventive], [data-cancel-preventive], [data-history-equipment], [data-edit-equipment], [data-edit-intervention], [data-delete-intervention]");
      if (!alvo) return;
      const d = alvo.dataset;
      const preventiva = state.preventivas.find((p) => p.id === (d.doPreventive || d.editPreventive || d.cancelPreventive));
      if (d.doPreventive && preventiva) editarIntervencao({ equipamentoId: preventiva.equipamentoId, preventiva });
      else if (d.editPreventive && preventiva) editarPreventiva(preventiva);
      else if (d.cancelPreventive && preventiva) {
        state.cancelando = preventiva;
        $("[data-preventive-cancel-desc]").textContent = `${preventiva.descricao} (${preventiva.equipamentoNome}, prevista para ${date(preventiva.dataPrevista)}) sai da agenda. Nenhuma intervenção é registrada.`;
        $("[data-preventive-cancel-error]").textContent = "";
        abrir("preventive-cancel");
      } else if (d.historyEquipment) historico(d.historyEquipment);
      else if (d.editEquipment) editarEquipamento(equipamentoPorId(d.editEquipment));
      else if (d.editIntervention) {
        const intervencao = state.historico.find((i) => i.id === d.editIntervention);
        if (intervencao) { fechar("equipment-history"); editarIntervencao({ intervencao }); }
      } else if (d.deleteIntervention) {
        const intervencao = state.historico.find((i) => i.id === d.deleteIntervention);
        if (!intervencao) return;
        state.excluindo = intervencao;
        $("[data-intervention-delete-desc]").textContent = intervencao.custoId
          ? `${intervencao.descricao} (${date(intervencao.data)}) será removida do histórico. O custo de ${money.format(intervencao.custoValor)} lançado em Custos também será excluído.`
          : `${intervencao.descricao} (${date(intervencao.data)}) será removida do histórico. Ela não tem custo lançado.`;
        $("[data-intervention-delete-error]").textContent = "";
        abrir("intervention-delete");
      }
    });

    for (const nome of ["equipment-form", "preventive-form", "intervention-form", "equipment-history", "intervention-delete", "preventive-cancel"]) {
      const modal = $(`[data-modal='${nome}']`);
      $$("[data-close-modal]", modal).forEach((b) => b.addEventListener("click", () => {
        fechar(nome);
        if (nome === "equipment-history") state.historicoDe = null;
      }));
      modal.addEventListener("click", (event) => { if (event.target === modal) fechar(nome); });
    }

    metricas(); agenda(); equipamentos();
    carregar().then((ok) => {
      const pedido = new URLSearchParams(location.search).get("equipamentoId");
      if (ok && pedido) {
        if (equipamentoPorId(pedido)) historico(pedido);
        else toast("error", "Equipamento não encontrado", "O equipamento do link não pertence à propriedade ativa.");
      }
    });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
