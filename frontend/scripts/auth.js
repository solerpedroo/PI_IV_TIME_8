/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

/**
 * Agro — interações das telas de autenticação
 * --------------------------------------------------------------------------
 * Responsabilidades:
 * - Validação client-side (obrigatórios, e-mail, senha, termos)
 * - Toggle de senha, medidor de força e máscara de telefone
 * - Integração com a API local de autenticação
 * - Persistência leve da sessão, do e-mail e das mensagens de sucesso
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
    sessionToken: "agro.sessionToken",
    sessionUser: "agro.sessionUser",
  };

  // Quando o front é servido pelo Java, usa a mesma origem. Em um servidor
  // estático local, usa a porta padrão do servidor Java para facilitar o desenvolvimento.
  const API_BASE = window.location.port === "8080"
    ? window.location.origin
    : "http://127.0.0.1:8080";

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

  /**
   * Faz uma chamada JSON e transforma erros da API em uma exceção tratável
   * pela tela. Não envia o token por padrão porque as rotas deste arquivo são
   * públicas; a sessão é usada pelo shell das telas autenticadas.
   */
  async function requestApi(path, body) {
    let response;

    try {
      response = await fetch(`${API_BASE}${path}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
    } catch {
      throw new Error("Não foi possível conectar ao servidor. Verifique se ele está em execução.");
    }

    const data = response.status === 204 ? {} : await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = new Error(data.error || "Não foi possível concluir a solicitação.");
      error.fields = data.fields || {};
      error.retryAfter = data.retryAfter || response.headers.get("Retry-After");
      throw error;
    }

    return data;
  }

  /** Mostra os erros de campo que o servidor validou. */
  function showApiErrors(form, error) {
    Object.entries(error.fields || {}).forEach(([field, message]) => {
      const input = form.elements.namedItem(field);
      if (input instanceof HTMLElement) setError(input, message);
    });
    const message = error.message === "Email confirmation required"
      ? "Confirme seu e-mail antes de entrar. Use o código enviado para sua caixa de entrada."
      : (error.message || "Não foi possível concluir a solicitação.");
    showMessage(form, message);
  }

  /* ------------------------------------------------------------------------ */
  /* Flash de sucesso (ex.: após redefinir senha ou logout)                   */
  /* ------------------------------------------------------------------------ */
  function consumeFlashMessage() {
    const flash = sessionStorage.getItem(STORAGE.flashSuccess);
    if (!flash) return;
    sessionStorage.removeItem(STORAGE.flashSuccess);

    // Prefer toast quando disponível; fallback para mensagem no formulário.
    if (window.AgroToast) {
      window.AgroToast.show({
        type: "success",
        title: "Tudo certo",
        description: flash,
      });
      return;
    }

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

    // Um link de e-mail pode informar o código pela query string.
    const token = params.get("token");
    const tokenInput = $("[name=token]");
    if (token && tokenInput) tokenInput.value = token;
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

      const email = sessionStorage.getItem(STORAGE.pendingEmail);
      if (!email) {
        resend.disabled = false;
        showMessage($("[data-confirm-email-form]"), "Informe um e-mail antes de solicitar o reenvio.");
        return;
      }

      try {
        await requestApi("/api/auth/resend-confirmation", { email });
      } catch (error) {
        resend.disabled = false;
        const seconds = Number(error.retryAfter || 0);
        showMessage($("[data-confirm-email-form]"), seconds
          ? `Aguarde ${seconds}s antes de solicitar outro código.`
          : error.message);
        return;
      }

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

  /** Confirma o código recebido pelo e-mail antes de liberar a conta. */
  function initConfirmEmailForm() {
    const form = $("[data-confirm-email-form]");
    if (!form) return;

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const token = String(form.elements.token.value || "").trim();
      if (!token) {
        setError(form.elements.token, "Informe o código recebido.");
        showMessage(form, "Informe o código de confirmação.");
        return;
      }

      setLoading(form, true);
      try {
        await requestApi("/api/auth/confirm-email", { token });
        sessionStorage.setItem(STORAGE.flashSuccess, "E-mail confirmado. Agora você já pode entrar.");
        window.location.href = "login.html";
      } catch (error) {
        showApiErrors(form, error);
      } finally {
        setLoading(form, false);
      }
    });
  }

  /** Determina a rota pública a partir dos campos que cada formulário possui. */
  function authRoute(form) {
    if (form.elements.name) return "/api/auth/register";
    if (form.elements.token) return "/api/auth/reset-password";
    if (form.querySelector("[data-password]")) return "/api/auth/login";
    return "/api/auth/forgot-password";
  }

  /** Cria o corpo exatamente com os nomes de campos aceitos pela API Java. */
  function authPayload(form) {
    const data = Object.fromEntries(new FormData(form).entries());

    // Checkboxes não marcados não aparecem no FormData.
    if (form.elements.terms) data.terms = form.elements.terms.checked;
    return data;
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

        const route = authRoute(form);
        let result;

        setLoading(form, true);
        try {
          result = await requestApi(route, authPayload(form));
        } catch (error) {
          // Permite que o link "Confirmar e-mail" já saiba para qual conta
          // pedir novo código depois de um login bloqueado por confirmação.
          if (route === "/api/auth/login" && error.message === "Email confirmation required" && email) {
            sessionStorage.setItem(STORAGE.pendingEmail, email.value.trim());
          }
          showApiErrors(form, error);
          return;
        } finally {
          setLoading(form, false);
        }

        // Persistência leve para a tela de confirmação de e-mail.
        if (email && form.dataset.persistEmail === "true") {
          sessionStorage.setItem(STORAGE.pendingEmail, email.value.trim());
        }

        // Somente o login devolve uma sessão. O token fica no sessionStorage
        // para não sobreviver ao fechamento do navegador.
        if (route === "/api/auth/login") {
          sessionStorage.setItem(STORAGE.sessionToken, result.token);
          sessionStorage.setItem(STORAGE.sessionUser, JSON.stringify(result.usuario));
        }

        // A resposta de recuperação não revela se o e-mail existe. Mantemos
        // a mensagem neutra e levamos o usuário à tela que aceita o código.
        if (route === "/api/auth/forgot-password") {
          sessionStorage.setItem(STORAGE.pendingEmail, email.value.trim());
          sessionStorage.setItem(
            STORAGE.flashSuccess,
            "Se o e-mail estiver cadastrado, enviamos um código de redefinição."
          );
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
    initConfirmEmailForm();
    initAuthForms();
    consumeFlashMessage();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
