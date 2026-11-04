/* Miguel — RA: 25003110 */
/* Autor secundário: Pedro Henrique Contardi Soler - 25005592 */
/* Interações da tela de Configurações: abas, formulários e preferências. */
(() => {
  "use strict";

  /* Os seletores locais evitam dependências externas e seguem os módulos atuais. */
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

  function toast(type, title, description) {
    window.AgroToast?.show({ type, title, description });
  }

  function showTab(name, updateHash = true) {
    const buttons = $$('[data-settings-tab]');
    const panels = $$('[data-settings-panel]');
    buttons.forEach((button) => button.classList.toggle("is-active", button.dataset.settingsTab === name));
    panels.forEach((panel) => { panel.hidden = panel.dataset.settingsPanel !== name; });
    if (updateHash) history.replaceState(null, "", `#settings-${name}`);
  }

  function initTabs() {
    $$('[data-settings-tab]').forEach((button) => button.addEventListener("click", (event) => {
      event.preventDefault();
      showTab(button.dataset.settingsTab);
    }));
    const initial = location.hash.replace("#settings-", "");
    showTab(["profile", "property", "notifications", "security", "team", "plan"].includes(initial) ? initial : "profile", false);
  }

  // Dados pessoais, preferências e senha são tratados em perfil-api.js.

  function init() {
    initTabs();
  }

  document.addEventListener("DOMContentLoaded", init);
})();
