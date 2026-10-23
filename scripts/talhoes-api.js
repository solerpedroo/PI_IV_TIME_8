/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

/* Mantem os modulos operacionais ligados aos talhoes confirmados pela API. */
(() => {
  "use strict";
  const base = window.location.port === "8080" ? window.location.origin : "http://127.0.0.1:8080";
  const token = () => sessionStorage.getItem("agro.sessionToken");
  let talhoes = [];
  const pendentes = new WeakMap();

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
      // A carga persistente só substitui os mocks quando há talhões reais.
      // Assim, os dados iniciais dos três módulos continuam compatíveis em
      // uma propriedade recém-criada, conforme o contrato desta tela.
      if (!talhoes.length) return;
      document.querySelectorAll("select[name=talhao], select[name=plot]").forEach(preencherSelect);
    } catch {
      // Sem sessao, os modulos mantem o estado visual atual ate o login ocorrer.
    }
  }

  // Registra operacoes novas no historico do talhao depois da validacao do modulo.
  document.addEventListener("submit", (event) => {
    const form = event.target;
    const select = form.elements.talhao || form.elements.plot;
    if (!select || !select.value) return;
    const tipo = form.matches("[data-activity-form]") ? "atividade" : form.matches("[data-occ-form]") ? "ocorrencia" : form.matches("[data-cost-form]") ? "custo" : null;
    const descricao = String(form.elements.nome?.value || form.elements.titulo?.value || form.elements.description?.value || "").trim();
    const talhao = talhoes.find((item) => item.nome === select.value);
    if (!tipo || !descricao || !talhao) return;
    pendentes.set(form, { tipo, descricao, talhaoId: talhao.id });
  }, true);

  document.addEventListener("submit", (event) => {
    const form = event.target;
    const evento = pendentes.get(form);
    if (!evento) return;
    setTimeout(async () => {
      if (form.querySelector("[aria-invalid=true]")) return;
      if (!token()) return;
      try { await request("/api/talhoes/" + evento.talhaoId + "/historico", { method: "POST", body: JSON.stringify({ tipo: evento.tipo, descricao: evento.descricao }) }); }
      catch { /* A operacao local continua; o proximo envio pode ser repetido pelo usuario. */ }
    }, 0);
  });

  document.addEventListener("DOMContentLoaded", iniciar);
})();
