/* Catálogo de culturas da propriedade (GET /api/culturas). */
(() => {
  "use strict";

  const base = window.location.origin;
  let cache = null;

  async function listar(force = false) {
    const token = sessionStorage.getItem("agro.sessionToken");
    if (!token) throw new Error("Sessão ausente.");
    if (cache && !force) return cache;

    const response = await fetch(`${base}/api/culturas`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: AbortSignal.timeout(15000),
    });
    const body = await response.json().catch(() => []);
    if (!response.ok) throw new Error(body?.error || "Não foi possível carregar as culturas.");

    cache = Array.isArray(body) ? body : [];
    return cache;
  }

  function nomes(culturas) {
    return culturas.map((item) => item.nome);
  }

  function preencherSelect(select, culturas, placeholder = "Selecione a cultura") {
    if (!select) return;
    const atual = select.value;
    select.replaceChildren(
      new Option(placeholder, ""),
      ...culturas.map((item) => new Option(item.nome, item.nome)),
    );
    if (culturas.some((item) => item.nome === atual)) select.value = atual;
    select.dispatchEvent(new Event("change"));
  }

  function limparCache() {
    cache = null;
  }

  window.AgroCulturas = { listar, nomes, preencherSelect, limparCache };
})();
