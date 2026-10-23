/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

/* Mantem os modulos operacionais ligados aos talhoes confirmados pela API. */
(() => {
  "use strict";
  const base = window.location.port === "8080" ? window.location.origin : "http://127.0.0.1:8080";
  const token = () => sessionStorage.getItem("agro.sessionToken");
  let talhoes = [];

  async function request(path, options = {}) {
    if (!token()) throw new Error("Sessão ausente.");
    const response = await fetch(base + path, {
      ...options,
      headers: { Authorization: "Bearer " + token(), "Content-Type": "application/json", ...(options.headers || {}) },
    });
    if (!response.ok) throw new Error("API indisponível.");
    return response.json();
  }

  function preencherSelect(select) {
    const atual = select.value;
    select.replaceChildren(new Option(select.dataset.placeholder || "Selecione o talhão", ""),
      ...talhoes.map((talhao) => new Option(talhao.nome, talhao.nome)));
    const escolhido = talhoes.find((talhao) => talhao.nome === atual);
    if (escolhido) select.value = escolhido.nome;
    select.dispatchEvent(new Event("change"));
  }

  async function iniciar() {
    try {
      talhoes = await request("/api/talhoes");
      window.AgroLocal.save("plots", talhoes.map((item) => ({id: item.id, plot: item.nome,
        crop: item.cultura.toLowerCase(), hectares: item.hectares, status: item.status})));
    } catch (error) {
      talhoes = window.AgroLocal.read("plots", []).map((item) => ({id: item.id, nome: item.plot}));
      window.AgroToast?.show({type: "error", title: "Talhões sem conexão", description: error.message + " Exibindo somente o cache desta propriedade."});
    }
    document.querySelectorAll("select[name=talhao], select[name=plot], select[name=activity-plot]").forEach(preencherSelect);
    document.dispatchEvent(new Event("agro:plots-loaded"));
  }

  // O módulo emite este evento apenas depois de confirmar a persistência.
  // Um submit inválido ou uma falha de armazenamento nunca gera histórico.
  document.addEventListener("agro:operation-saved", async ({detail}) => {
    const talhao = talhoes.find((item) => item.nome === detail.talhao);
    if (!talhao) return;
    try {
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
