/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

/* Carrega os talhões da propriedade ativa no servidor. A lista não é misturada
   ao armazenamento demonstrativo: com sessão, a origem é exclusivamente a API. */
(() => {
  "use strict";
  const token = sessionStorage.getItem("agro.sessionToken");
  if (!token) return;

  const escape = (value) => String(value).replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));
  const badge = (status) => status === "Ativo" ? "badge-success" : status === "Irrigação pendente" ? "badge-warning" : "badge-neutral";

  async function load() {
    const response = await fetch("/api/talhoes", { headers: { Authorization: `Bearer ${token}` } });
    if (!response.ok) throw new Error("Não foi possível carregar os talhões da propriedade selecionada.");
    const talhoes = await response.json();
    const tbody = document.querySelector("[data-plots-table] tbody");
    if (!tbody) return;
    tbody.replaceChildren(...talhoes.map((talhao) => {
      const row = document.createElement("tr");
      row.dataset.id = talhao.id;
      row.dataset.plot = talhao.nome;
      row.dataset.crop = talhao.cultura.toLowerCase();
      row.dataset.hectares = talhao.hectares;
      row.dataset.status = talhao.status;
      row.innerHTML = `<td class="col-plot-name">${escape(talhao.nome)}</td><td><span class="badge badge-neutral">${escape(talhao.cultura)}</span></td><td>${Number(talhao.hectares).toLocaleString("pt-BR")} ha</td><td><span class="badge ${badge(talhao.status)}">${escape(talhao.status)}</span></td><td class="col-muted">Servidor</td><td class="col-actions"></td>`;
      return row;
    }));
    document.querySelector("[data-plots-search]")?.dispatchEvent(new Event("input"));
  }

  document.addEventListener("DOMContentLoaded", () => load().catch((error) => window.AgroToast?.show({ type: "error", title: "Talhões indisponíveis", description: error.message })));
})();
