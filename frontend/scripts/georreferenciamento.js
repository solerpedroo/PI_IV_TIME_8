/* Autor: Pedro André do Carmo Chavier - RA: 25018639 */
/**
 * Agro: mapa dos talhões em Áreas de Cultivo (#233) e localização do talhão (#231).
 * --------------------------------------------------------------------------
 * - Lê GET /api/mapa (dados reais da propriedade) e desenha um SVG simples:
 *   polígono ou ponto de cada talhão localizado e um marcador por ocorrência.
 *   Sem biblioteca e sem camada de mapa: só o que o produtor cadastrou.
 * - Talhão sem localização não é desenhado: vai para a lista "Sem localização".
 * - O modal "Localização" grava com PUT/DELETE /api/talhoes/{id}/localizacao.
 *   Validação, área e centro são calculados no servidor; a tela só mostra o
 *   resultado depois da confirmação.
 * - Não usa o AgroLocal: localização e mapa vêm sempre da API.
 */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const API_BASE = window.location.origin;
  const SVG_NS = "http://www.w3.org/2000/svg";
  const LARGURA = 320;
  const ALTURA = 220;
  const MARGEM = 0.08;
  const CAIXA_MINIMA = 0.002; // graus em volta de um único ponto

  const state = {
    mapa: null,        // última resposta de GET /api/mapa
    selecionado: null, // id do talhão aberto no painel
    editando: null,    // talhão aberto no modal
  };
  let consulta = 0;

  function toast(type, title, description) {
    if (window.AgroToast) window.AgroToast.show({ type, title, description });
  }

  async function api(path, method = "GET", body) {
    const token = sessionStorage.getItem("agro.sessionToken");
    if (!token) throw new Error("Entre novamente para acessar o mapa.");

    const response = await fetch(API_BASE + path, {
      method,
      headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
      body: body ? JSON.stringify(body) : undefined,
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

  const numero = (valor, casas = 2) => Number(valor).toLocaleString("pt-BR", { minimumFractionDigits: 0, maximumFractionDigits: casas });
  const coordenada = (valor) => Number(valor).toFixed(6);
  const semAcento = (texto) => String(texto || "").normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
  const classeCultura = (cultura) => "crop-" + (semAcento(cultura) || "vazio");
  const classePrioridade = (prioridade) => "geo-marker-" + (semAcento(prioridade) || "baixa");

  function svg(tag, atributos = {}) {
    const el = document.createElementNS(SVG_NS, tag);
    Object.entries(atributos).forEach(([nome, valor]) => el.setAttribute(nome, String(valor)));
    return el;
  }

  function osm(lat, lng) {
    return `https://www.openstreetmap.org/?mlat=${coordenada(lat)}&mlon=${coordenada(lng)}#map=16/${coordenada(lat)}/${coordenada(lng)}`;
  }

  // Área calculada pelo servidor e a diferença para os hectares digitados no cadastro.
  function textoArea(area, hectares) {
    if (area == null) return "Ponto: sem área calculada (use um polígono para medir).";
    const diferenca = Math.round((area - Number(hectares || 0)) * 100) / 100;
    let comparacao = "igual aos hectares cadastrados";
    if (diferenca > 0) comparacao = `${numero(diferenca)} ha a mais que os ${numero(hectares)} ha cadastrados`;
    if (diferenca < 0) comparacao = `${numero(-diferenca)} ha a menos que os ${numero(hectares)} ha cadastrados`;
    return `Área calculada: ${numero(area)} ha (${comparacao}).`;
  }

  /* ---------------------------------------------------------------------- */
  /* Desenho                                                                */
  /* ---------------------------------------------------------------------- */

  // Projeção equirretangular dentro dos limites, com margem e correção pelo cos da latitude média.
  function projetor(limites) {
    let { latMin, latMax, lngMin, lngMax } = limites;
    if (latMax - latMin < 2 * CAIXA_MINIMA) {
      const meio = (latMin + latMax) / 2;
      latMin = meio - CAIXA_MINIMA; latMax = meio + CAIXA_MINIMA;
    }
    if (lngMax - lngMin < 2 * CAIXA_MINIMA) {
      const meio = (lngMin + lngMax) / 2;
      lngMin = meio - CAIXA_MINIMA; lngMax = meio + CAIXA_MINIMA;
    }
    const folgaLat = (latMax - latMin) * MARGEM;
    const folgaLng = (lngMax - lngMin) * MARGEM;
    latMin -= folgaLat; latMax += folgaLat; lngMin -= folgaLng; lngMax += folgaLng;

    const correcao = Math.cos(((latMin + latMax) / 2) * Math.PI / 180);
    const larguraReal = (lngMax - lngMin) * correcao;
    const alturaReal = latMax - latMin;
    const escala = Math.min(LARGURA / larguraReal, ALTURA / alturaReal);
    const sobraX = (LARGURA - larguraReal * escala) / 2;
    const sobraY = (ALTURA - alturaReal * escala) / 2;

    return (lat, lng) => [
      Math.round((sobraX + (lng - lngMin) * correcao * escala) * 10) / 10,
      Math.round((sobraY + (latMax - lat) * escala) * 10) / 10,
    ];
  }

  function desenhar(mapa) {
    const area = $("[data-geo-map]");
    area.replaceChildren();
    if (!mapa.limites || !mapa.talhoes.length) return;

    const projetar = projetor(mapa.limites);
    const formas = svg("g", { class: "geo-shapes" });
    const rotulos = svg("g", { class: "geo-labels" });
    const marcadores = svg("g", { class: "geo-markers" });

    mapa.talhoes.forEach((talhao) => {
      const pontos = talhao.localizacao.pontos.map((p) => projetar(p.lat, p.lng));
      const [cx, cy] = projetar(talhao.centro.lat, talhao.centro.lng);
      const comum = {
        class: `plot-shape geo-plot ${classeCultura(talhao.cultura)}`,
        "data-geo-plot": talhao.id, tabindex: 0, role: "button",
        "aria-label": `${talhao.nome}: ${talhao.cultura}, ${numero(talhao.hectares)} ha`,
      };
      const forma = talhao.localizacao.tipo === "poligono"
        ? svg("polygon", { ...comum, points: pontos.map((p) => p.join(",")).join(" ") })
        : svg("circle", { ...comum, cx, cy, r: 7 });
      if (talhao.id === state.selecionado) forma.classList.add("is-selected");
      const dica = svg("title"); dica.textContent = talhao.nome; forma.append(dica);
      formas.append(forma);

      const rotulo = svg("text", { x: cx, y: talhao.localizacao.tipo === "poligono" ? cy - 9 : cy - 11, class: "plot-shape-label geo-label", "text-anchor": "middle" });
      rotulo.textContent = talhao.nome;
      rotulos.append(rotulo);

      // Ocorrências sem posição própria ficam no centro, espalhadas em volta para não se sobreporem.
      const noCentro = talhao.ocorrencias.filter((o) => o.posicao === "talhao");
      talhao.ocorrencias.forEach((ocorrencia) => {
        let [x, y] = projetar(ocorrencia.lat, ocorrencia.lng);
        const indice = noCentro.indexOf(ocorrencia);
        if (indice >= 0 && noCentro.length > 1) {
          const angulo = (2 * Math.PI * indice) / noCentro.length;
          x = Math.round((x + 8 * Math.cos(angulo)) * 10) / 10;
          y = Math.round((y + 8 * Math.sin(angulo)) * 10) / 10;
        }
        const link = svg("a", {
          href: `ocorrencias.html?talhaoId=${encodeURIComponent(talhao.id)}&ocorrenciaId=${encodeURIComponent(ocorrencia.id)}`,
          class: "geo-occurrence", "data-geo-occurrence": ocorrencia.id,
          "aria-label": `Ocorrência ${ocorrencia.titulo} (${ocorrencia.prioridade}) em ${talhao.nome}`,
        });
        const marca = svg("circle", { cx: x, cy: y, r: 4, class: `geo-marker ${classePrioridade(ocorrencia.prioridade)}` });
        const titulo = svg("title");
        titulo.textContent = `${ocorrencia.titulo} · ${ocorrencia.prioridade}${ocorrencia.posicao === "talhao" ? " · no talhão" : ""}`;
        link.append(titulo, marca);
        marcadores.append(link);
      });
    });

    area.append(formas, rotulos, marcadores);
  }

  function listarSemLocalizacao(mapa) {
    const lista = $("[data-geo-missing]");
    if (!mapa.semLocalizacao.length) {
      const vazio = document.createElement("li");
      vazio.className = "geo-missing-empty";
      vazio.textContent = "Todos os talhões têm localização.";
      lista.replaceChildren(vazio);
      return;
    }
    lista.replaceChildren(...mapa.semLocalizacao.map((talhao) => {
      const item = document.createElement("li");
      const texto = document.createElement("div");
      const nome = document.createElement("span");
      const meta = document.createElement("span");
      const botao = document.createElement("button");
      item.className = "geo-missing-item";
      nome.className = "geo-missing-name";
      nome.textContent = talhao.nome;
      meta.className = "geo-missing-meta";
      meta.textContent = `${talhao.cultura} · ${numero(talhao.hectares)} ha · ${talhao.ocorrenciasAbertas} ocorrência(s) aberta(s)`;
      botao.type = "button";
      botao.className = "btn btn-outline geo-missing-btn";
      botao.dataset.geoEdit = talhao.id;
      botao.textContent = "Definir localização";
      botao.setAttribute("aria-label", `Definir localização de ${talhao.nome}`);
      texto.append(nome, meta);
      item.append(texto, botao);
      return item;
    }));
  }

  function mostrarPainel(id) {
    const painel = $("[data-geo-panel]");
    const talhao = state.mapa?.talhoes.find((t) => t.id === id);
    state.selecionado = talhao ? id : null;
    $$("[data-geo-plot]").forEach((forma) => forma.classList.toggle("is-selected", forma.dataset.geoPlot === state.selecionado));
    if (!talhao) { painel.hidden = true; return; }

    const abertas = talhao.ocorrencias.filter((o) => o.status !== "RESOLVIDA").length;
    const dados = [
      ["Cultura", talhao.cultura],
      ["Hectares cadastrados", `${numero(talhao.hectares)} ha`],
      ["Localização", talhao.localizacao.tipo === "poligono" ? `Polígono com ${talhao.localizacao.pontos.length} vértices` : "Ponto"],
      ["Área calculada", textoArea(talhao.areaCalculadaHectares, talhao.hectares)],
      ["Ocorrências abertas", String(abertas)],
    ];
    $("[data-geo-panel-title]").textContent = talhao.nome;
    $("[data-geo-panel-meta]").replaceChildren(...dados.flatMap(([rotulo, valor]) => {
      const dt = document.createElement("dt");
      const dd = document.createElement("dd");
      dt.textContent = rotulo;
      dd.textContent = valor;
      return [dt, dd];
    }));
    $("[data-geo-panel-occurrences]").href = `ocorrencias.html?talhaoId=${encodeURIComponent(talhao.id)}`;
    $("[data-geo-panel-osm]").href = osm(talhao.centro.lat, talhao.centro.lng);
    $("[data-geo-panel-edit]").dataset.geoEdit = talhao.id;
    painel.hidden = false;
  }

  function mensagem(texto, erro = false) {
    $("[data-geo-message]").textContent = texto;
    $("[data-geo-retry]").hidden = !erro;
  }

  // Uma resposta antiga nunca substitui a mais nova.
  async function carregar() {
    const numeroDaConsulta = ++consulta;
    mensagem("Carregando mapa…");
    try {
      const mapa = await api("/api/mapa");
      if (numeroDaConsulta !== consulta) return null;
      state.mapa = mapa;
      desenhar(mapa);
      listarSemLocalizacao(mapa);
      const gerais = mapa.ocorrenciasSemTalhao ? ` ${mapa.ocorrenciasSemTalhao} ocorrência(s) geral(is) da propriedade não aparecem no mapa.` : "";
      if (!mapa.talhoes.length) mensagem("Nenhum talhão com localização. Defina a localização na lista ao lado." + gerais);
      else mensagem(`${mapa.talhoes.length} talhão(ões) no mapa. Toque em um talhão para ver os detalhes.` + gerais);
      mostrarPainel(state.selecionado);
      return mapa;
    } catch (error) {
      if (numeroDaConsulta !== consulta) return null;
      state.mapa = null;
      $("[data-geo-map]").replaceChildren();
      $("[data-geo-missing]").replaceChildren();
      $("[data-geo-panel]").hidden = true;
      mensagem("Não foi possível carregar o mapa. " + error.message, true);
      return null;
    }
  }

  /* ---------------------------------------------------------------------- */
  /* Modal de localização                                                   */
  /* ---------------------------------------------------------------------- */
  const modal = () => $("[data-modal='geo-form']");
  const formulario = () => $("[data-geo-form]");

  function abrirModal() {
    modal().classList.add("is-open");
    modal().setAttribute("aria-hidden", "false");
  }

  function fecharModal() {
    modal().classList.remove("is-open");
    modal().setAttribute("aria-hidden", "true");
    state.editando = null;
  }

  function limparErros(form) {
    $$(".field-error", form).forEach((el) => { el.textContent = ""; });
    $$("[aria-invalid]", form).forEach((el) => el.removeAttribute("aria-invalid"));
  }

  function erroNoCampo(form, campo, texto) {
    const destino = $(`[data-error-for="${campo}"]`, form);
    if (destino) destino.textContent = texto;
    const controles = campo === "pontos"
      ? [form.elements.namedItem(form.elements.namedItem("tipo").value === "ponto" ? "lat" : "pontos")]
      : [form.elements.namedItem(campo)];
    controles.filter(Boolean).forEach((controle) => controle.setAttribute("aria-invalid", "true"));
  }

  function escolherTipo(form, tipo) {
    form.elements.namedItem("tipo").value = tipo;
    $$("[data-geo-type]", form).forEach((botao) => botao.setAttribute("aria-pressed", String(botao.dataset.geoType === tipo)));
    $("[data-geo-point-fields]", form).hidden = tipo !== "ponto";
    $("[data-geo-polygon-fields]", form).hidden = tipo !== "poligono";
  }

  function talhaoPorId(id) {
    const mapa = state.mapa;
    if (!mapa) return null;
    return mapa.talhoes.find((t) => t.id === id) || mapa.semLocalizacao.find((t) => t.id === id) || null;
  }

  async function editar(id) {
    let talhao = talhaoPorId(id);
    if (!talhao) {
      await carregar(); // talhão recém-criado: busca o mapa de novo antes de abrir
      talhao = talhaoPorId(id);
    }
    if (!talhao) {
      toast("error", "Talhão não encontrado", "Atualize a página e tente novamente.");
      return;
    }

    const form = formulario();
    state.editando = talhao;
    form.reset();
    limparErros(form);
    $("#geo-form-title").textContent = "Localização: " + talhao.nome;
    const local = talhao.localizacao;
    escolherTipo(form, local?.tipo || "ponto");
    if (local?.tipo === "ponto") {
      form.elements.namedItem("lat").value = local.pontos[0].lat;
      form.elements.namedItem("lng").value = local.pontos[0].lng;
    } else if (local?.tipo === "poligono") {
      form.elements.namedItem("pontos").value = local.pontos.map((p) => `${coordenada(p.lat)}, ${coordenada(p.lng)}`).join("\n");
    }
    const area = $("[data-geo-area]", form);
    area.hidden = !local;
    area.textContent = local ? "Salva: " + textoArea(talhao.areaCalculadaHectares, talhao.hectares) : "";
    $("[data-geo-remove]", form).hidden = !local;
    abrirModal();
    $("[data-geo-type][aria-pressed='true']", form)?.focus();
  }

  // Lê o formulário no formato do contrato: {tipo, pontos: [{lat, lng}]}. Só confere o formato;
  // as regras (faixas, vértices, lados cruzados) são do servidor.
  function lerFormulario(form) {
    const tipo = form.elements.namedItem("tipo").value;
    if (tipo === "ponto") {
      const lat = form.elements.namedItem("lat").value.trim();
      const lng = form.elements.namedItem("lng").value.trim();
      if (!lat || !lng || !Number.isFinite(Number(lat)) || !Number.isFinite(Number(lng))) {
        return { erro: "Informe a latitude e a longitude do ponto." };
      }
      return { corpo: { tipo, pontos: [{ lat: Number(lat), lng: Number(lng) }] } };
    }

    const linhas = form.elements.namedItem("pontos").value.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
    if (!linhas.length) return { erro: "Informe os vértices do polígono, um por linha." };
    const pontos = [];
    for (let i = 0; i < linhas.length; i += 1) {
      const partes = linhas[i].split(/[\s,;]+/).filter(Boolean);
      const lat = Number(partes[0]);
      const lng = Number(partes[1]);
      if (partes.length !== 2 || !Number.isFinite(lat) || !Number.isFinite(lng)) {
        return { erro: `Linha ${i + 1}: use o formato -22.905000, -47.061000.` };
      }
      pontos.push({ lat, lng });
    }
    return { corpo: { tipo, pontos } };
  }

  function ocupado(form, valor) {
    if (valor) form.setAttribute("aria-busy", "true");
    else form.removeAttribute("aria-busy");
    $$("button", form).forEach((botao) => { botao.disabled = valor; });
  }

  async function salvar(event) {
    event.preventDefault();
    const form = event.currentTarget; // guardado antes do primeiro await
    if (form.getAttribute("aria-busy") === "true" || !state.editando) return;
    limparErros(form);

    const lido = lerFormulario(form);
    if (lido.erro) {
      erroNoCampo(form, "pontos", lido.erro);
      return;
    }

    const talhao = state.editando;
    ocupado(form, true);
    let salvo;
    try {
      salvo = await api(`/api/talhoes/${encodeURIComponent(talhao.id)}/localizacao`, "PUT", lido.corpo);
    } catch (error) {
      Object.entries(error.fields).forEach(([campo, texto]) => erroNoCampo(form, campo, texto));
      $("[data-geo-form-error]", form).textContent = error.message;
      ocupado(form, false);
      return;
    }
    ocupado(form, false);
    fecharModal();
    toast("success", "Localização salva", `${salvo.nome}: ${textoArea(salvo.areaCalculadaHectares, salvo.hectares)}`);
    state.selecionado = salvo.id;
    await carregar();
  }

  async function remover(event) {
    const botao = event.currentTarget; // guardado antes do primeiro await
    const form = botao.closest("form");
    const talhao = state.editando;
    if (!talhao || form.getAttribute("aria-busy") === "true") return;
    limparErros(form);
    ocupado(form, true);
    try {
      await api(`/api/talhoes/${encodeURIComponent(talhao.id)}/localizacao`, "DELETE");
    } catch (error) {
      $("[data-geo-form-error]", form).textContent = error.message;
      ocupado(form, false);
      return;
    }
    ocupado(form, false);
    fecharModal();
    toast("success", "Localização removida", `${talhao.nome} saiu do mapa.`);
    if (state.selecionado === talhao.id) state.selecionado = null;
    await carregar();
  }

  function usarMinhaLocalizacao(event) {
    const form = event.currentTarget.closest("form");
    if (!navigator.geolocation) {
      erroNoCampo(form, "pontos", "Este navegador não informa a localização. Digite a latitude e a longitude.");
      return;
    }
    navigator.geolocation.getCurrentPosition((posicao) => {
      form.elements.namedItem("lat").value = posicao.coords.latitude.toFixed(6);
      form.elements.namedItem("lng").value = posicao.coords.longitude.toFixed(6);
    }, () => erroNoCampo(form, "pontos", "Não foi possível obter sua localização. Digite a latitude e a longitude."),
    { enableHighAccuracy: true, timeout: 10000 });
  }

  /* ---------------------------------------------------------------------- */
  /* Eventos                                                                */
  /* ---------------------------------------------------------------------- */
  function init() {
    const area = $("[data-geo-map]");
    const form = formulario();
    if (!area || !form) return;

    area.addEventListener("click", (event) => {
      const forma = event.target.closest("[data-geo-plot]");
      if (forma) mostrarPainel(forma.dataset.geoPlot);
    });
    area.addEventListener("keydown", (event) => {
      const forma = event.target.closest("[data-geo-plot]");
      if (forma && (event.key === "Enter" || event.key === " ")) {
        event.preventDefault();
        mostrarPainel(forma.dataset.geoPlot);
      }
    });
    $("[data-geo-panel-close]").addEventListener("click", () => mostrarPainel(null));
    $("[data-geo-retry]").addEventListener("click", () => carregar());

    // Botões "Localização" do menu da tabela, "Definir localização" e "Editar localização".
    document.addEventListener("click", (event) => {
      const botao = event.target.closest("[data-geo-edit]");
      if (botao && botao.dataset.geoEdit) editar(botao.dataset.geoEdit);
    });

    $$("[data-geo-type]", form).forEach((botao) => botao.addEventListener("click", () => {
      escolherTipo(form, botao.dataset.geoType);
      limparErros(form);
    }));
    $("[data-geo-locate]", form).addEventListener("click", usarMinhaLocalizacao);
    $("[data-geo-remove]", form).addEventListener("click", remover);
    $$("[data-close-modal]", modal()).forEach((botao) => botao.addEventListener("click", fecharModal));
    modal().addEventListener("click", (event) => { if (event.target === modal()) fecharModal(); });
    form.addEventListener("submit", salvar);

    // Cadastro ou edição de talhão na tabela (nome, cultura, hectares) muda o mapa: recarrega.
    const corpo = $("[data-plots-table] tbody");
    let espera;
    if (corpo) {
      new MutationObserver(() => {
        clearTimeout(espera);
        espera = setTimeout(() => carregar(), 300);
      }).observe(corpo, { childList: true, subtree: true });
    }

    carregar();
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
