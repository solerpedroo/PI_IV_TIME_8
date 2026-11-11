/* Tela Culturas — cadastro do catálogo por propriedade. */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const base = window.location.origin;

  async function api(path, options = {}) {
    const token = sessionStorage.getItem("agro.sessionToken");
    if (!token) throw new Error("Sessão ausente.");
    const response = await fetch(`${base}${path}`, {
      ...options,
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json", ...(options.headers || {}) },
      signal: AbortSignal.timeout(15000),
    });
    const body = response.status === 204 ? {} : await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = new Error(body.error || "Operação não concluída.");
      error.fields = body.fields || {};
      throw error;
    }
    return body;
  }

  function esc(text) {
    return String(text ?? "").replace(/[&<>\"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[char]);
  }

  function toast(type, title, description) {
    window.AgroToast?.show({ type, title, description });
  }

  function renderTable(culturas) {
    const tbody = $("[data-cultures-table] tbody");
    if (!tbody) return;
    if (!culturas.length) {
      tbody.innerHTML = `<tr><td colspan="2"><p class="empty-hint">Nenhuma cultura cadastrada.</p></td></tr>`;
      return;
    }
    tbody.innerHTML = culturas.map((item) => `
      <tr data-culture-id="${esc(item.id)}">
        <td>${esc(item.nome)}</td>
        <td class="table-actions">
          <button type="button" class="btn btn-ghost btn-sm" data-remove-culture data-culture-id="${esc(item.id)}" aria-label="Excluir ${esc(item.nome)}">Excluir</button>
        </td>
      </tr>`).join("");
  }

  async function reload() {
    const culturas = await window.AgroCulturas.listar(true);
    renderTable(culturas);
  }

  function initForm() {
    const form = $("[data-culture-form]");
    if (!form) return;

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const nome = String(form.elements.nome.value || "").trim();
      const output = $("[data-error-for='nome']", form);
      if (output) output.textContent = "";
      if (nome.length < 2) {
        if (output) output.textContent = "Use pelo menos 2 caracteres.";
        return;
      }

      try {
        await api("/api/culturas", { method: "POST", body: JSON.stringify({ nome }) });
        window.AgroCulturas.limparCache();
        form.reset();
        await reload();
        toast("success", "Cultura cadastrada", `${nome} já pode ser usada em talhões e safras.`);
      } catch (error) {
        if (error.fields?.nome && output) output.textContent = error.fields.nome;
        else toast("error", "Não foi possível cadastrar", error.message);
      }
    });
  }

  function initTable() {
    document.addEventListener("click", async (event) => {
      const button = event.target.closest("[data-remove-culture]");
      if (!button) return;
      const id = button.dataset.cultureId;
      if (!id || !window.confirm("Excluir esta cultura? Só é permitido se não estiver em uso.")) return;

      try {
        await api(`/api/culturas/${encodeURIComponent(id)}`, { method: "DELETE" });
        window.AgroCulturas.limparCache();
        await reload();
        toast("success", "Cultura removida", "O catálogo foi atualizado.");
      } catch (error) {
        toast("error", "Não foi possível excluir", error.message);
      }
    });
  }

  function whenAuthenticated(run) {
    if (window.__agroAuthenticated) {
      run();
      return;
    }
    document.addEventListener("agro:authenticated", () => run(), { once: true });
  }

  async function init() {
    initForm();
    initTable();
    whenAuthenticated(async () => {
      try {
        await reload();
      } catch (error) {
        const hint = error.message === "Não encontrado."
          ? "Reinicie a API (compile + rodar.sh) para carregar a rota /api/culturas."
          : error.message;
        toast("error", "Culturas indisponíveis", hint);
      }
    });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
