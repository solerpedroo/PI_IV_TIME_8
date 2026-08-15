/**
 * Agro — interações das telas de autenticação
 * --------------------------------------------------------------------------
 * Responsabilidades:
 * - Validação client-side (obrigatórios, e-mail, senha, termos)
 * - Toggle de senha, medidor de força e máscara de telefone
 * - Navegação mock entre páginas (sem API real)
 * - Persistência leve via sessionStorage (e-mail / flash de sucesso)
 *
 * Quando a API existir, substitua a função `simulateRequest` por fetch real.
 */
(() => {
  "use strict";

  /* ------------------------------------------------------------------------ */
  /* Helpers DOM                                                              */
  /* ------------------------------------------------------------------------ */
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

  const STORAGE = {
    pendingEmail: "agro.pendingEmail",
    flashSuccess: "agro.flashSuccess",
  };

  const STRENGTH_LABELS = ["—", "Fraca", "Média", "Boa", "Forte"];

  /**
   * Exibe mensagem acessível no formulário (erro ou sucesso).
   */
  function showMessage(form, message, type = "error") {
    const box = $("[data-form-message]", form);
    if (!box) return;
    box.textContent = message;
    box.className = `form-message ${type} is-visible`;
    box.setAttribute("role", type === "error" ? "alert" : "status");
  }

  function clearMessage(form) {
    const box = $("[data-form-message]", form);
    if (!box) return;
    box.className = "form-message";
    box.removeAttribute("role");
    box.textContent = "";
  }

  function setError(input, message) {
    input.setAttribute("aria-invalid", "true");
    const group = input.closest(".input-group") || input.closest(".terms");
    const error = group?.querySelector(".field-error");
    if (error) error.textContent = message;
  }

  function clearError(input) {
    input.removeAttribute("aria-invalid");
    const group = input.closest(".input-group") || input.closest(".terms");
    const error = group?.querySelector(".field-error");
    if (error) error.textContent = "";
  }

  /**
   * Valida campos required (incluindo checkbox de termos).
   * @returns {HTMLElement|null} primeiro inválido
   */
  function validateRequired(form) {
    let firstInvalid = null;
    $$("[required]", form).forEach((input) => {
      clearError(input);
      const missing = input.type === "checkbox" ? !input.checked : !String(input.value || "").trim();
      if (missing) {
        setError(input, input.dataset.requiredMessage || "Preencha este campo.");
        firstInvalid ||= input;
      }
    });
    return firstInvalid;
  }

  /**
   * Avalia regras de força da senha (espelha o meter do pen).
   */
  function getPasswordChecks(value) {
    return {
      length: value.length >= 8,
      upper: /[A-ZÀ-Ý]/.test(value),
      number: /\d/.test(value),
      symbol: /[^A-Za-zÀ-ÿ\d]/.test(value),
    };
  }

  function passwordLevel(checks) {
    return Object.values(checks).filter(Boolean).length;
  }

  function setLoading(form, loading) {
    const button = $("[type=submit]", form);
    if (!button) return;
    button.disabled = loading;
    button.classList.toggle("is-loading", loading);
    button.dataset.originalLabel ||= button.textContent.trim();
    button.textContent = loading ? "Aguarde…" : button.dataset.originalLabel;
    form.setAttribute("aria-busy", String(loading));
  }

  /** Simulação de latência de rede — trocar por fetch quando houver API. */
  function simulateRequest(ms = 550) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  /* ------------------------------------------------------------------------ */
  /* Flash de sucesso (ex.: após redefinir senha)                             */
  /* ------------------------------------------------------------------------ */
  function consumeFlashMessage() {
    const flash = sessionStorage.getItem(STORAGE.flashSuccess);
    if (!flash) return;
    sessionStorage.removeItem(STORAGE.flashSuccess);
    const form = $("form[data-auth-form]");
    if (form) showMessage(form, flash, "success");
  }

  /* ------------------------------------------------------------------------ */
  /* Toggle de senha                                                          */
  /* ------------------------------------------------------------------------ */
  function initPasswordToggles() {
    $$("[data-password-toggle]").forEach((button) => {
      button.addEventListener("click", () => {
        const input = document.getElementById(button.dataset.passwordToggle);
        if (!input) return;
        const visible = input.type === "text";
        input.type = visible ? "password" : "text";
        const label = visible ? "Mostrar" : "Ocultar";
        const textNode = button.querySelector("[data-toggle-label]");
        if (textNode) textNode.textContent = label;
        else button.textContent = label;
        button.setAttribute("aria-label", `${label} senha`);
        button.setAttribute("aria-pressed", String(!visible));
      });
    });
  }

  /* ------------------------------------------------------------------------ */
  /* Medidor de força                                                         */
  /* ------------------------------------------------------------------------ */
  function initPasswordMeter() {
    const passwordInput = $("[data-password-meter]");
    if (!passwordInput) return;

    const meter = $("[data-meter]");
    if (!meter) return;

    const rules = $$("[data-rule]", meter);
    const label = $("[data-meter-label]", meter);

    const update = () => {
      const checks = getPasswordChecks(passwordInput.value);
      const level = passwordLevel(checks);
      meter.dataset.level = String(level);
      if (label) {
        label.dataset.level = String(level);
        label.textContent = STRENGTH_LABELS[level];
      }
      rules.forEach((rule) => {
        rule.classList.toggle("is-valid", Boolean(checks[rule.dataset.rule]));
      });
    };

    passwordInput.addEventListener("input", update);
    update();
  }

  /* ------------------------------------------------------------------------ */
  /* Máscara de telefone BR                                                   */
  /* ------------------------------------------------------------------------ */
  function maskPhone(value) {
    const digits = value.replace(/\D/g, "").slice(0, 11);
    if (digits.length <= 2) return digits.replace(/(\d{0,2})/, "($1");
    if (digits.length <= 6) return digits.replace(/(\d{2})(\d{0,4})/, "($1) $2");
    if (digits.length <= 10) return digits.replace(/(\d{2})(\d{4})(\d{0,4})/, "($1) $2-$3");
    return digits.replace(/(\d{2})(\d{5})(\d{0,4})/, "($1) $2-$3");
  }

  function initPhoneMask() {
    $$("[data-phone-mask]").forEach((input) => {
      input.addEventListener("input", () => {
        const caretEnd = input.selectionEnd === input.value.length;
        input.value = maskPhone(input.value);
        if (caretEnd) input.setSelectionRange(input.value.length, input.value.length);
      });
    });
  }

  /* ------------------------------------------------------------------------ */
  /* Confirmar e-mail: preenche placeholder                                   */
  /* ------------------------------------------------------------------------ */
  function initConfirmEmail() {
    const placeholder = $("#email-placeholder");
    if (!placeholder) return;

    const params = new URLSearchParams(window.location.search);
    const fromQuery = params.get("email");
    const fromStorage = sessionStorage.getItem(STORAGE.pendingEmail);
    const email = fromQuery || fromStorage;

    if (email) {
      placeholder.textContent = email;
      sessionStorage.setItem(STORAGE.pendingEmail, email);
    }
  }

  /* ------------------------------------------------------------------------ */
  /* Reenvio com cooldown                                                     */
  /* ------------------------------------------------------------------------ */
  function initResend() {
    const resend = $("[data-resend]");
    if (!resend) return;

    resend.addEventListener("click", async () => {
      if (resend.disabled) return;
      resend.dataset.originalLabel ||= resend.textContent.trim();
      resend.disabled = true;

      // Simula envio; depois aplica cooldown visual.
      await simulateRequest(400);

      let seconds = 30;
      resend.textContent = `Reenviar em ${seconds}s`;
      const timer = setInterval(() => {
        seconds -= 1;
        if (seconds <= 0) {
          clearInterval(timer);
          resend.disabled = false;
          resend.textContent = resend.dataset.originalLabel;
          return;
        }
        resend.textContent = `Reenviar em ${seconds}s`;
      }, 1000);
    });
  }

  /* ------------------------------------------------------------------------ */
  /* Formulários de autenticação                                              */
  /* ------------------------------------------------------------------------ */
  function initAuthForms() {
    $$("form[data-auth-form]").forEach((form) => {
      form.addEventListener("input", (event) => {
        const target = event.target;
        if (target.matches("input")) clearError(target);
        clearMessage(form);
        form.classList.remove("is-invalid");
      });

      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        clearMessage(form);
        form.classList.remove("is-invalid");

        const firstInvalid = validateRequired(form);
        if (firstInvalid) {
          form.classList.add("is-invalid");
          showMessage(form, "Revise os campos obrigatórios para continuar.");
          firstInvalid.focus();
          return;
        }

        const email = $("[type=email]", form);
        if (email && !email.validity.valid) {
          setError(email, "Digite um e-mail válido.");
          form.classList.add("is-invalid");
          showMessage(form, "Confira o formato do e-mail informado.");
          email.focus();
          return;
        }

        const password = $("[data-password]", form);
        const confirmation = $("[data-password-confirm]", form);
        const requiresStrong = form.hasAttribute("data-require-strong-password");

        if (password) {
          const checks = getPasswordChecks(password.value);
          if (requiresStrong && passwordLevel(checks) < 4) {
            setError(password, "Atenda a todas as regras de senha.");
            form.classList.add("is-invalid");
            showMessage(form, "Escolha uma senha que cumpra todas as regras.");
            password.focus();
            return;
          }
          if (!requiresStrong && password.value.length < 8) {
            setError(password, "A senha deve ter pelo menos 8 caracteres.");
            form.classList.add("is-invalid");
            showMessage(form, "Escolha uma senha mais segura.");
            password.focus();
            return;
          }
        }

        if (password && confirmation && password.value !== confirmation.value) {
          setError(confirmation, "As senhas não coincidem.");
          form.classList.add("is-invalid");
          showMessage(form, "Confirme a senha corretamente.");
          confirmation.focus();
          return;
        }

        setLoading(form, true);
        await simulateRequest();
        setLoading(form, false);

        // Persistência leve para a tela de confirmação de e-mail.
        if (email && form.dataset.persistEmail === "true") {
          sessionStorage.setItem(STORAGE.pendingEmail, email.value.trim());
        }

        const successRoute = form.dataset.successRoute;
        const flash = form.dataset.flashMessage;

        if (flash) {
          sessionStorage.setItem(STORAGE.flashSuccess, flash);
        }

        if (successRoute) {
          let url = successRoute;
          if (email && form.dataset.persistEmail === "true") {
            const sep = successRoute.includes("?") ? "&" : "?";
            url = `${successRoute}${sep}email=${encodeURIComponent(email.value.trim())}`;
          }
          window.location.href = url;
          return;
        }

        showMessage(form, form.dataset.successMessage || "Dados enviados com sucesso.", "success");
      });
    });
  }

  /* ------------------------------------------------------------------------ */
  /* Boot                                                                     */
  /* ------------------------------------------------------------------------ */
  function init() {
    initPasswordToggles();
    initPasswordMeter();
    initPhoneMask();
    initConfirmEmail();
    initResend();
    initAuthForms();
    consumeFlashMessage();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
