/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */
/* Autor secundário: Pedro Henrique Contardi Soler - 25005592 */

/*
  Conecta a aba Propriedade à API Java quando há uma sessão autenticada.
  Sem token, a tela mantém o modo demonstrativo já existente, sem simular gravação
  remota. A seleção é enviada ao servidor, portanto Áreas de Cultivo receberá o
  contexto correto mesmo após recarregar a página.
*/
(() => {
  "use strict";

  const form = document.querySelector("#property-form");
  if (!form) return;

  const token = () => sessionStorage.getItem("agro.sessionToken");
  const base = window.location.port === "8080" ? window.location.origin : "http://127.0.0.1:8080";
  const toast = (type, title, description) => window.AgroToast?.show({ type, title, description });
  const request = async (path, options = {}) => {
    const response = await fetch(base + path, {
      ...options,
      headers: { Authorization: `Bearer ${token()}`, "Content-Type": "application/json", ...(options.headers || {}) }
    });
    const body = response.status === 204 ? null : await response.json().catch(() => ({}));
    if (!response.ok) throw Object.assign(new Error(body.error || "Não foi possível concluir a operação."), { body });
    return body;
  };

  let currentId = null;
  let properties = [];
  const selector = document.createElement("select");
  selector.className = "property-context-select";
  selector.setAttribute("aria-label", "Propriedade ativa");
  form.closest(".settings-card")?.querySelector(".settings-card-header div")?.append(selector);

  function fill(property) {
    currentId = property.id;
    form.elements.farm.value = property.nome || "";
    form.elements.area.value = property.areaHectares || "";
    form.elements.location.value = property.localizacao || "";
    form.elements.city.value = property.municipio || "";
    form.elements.harvest.value = property.safraAtual || "";
    form.elements.harvest.dispatchEvent(new Event("change", { bubbles: true }));
  }

  function renderSelector() {
    selector.replaceChildren(...properties.map((property) => {
      const option = new Option(property.nome, property.id, false, property.id === currentId);
      return option;
    }));
  }

  async function load() {
    if (!token()) return;
    properties = await request("/api/propriedades");
    const selected = properties.find((property) => property.selecionada) || properties[0];
    if (selected) { fill(selected); renderSelector(); }
    document.dispatchEvent(new Event("agro:properties-loaded"));
  }

  selector.addEventListener("change", async () => {
    selector.disabled = true;
    try {
      const selected = await request(`/api/propriedades/${encodeURIComponent(selector.value)}/selecionar`, { method: "POST", body: "{}" });
      const user = JSON.parse(sessionStorage.getItem("agro.sessionUser"));
      user.propriedadeId = selected.propriedadeId;
      sessionStorage.setItem("agro.sessionUser", JSON.stringify(user));
      // Reinicializa também os módulos locais no namespace da nova propriedade.
      window.location.reload();
    } catch (error) {
      selector.value = currentId;
      selector.disabled = false;
      toast("error", "Não foi possível trocar", error.message);
    }
  });

  // Limpa o formulário para que o próximo salvamento crie uma nova propriedade.
  document.querySelector("[data-new-property]")?.addEventListener("click", () => {
    currentId = null;
    form.reset();
    form.elements.farm.focus();
  });

  // Captura o submit antes do manipulador local para não anunciar persistência remota
  // quando a API devolve uma validação ou erro de autorização.
  form.addEventListener("submit", async (event) => {
    if (!token()) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    if (form.getAttribute("aria-busy") === "true") return;
    form.setAttribute("aria-busy", "true");
    form.querySelectorAll("[aria-invalid]").forEach(control => control.removeAttribute("aria-invalid"));
    const payload = {
      nome: form.elements.farm.value.trim(), areaHectares: Number(form.elements.area.value), localizacao: form.elements.location.value.trim(),
      municipio: form.elements.city.value.trim(), safraAtual: form.elements.harvest.value
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

  document.addEventListener("DOMContentLoaded", () => load().catch((error) => toast("error", "Propriedades indisponíveis", error.message)));
})();
