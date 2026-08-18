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

  function validateRequired(form) {
    let valid = true;
    $$('[required]', form).forEach((control) => {
      const empty = !control.value.trim();
      control.toggleAttribute("aria-invalid", empty);
      const error = control.parentElement.querySelector(".field-error");
      if (error) error.textContent = empty ? "Preencha este campo." : "";
      valid = valid && !empty;
    });
    return valid;
  }

  function initForms() {
    $$('[data-settings-form]').forEach((form) => form.addEventListener("submit", (event) => {
      event.preventDefault();
      if (!validateRequired(form)) {
        toast("error", "Não foi possível salvar", "Revise os campos obrigatórios.");
        return;
      }
      toast("success", "Alterações salvas", "As informações foram atualizadas nesta sessão.");
    }));

    $("[data-security-form]")?.addEventListener("submit", (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      const current = form.elements.current;
      const next = form.elements.new;
      const confirm = form.elements.confirm;
      [current, next, confirm].forEach((control) => { control.removeAttribute("aria-invalid"); control.parentElement.querySelector(".field-error").textContent = ""; });
      let invalid = false;
      if (!current.value) { current.setAttribute("aria-invalid", "true"); current.parentElement.querySelector(".field-error").textContent = "Informe sua senha atual."; invalid = true; }
      if (next.value.length < 8) { next.setAttribute("aria-invalid", "true"); next.parentElement.querySelector(".field-error").textContent = "Use pelo menos 8 caracteres."; invalid = true; }
      if (next.value !== confirm.value) { confirm.setAttribute("aria-invalid", "true"); confirm.parentElement.querySelector(".field-error").textContent = "As senhas não coincidem."; invalid = true; }
      if (invalid) { toast("error", "Senha não atualizada", "Revise os campos destacados."); return; }
      form.reset();
      toast("success", "Senha atualizada", "Sua senha foi alterada com sucesso nesta sessão.");
    });
  }

  function initPreferences() {
    $("[data-save-notifications]")?.addEventListener("click", () => {
      const enabled = $$('[data-preference]:checked').length;
      toast("success", "Preferências salvas", `${enabled} tipo${enabled === 1 ? "" : "s"} de alerta${enabled === 1 ? "" : "s"} ativado${enabled === 1 ? "" : "s"}.`);
    });
    $("[data-change-photo]")?.addEventListener("click", () => toast("info", "Alteração de foto", "A seleção de arquivo será conectada ao perfil quando a API estiver disponível."));
    $("[data-team-action]")?.addEventListener("click", () => toast("info", "Convite de colaborador", "O convite será enviado quando o gerenciamento de equipe estiver conectado."));
  }

  function initHarvestPicker() {
    const native = $("#property-harvest");
    if (!native || $("[data-open-harvest-modal]")) return;
    const enhancedRoot = native.closest(".select");
    enhancedRoot?.setAttribute("hidden", "true");
    const trigger = document.createElement("button");
    trigger.type = "button";
    trigger.className = "harvest-picker-trigger";
    trigger.dataset.openHarvestModal = "";
    trigger.setAttribute("aria-haspopup", "dialog");
    trigger.setAttribute("aria-controls", "harvest-form");
    trigger.innerHTML = '<span><strong data-harvest-label></strong></span><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>';
    enhancedRoot?.parentNode.insertBefore(trigger, enhancedRoot);
    const label = $("label[for='property-harvest-trigger']");
    if (label) label.htmlFor = trigger.id = "property-harvest-custom-trigger";
    const modal = document.createElement("div");
    modal.className = "modal-backdrop";
    modal.dataset.modal = "harvest-form";
    modal.setAttribute("aria-hidden", "true");
    modal.setAttribute("role", "presentation");
    modal.innerHTML = '<div class="modal harvest-modal" role="dialog" aria-modal="true" aria-labelledby="harvest-form-title"><div class="calendar-modal-header"><div><h2 id="harvest-form-title">Selecionar safra</h2><p>Escolha a safra principal da propriedade.</p></div><button class="icon-btn icon-btn--sm" type="button" data-harvest-close aria-label="Fechar seletor"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"/></svg></button></div><div class="harvest-options" data-harvest-options role="listbox" aria-label="Safras disponíveis"></div><div class="modal-actions"><button class="btn btn-outline" type="button" data-harvest-close>Cancelar</button><button class="btn btn-primary" type="button" data-confirm-harvest>Aplicar safra</button></div></div>';
    document.body.appendChild(modal);
    let pending = native.value;

    function syncLabel() {
      $("[data-harvest-label]", trigger).textContent = native.value || "Selecione a safra";
    }

    function renderOptions() {
      const options = $("[data-harvest-options]", modal);
      options.innerHTML = [...native.options].map((option) => `<button class="harvest-option ${option.value === pending ? "is-selected" : ""}" type="button" data-harvest-option="${option.value}" role="option" aria-selected="${option.value === pending}"><span><strong>${option.textContent}</strong><small>${option.value === "2025/26" ? "Safra em acompanhamento" : "Período disponível"}</small></span><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg></button>`).join("");
      $$('[data-harvest-option]', modal).forEach((option) => option.addEventListener("click", () => {
        pending = option.dataset.harvestOption;
        renderOptions();
      }));
    }

    function close() {
      modal.classList.remove("is-open");
      modal.setAttribute("aria-hidden", "true");
      trigger.focus();
    }

    trigger.addEventListener("click", () => {
      pending = native.value;
      renderOptions();
      modal.classList.add("is-open");
      modal.setAttribute("aria-hidden", "false");
    });
    $$('[data-harvest-close]', modal).forEach((button) => button.addEventListener("click", close));
    modal.addEventListener("click", (event) => { if (event.target === modal) close(); });
    $("[data-confirm-harvest]", modal).addEventListener("click", () => {
      native.value = pending;
      native.dispatchEvent(new Event("change", { bubbles: true }));
      syncLabel();
      close();
      toast("success", "Safra atualizada", `A propriedade agora acompanha a safra ${pending}.`);
    });
    native.addEventListener("change", syncLabel);
    syncLabel();
  }

  function init() {
    initTabs();
    initForms();
    initPreferences();
    initHarvestPicker();
  }

  document.addEventListener("DOMContentLoaded", init);
})();
