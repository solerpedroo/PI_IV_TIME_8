// Miguel — RA: 25003110
// Autor secundário: Pedro Henrique Contardi Soler — RA: 25005592
/*
  Conecta a aba Propriedade à API Java quando há uma sessão autenticada.
  A troca de contexto usa AgroContextoPropriedade (sidebar + este select).
*/
(() => {
  "use strict";

  const form = document.querySelector("#property-form");
  if (!form) return;

  const token = () => sessionStorage.getItem("agro.sessionToken");
  const base = window.location.origin;
  const toast = (type, title, description) => window.AgroToast?.show({ type, title, description });
  const ctx = () => window.AgroContextoPropriedade;

  const request = async (path, options = {}) => {
    const response = await fetch(base + path, {
      ...options,
      signal: AbortSignal.timeout(15000),
      headers: { Authorization: `Bearer ${token()}`, "Content-Type": "application/json", ...(options.headers || {}) },
    });
    const body = response.status === 204 ? null : await response.json().catch(() => ({}));
    if (!response.ok) throw Object.assign(new Error(body.error || "Não foi possível concluir a operação."), { body });
    return body;
  };

  let currentId = null;
  let properties = [];
  const selector = document.querySelector("#property-context");

  function fill(property) {
    currentId = property.id;
    form.elements.farm.value = property.nome || "";
    form.elements.area.value = property.areaHectares || "";
    form.elements.location.value = property.localizacao || "";
    form.elements.city.value = property.municipio || "";
    form.elements.harvest.value = property.safraAtual || "";
    form.elements.harvest.dispatchEvent(new Event("change", { bubbles: true }));
  }

  function aplicarModoPropriedade() {
    const user = JSON.parse(sessionStorage.getItem("agro.sessionUser") || "null");
    const colaborador = user?.papel === "colaborador";
    const notice = document.querySelector("[data-property-collab-notice]");
    const nova = document.querySelector("[data-new-property]");
    const salvar = document.querySelector('button[form="property-form"]');
    if (notice) notice.hidden = !colaborador;
    if (nova) nova.hidden = colaborador;
    if (salvar) salvar.hidden = colaborador;
    for (const name of ["farm", "area", "location", "city", "harvest"]) {
      const controle = form.elements[name];
      if (controle) controle.disabled = colaborador;
    }
    if (selector) selector.disabled = false;
  }

  function renderSelector() {
    if (!ctx()) return;
    const user = JSON.parse(sessionStorage.getItem("agro.sessionUser") || "null");
    const mostrar = ctx().aplicarVisibilidadeSeletor(properties, { user });
    if (!mostrar || !selector) return;
    ctx().preencherSelect(selector, properties, currentId);
    if (selector.dataset.selectEnhanced !== "true" && window.AgroSelect) window.AgroSelect.enhance(selector);
  }

  async function loadHarvestPeriods(valorAtual = "") {
    if (!window.AgroPeriodosSafra || !token()) return window.AgroPeriodosSafra?.fallbackLocal?.() || null;
    window.AgroPeriodosSafra.limparCache();
    const data = await window.AgroPeriodosSafra.garantirSelect(
      form.elements.harvest,
      valorAtual || form.elements.harvest.value,
    );
    if (form.elements.harvest.dataset.selectEnhanced === "true" && window.AgroSelect) {
      window.AgroSelect.enhance(form.elements.harvest);
    }
    document.dispatchEvent(new CustomEvent("agro:periodos-safra", { detail: data }));
    return data;
  }

  function whenAuthenticated(run) {
    if (window.__agroAuthenticated) {
      run();
      return;
    }
    document.addEventListener("agro:authenticated", () => run(), { once: true });
  }

  async function load() {
    if (!token() || !ctx()) return;
    properties = await ctx().listar();
    const selected = properties.find((property) => property.selecionada) || properties[0];
    const periodos = await loadHarvestPeriods(selected?.safraAtual || "").catch(
      () => window.AgroPeriodosSafra?.fallbackLocal() || null,
    );
    if (selected) {
      fill(selected);
    } else if (periodos?.padrao && !form.elements.harvest.value) {
      form.elements.harvest.value = periodos.padrao;
      form.elements.harvest.dispatchEvent(new Event("change", { bubbles: true }));
    }
    if (selected || properties.length) renderSelector();
    aplicarModoPropriedade();
    document.dispatchEvent(new CustomEvent("agro:properties-loaded", { detail: { properties } }));
    document.dispatchEvent(new CustomEvent("agro:properties-context-updated", { detail: { properties } }));
  }

  document.querySelector("[data-new-property]")?.addEventListener("click", () => {
    const user = JSON.parse(sessionStorage.getItem("agro.sessionUser") || "null");
    if (user?.papel === "colaborador") return;
    currentId = null;
    form.reset();
    for (const field of ["farm", "area", "location", "city", "harvest"]) form.elements[field].value = "";
    window.AgroPeriodosSafra?.carregar().then((data) => {
      if (data?.padrao) {
        form.elements.harvest.value = data.padrao;
        form.elements.harvest.dispatchEvent(new Event("change", { bubbles: true }));
      }
    }).catch(() => {});
    renderSelector();
    form.elements.farm.focus();
  });

  form.addEventListener("submit", async (event) => {
    if (!token()) return;
    const user = JSON.parse(sessionStorage.getItem("agro.sessionUser") || "null");
    if (user?.papel === "colaborador") {
      event.preventDefault();
      toast("warning", "Somente leitura", "Colaboradores não alteram os dados cadastrais da propriedade.");
      return;
    }
    event.preventDefault();
    event.stopImmediatePropagation();
    if (form.getAttribute("aria-busy") === "true") return;
    form.setAttribute("aria-busy", "true");
    form.querySelectorAll("[aria-invalid]").forEach((control) => control.removeAttribute("aria-invalid"));
    const payload = {
      nome: form.elements.farm.value.trim(),
      areaHectares: Number(form.elements.area.value),
      localizacao: form.elements.location.value.trim(),
      municipio: form.elements.city.value.trim(),
      safraAtual: form.elements.harvest.value,
    };
    try {
      const property = currentId
        ? await request(`/api/propriedades/${encodeURIComponent(currentId)}`, { method: "PUT", body: JSON.stringify(payload) })
        : await request("/api/propriedades", { method: "POST", body: JSON.stringify(payload) });
      currentId = property.id;
      await load();
      toast("success", "Propriedade salva", "As alterações foram persistidas no servidor.");
    } catch (error) {
      const fields = error.body?.fields || {};
      Object.entries(fields).forEach(([name, message]) => {
        const control = { nome: "farm", areaHectares: "area", localizacao: "location", municipio: "city", safraAtual: "harvest" }[name];
        if (control && form.elements[control]) form.elements[control].setAttribute("aria-invalid", "true");
        toast("error", "Dados inválidos", message);
      });
      if (!Object.keys(fields).length) toast("error", "Não foi possível salvar", error.message);
    } finally {
      form.removeAttribute("aria-busy");
    }
  }, true);

  function boot() {
    load().catch((error) => toast("error", "Propriedades indisponíveis", error.message));
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => whenAuthenticated(boot));
  } else {
    whenAuthenticated(boot);
  }
})();