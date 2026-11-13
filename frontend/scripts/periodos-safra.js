/* Períodos de safra (GET /api/propriedades/periodos-safra). */
(() => {
  "use strict";

  const SAFRA_RE = /^\d{4}\/\d{2}$/;

  function valida(texto) {
    if (!texto || !SAFRA_RE.test(String(texto).trim())) return false;
    const ano = Number(texto.slice(0, 4));
    const sufixo = Number(texto.slice(5, 7));
    return sufixo === (ano + 1) % 100;
  }

  function formatar(anoInicio) {
    const sufixo = String((anoInicio + 1) % 100).padStart(2, "0");
    return `${anoInicio}/${sufixo}`;
  }

  function padraoLocal() {
    const hoje = new Date();
    const ano = hoje.getFullYear();
  const mes = hoje.getMonth() + 1;
    return mes >= 7 ? formatar(ano) : formatar(ano - 1);
  }

  function sugerirLocal(antes = 3, depois = 2) {
    const base = Number(padraoLocal().slice(0, 4));
    const opcoes = [];
    for (let i = -antes; i <= depois; i += 1) opcoes.push(formatar(base + i));
    return opcoes;
  }

  function fallbackLocal(extra = []) {
    const padrao = padraoLocal();
    const set = new Set([...sugerirLocal(), ...extra].filter(valida));
    if (padrao) set.add(padrao);
    const opcoes = [...set].sort((a, b) => Number(b.slice(0, 4)) - Number(a.slice(0, 4)));
    return { padrao, sugeridos: sugerirLocal(), emUso: [], opcoes, local: true };
  }

  async function carregar(force = false) {
    if (window.__agroPeriodosSafra && !force) return window.__agroPeriodosSafra;

    const token = sessionStorage.getItem("agro.sessionToken");
    if (!token) return fallbackLocal();

    try {
      const response = await fetch(`${window.location.origin}/api/propriedades/periodos-safra`, {
        headers: { Authorization: `Bearer ${token}` },
        signal: AbortSignal.timeout(15000),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.error || "Não foi possível carregar períodos de safra.");

      const opcoes = Array.isArray(body.opcoes) && body.opcoes.length
        ? body.opcoes
        : fallbackLocal().opcoes;

      window.__agroPeriodosSafra = {
        padrao: body.padrao && valida(body.padrao) ? body.padrao : padraoLocal(),
        sugeridos: Array.isArray(body.sugeridos) ? body.sugeridos : sugerirLocal(),
        emUso: Array.isArray(body.emUso) ? body.emUso : [],
        opcoes,
      };
      return window.__agroPeriodosSafra;
    } catch {
      window.__agroPeriodosSafra = fallbackLocal();
      return window.__agroPeriodosSafra;
    }
  }

  async function garantirSelect(select, valorAtual = "") {
    const extra = valorAtual && valida(valorAtual) ? [valorAtual] : [];
    const data = await carregar(true);
    const opcoes = [...new Set([...data.opcoes, ...extra])].sort(
      (a, b) => Number(b.slice(0, 4)) - Number(a.slice(0, 4)),
    );
    preencherSelect(select, opcoes);
    return { ...data, opcoes };
  }

  function preencherSelect(select, opcoes, placeholder = "Selecione a safra") {
    if (!select) return;
    const atual = select.value;
    const lista = opcoes.length ? opcoes : [];
    select.replaceChildren(
      new Option(placeholder, ""),
      ...lista.map((item) => new Option(item, item)),
    );
    if (lista.includes(atual)) select.value = atual;
    select.dispatchEvent(new Event("change", { bubbles: true }));
  }

  function aplicarDatalist(input, opcoes, id = "agro-periodos-safra") {
    if (!input) return;
    let datalist = document.getElementById(id);
    if (!datalist) {
      datalist = document.createElement("datalist");
      datalist.id = id;
      document.body.appendChild(datalist);
    }
    datalist.replaceChildren(...opcoes.map((item) => {
      const option = document.createElement("option");
      option.value = item;
      return option;
    }));
    input.setAttribute("list", id);
    input.setAttribute("placeholder", "Ex.: 2026/27");
  }

  function limparCache() {
    window.__agroPeriodosSafra = null;
  }

  window.AgroPeriodosSafra = {
    carregar, garantirSelect, preencherSelect, aplicarDatalist, valida, limparCache, fallbackLocal,
  };
})();
