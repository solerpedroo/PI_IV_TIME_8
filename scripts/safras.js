// Miguel — RA: 25003110
// Tela de acompanhamento: usa somente as rotas persistentes do servidor, sem estado fictício no navegador.
(() => {
  "use strict";
  const $ = (s, r = document) => r.querySelector(s);
  const state = { safras: [], talhoes: [] };
  const base = window.location.port === "8080" ? window.location.origin : "http://127.0.0.1:8080";
  const token = () => sessionStorage.getItem("agro.sessionToken");
  const api = async (path, options = {}) => {
    if (!token()) throw new Error("Sessão ausente.");
    const res = await fetch(base + path, { ...options, headers: { Authorization: "Bearer " + token(), "Content-Type": "application/json", ...(options.headers || {}) } });
    const body = res.status === 204 ? null : await res.json();
    if (!res.ok) throw body;
    return body;
  };
  const toast = (type, title, description) => window.AgroToast?.show({ type, title, description });
  const modal = $("[data-modal='safra-form']");
  const abrir = () => { modal.classList.add("is-open"); modal.setAttribute("aria-hidden", "false"); };
  const fechar = () => { modal.classList.remove("is-open"); modal.setAttribute("aria-hidden", "true"); };
  const esc = (v) => String(v).replace(/[&<>"]/g, c => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;" })[c]);
  function render() {
    const etapa = $("[data-filter-stage]").value;
    const lista = state.safras.filter(s => !etapa || s.etapa === etapa);
    $("[data-safras-list]").innerHTML = lista.map(s => `<article class="list-item"><div><strong>${esc(s.nome)}</strong><p>${esc(s.cultura)} · ${s.dataInicio} a ${s.dataFim} · ${s.areaHectares} ha</p><span class="badge badge-neutral">${esc(s.etapa)}</span></div><div><a class="btn btn-outline" href="atividades.html?safra=${encodeURIComponent(s.id)}">Atividades</a> <a class="btn btn-outline" href="ocorrencias.html?safra=${encodeURIComponent(s.id)}">Ocorrências</a> <a class="btn btn-outline" href="custos.html?safra=${encodeURIComponent(s.id)}">Custos</a> ${s.etapa !== "Finalização" ? `<button class="btn btn-primary" data-advance="${s.id}">Avançar</button>` : `<a class="btn btn-outline" href="${base}/api/safras/${s.id}/historico">Histórico</a>`}</div></article>`).join("");
    $("[data-safras-empty]").hidden = lista.length > 0;
  }
  async function carregar() { state.safras = await api("/api/safras"); state.talhoes = await api("/api/talhoes"); $("[name='talhoes']").innerHTML = state.talhoes.map(t => `<option value="${t.id}">${esc(t.nome)}</option>`).join(""); render(); }
  $("[data-new-safra]").addEventListener("click", abrir); $("[data-close-safra]").addEventListener("click", fechar); $("[data-filter-stage]").addEventListener("change", render);
  $("[data-safra-form]").addEventListener("submit", async e => { e.preventDefault(); const f = e.currentTarget; const data = Object.fromEntries(new FormData(f)); data.talhoes = [...f.talhoes.selectedOptions].map(o => o.value); try { await api("/api/safras", { method:"POST", body:JSON.stringify(data) }); fechar(); f.reset(); await carregar(); toast("success", "Safra criada", "O ciclo foi salvo em planejamento."); } catch (err) { toast("error", "Não foi possível criar", err?.error || "Revise os dados informados."); } });
  $("[data-safras-list]").addEventListener("click", async e => { const b = e.target.closest("[data-advance]"); if (!b) return; try { await api(`/api/safras/${b.dataset.advance}/avancar`, { method:"POST", body:"{}" }); await carregar(); toast("success", "Etapa atualizada", "O ciclo avançou para a próxima etapa."); } catch (err) { toast("error", "Não foi possível avançar", err?.error || "Recarregue e tente novamente."); } });
  carregar().catch(() => toast("error", "Safras indisponíveis", "Inicie o servidor e faça login para acompanhar os ciclos."));
})();
