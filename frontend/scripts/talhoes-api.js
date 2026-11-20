/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

/* Mantem os modulos operacionais ligados aos talhoes confirmados pela API. */
(() => {
  "use strict";
  const base = window.location.origin;
  /* Token da sessão usado nas consultas de talhão. Sem ele a tela não chama a API. */
  const token = () => sessionStorage.getItem("agro.sessionToken");
  let talhoes = [];

  /* Consulta a API dos talhões. Se a sessão ou o servidor falhar, os seletores
     da tela ficam sem opções novas e um aviso explica o que aconteceu. */
  async function request(path, options = {}) {
    if (!token()) throw new Error("Sessão ausente.");
    const response = await fetch(base + path, {
      ...options,
      signal: AbortSignal.timeout(15000),
      headers: { Authorization: "Bearer " + token(), "Content-Type": "application/json", ...(options.headers || {}) },
    });
    if (!response.ok) throw new Error("API indisponível.");
    return response.json();
  }

  /* Recoloca as opções do seletor com os talhões carregados e, se a escolha
     anterior ainda existir, deixa ela selecionada na tela. */
  function preencherSelect(select) {
    const atual = select.value;
    select.replaceChildren(new Option(select.dataset.placeholder || "Selecione o talhão", ""),
      ...talhoes.map((talhao) => new Option(talhao.nome, talhao.nome)));
    const escolhido = talhoes.find((talhao) => talhao.nome === atual);
    if (escolhido) select.value = escolhido.nome;
    select.dispatchEvent(new Event("change"));
  }

  /* Ao abrir a página, busca os talhões confirmados e preenche cada seletor
     de talhão (atividades, custos e demais módulos da tela). */
  async function iniciar() {
    try {
      talhoes = await request("/api/talhoes");
      window.AgroLocal.save("plots", talhoes.map((item) => ({id: item.id, plot: item.nome,
        crop: item.cultura.toLowerCase(), hectares: item.hectares, status: item.status,
        safra: item.safra || ""})));
    } catch (error) {
      talhoes = [];
      window.AgroToast?.show({type: "error", title: "Talhões sem conexão", description: error.message + " Recarregue para consultar os talhões."});
    }
    document.querySelectorAll("select[name=talhao], select[name=plot], select[name=activity-plot]").forEach(preencherSelect);
    document.dispatchEvent(new Event("agro:plots-loaded"));
  }

  // O módulo emite este evento apenas depois de confirmar a persistência.
  // Um submit inválido ou uma falha de armazenamento nunca gera histórico.
  // Na tela, o histórico do talhão só entra depois que a operação foi salva.
  document.addEventListener("agro:operation-saved", async ({detail}) => {
    if (!detail?.talhao) return;
    const talhao = talhoes.find((item) => item.nome === detail.talhao);
    if (!talhao) return;
    try {
      /*
        Corpo JSON do POST /api/talhoes/{id}/historico. Não é o cadastro do talhão.
        O servidor grava um evento em historicos_talhoes com esta forma, mais
        propriedadeId, talhaoId e criadoEm que ele mesmo preenche:

        { "tipo": "atividade" | "ocorrencia" | "custo", "descricao": "texto da operação" }

        detail vem do evento agro:operation-saved, disparado depois que a tela
        já confirmou o salvamento da atividade, ocorrência ou custo.
      */
      await request("/api/talhoes/" + encodeURIComponent(talhao.id) + "/historico", {
        method: "POST", body: JSON.stringify({tipo: detail.tipo, descricao: detail.descricao}),
      });
    } catch {
      // Não repetimos automaticamente: uma resposta perdida pode já ter sido gravada.
      window.AgroToast?.show({type: "error", title: "Histórico não confirmado",
        description: "O registro foi salvo neste navegador, mas o envio do histórico não foi confirmado pelo servidor."});
    }
  });

  document.addEventListener("DOMContentLoaded", iniciar);
})();
