/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
/* Dados de perfil pertencem à API. sessionStorage guarda somente a sessão e
   sua projeção pública; senhas, códigos e preferências não vão ao localStorage. */
(() => {
  "use strict";
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const base = location.port === "8080" ? location.origin : "http://127.0.0.1:8080";
  let user = null;

  const toast = (type, title, description) => window.AgroToast?.show({type, title, description});
  async function api(path, options = {}) {
    const response = await fetch(base + path, {...options, signal: AbortSignal.timeout(15000),
      headers: {Authorization: "Bearer " + sessionStorage.getItem("agro.sessionToken"), "Content-Type": "application/json"}});
    const data = response.status === 204 ? null : await response.json();
    if (!response.ok) throw Object.assign(new Error(data.error || "Não foi possível concluir."), {fields: data.fields || {}});
    return data;
  }

  function errors(form, fields = {}) {
    $$(".field-error", form).forEach(el => { el.textContent = ""; });
    $$("[aria-invalid]", form).forEach(el => el.removeAttribute("aria-invalid"));
    Object.entries(fields).forEach(([key, text]) => {
      const control = form.elements.namedItem(key);
      control?.setAttribute("aria-invalid", "true");
      const target = control?.closest(".input-group")?.querySelector(".field-error");
      if (target) target.textContent = text;
    });
    $("[aria-invalid]", form)?.focus();
  }

  function busy(form, value) {
    form.setAttribute("aria-busy", String(value));
    const buttons = $$('button[type="submit"], button:not([type])', form);
    if (form.id) buttons.push(...$$(`button[form='${form.id}']`));
    buttons.forEach(button => { button.disabled = value; });
  }

  function updateEmailPassword() {
    const changed = user && $("#profile-email").value.trim().toLowerCase() !== user.email;
    $("[data-email-password]").hidden = !changed;
    if (!changed) $("#profile-current").value = "";
  }

  function unavailable(message) {
    user = null;
    $("[data-profile-message]").textContent = message;
    $("[data-profile-retry]").hidden = false;
    for (const name of ["name", "email", "phone", "senhaAtual"]) $("#profile-form").elements.namedItem(name).value = "";
    $$('button[form="profile-form"], [data-save-notifications], [data-default-property-form] button, [data-security-form] button').forEach(button => { button.disabled = true; });
    $$("[data-preference]").forEach(control => { control.disabled = true; control.checked = false; });
    $("[data-default-property-form] select").disabled = true;
    $("[data-email-pending]").hidden = true;
  }

  async function defaultOptions() {
    const properties = await api("/api/propriedades");
    const select = $("[data-default-property-form] select");
    select.replaceChildren(...properties.map(p => new Option(p.nome || p.farm, p.id)));
    select.value = user.propriedadePadraoId;
    select.disabled = false;
  }

  async function load() {
    try {
      const data = await api("/api/perfil");
      user = data.usuario;
      const form = $("#profile-form");
      for (const name of ["name", "email", "phone"]) form.elements.namedItem(name).value = user[name] || "";
      form.elements.namedItem("senhaAtual").value = "";
      $$("[data-preference]").forEach(control => { control.checked = data.preferencias[control.dataset.preference]; control.disabled = false; });
      $("[data-profile-message]").textContent = "Perfil carregado do servidor.";
      $("[data-profile-retry]").hidden = true;
      $$('button[form="profile-form"], [data-save-notifications], [data-default-property-form] button, [data-security-form] button').forEach(button => { button.disabled = false; });
      $("[data-email-pending]").hidden = !user.emailPendente;
      $("[data-email-pending-message]").textContent = user.emailPendente
        ? `Aguardando confirmação de ${user.emailPendente}. Seu e-mail atual é ${user.email}. O código vale por 30 minutos.` : "";
      $(".profile-large-avatar").textContent = user.name.split(/\s+/).slice(0, 2).map(part => part[0]).join("");
      updateEmailPassword();
      // Mantém a propriedade ativa da sessão; não troca para a padrão durante reload.
      const session = JSON.parse(sessionStorage.getItem("agro.sessionUser") || "{}");
      sessionStorage.setItem("agro.sessionUser", JSON.stringify({...user, propriedadeId: session.propriedadeId || user.propriedadeId}));
      document.dispatchEvent(new CustomEvent("agro:profile-updated", {detail: user}));
      try { await defaultOptions(); }
      catch (error) { $("[data-default-property-form] select").disabled = true; $("[data-default-property-form] button").disabled = true; toast("error", "Propriedades indisponíveis", error.message); }
    } catch (error) { unavailable("Perfil indisponível. " + error.message); throw error; }
  }

  async function save(form, action) {
    if (!user || form.getAttribute("aria-busy") === "true") return;
    errors(form); busy(form, true);
    let confirmed = false;
    try {
      const result = await action();
      confirmed = true;
      await load();
      toast(result?.aviso ? "warning" : "success", result?.aviso ? "Envio não confirmado" : "Dados salvos",
        result?.aviso || result?.message || "Alterações confirmadas pelo servidor.");
    } catch (error) {
      if (!confirmed) errors(form, error.fields);
      toast("error", confirmed ? "Gravação confirmada" : "Não foi possível salvar", confirmed
        ? "Os dados foram salvos, mas a atualização da tela falhou. Tente consultar novamente."
        : error.message);
    } finally { busy(form, false); if (!user) unavailable("Perfil indisponível. Tente consultar novamente."); }
  }

  function leave(message) {
    sessionStorage.removeItem("agro.sessionToken");
    sessionStorage.removeItem("agro.sessionUser");
    sessionStorage.setItem("agro.flashSuccess", message);
    location.href = "login.html";
  }

  async function security(form, endpoint, payload) {
    if (!user || form.getAttribute("aria-busy") === "true") return;
    errors(form); busy(form, true);
    try {
      const data = await api(endpoint, {method: endpoint.endsWith("senha") ? "PUT" : "POST", body: JSON.stringify(payload)});
      form.reset(); leave(data.message);
    } catch (error) { errors(form, error.fields); toast("error", "Operação não confirmada", error.message); }
    finally { busy(form, false); }
  }

  function init() {
    unavailable("Carregando perfil…");
    $("[data-profile-retry]").addEventListener("click", () => load().catch(() => {}));
    $("#profile-email").addEventListener("input", updateEmailPassword);
    $("#profile-form").addEventListener("submit", e => {
      e.preventDefault(); const form = e.currentTarget;
      const data = Object.fromEntries(["name", "email", "phone", "senhaAtual"].map(key => [key, form.elements.namedItem(key).value]));
      save(form, () => api("/api/perfil", {method: "PUT", body: JSON.stringify(data)}));
    });
    $("[data-save-notifications]").addEventListener("click", async e => {
      const button = e.currentTarget; if (!user || button.disabled) return; button.disabled = true;
      try {
        const data = Object.fromEntries($$("[data-preference]").map(control => [control.dataset.preference, control.checked]));
        await api("/api/perfil/preferencias", {method: "PUT", body: JSON.stringify(data)});
        toast("success", "Preferências salvas", "Seleção persistida na sua conta.");
      } catch (error) { toast("error", "Preferências não confirmadas", error.message); }
      finally { button.disabled = !user; }
    });
    $("[data-default-property-form]").addEventListener("submit", e => {
      e.preventDefault(); const form = e.currentTarget;
      save(form, async () => {
        await api("/api/perfil/propriedade-padrao", {method: "PUT", body: JSON.stringify({propriedadeId: form.elements.propriedadeId.value})});
        return {message: "Propriedade padrão salva. Será usada no próximo login."};
      });
    });
    $("[data-security-form]").addEventListener("submit", e => {
      e.preventDefault(); const form = e.currentTarget;
      security(form, "/api/perfil/senha", Object.fromEntries(["current", "new", "confirm"].map(key => [key, form.elements.namedItem(key).value])));
    });
    $("[data-email-confirm-form]").addEventListener("submit", e => {
      e.preventDefault(); security(e.currentTarget, "/api/perfil/email/confirmar", {codigo: e.currentTarget.elements.codigo.value});
    });
    $("[data-email-resend]").addEventListener("click", async e => {
      const button = e.currentTarget; if (button.disabled) return; button.disabled = true;
      try { const data = await api("/api/perfil/email/reenviar", {method: "POST", body: "{}"}); toast(data.aviso ? "warning" : "success", "Confirmação de e-mail", data.aviso || data.message); }
      catch (error) { toast("error", "Reenvio não confirmado", error.message); }
      finally { button.disabled = false; }
    });
    document.addEventListener("agro:properties-loaded", () => { if (user) defaultOptions().catch(error => toast("error", "Propriedades indisponíveis", error.message)); });
    load().catch(() => {});
  }
  document.addEventListener("DOMContentLoaded", init);
})();
