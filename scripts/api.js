/**
 * Agro — cliente HTTP da API Java (backend/servidor, porta 8080).
 * Validações/cálculos pesados vão ao ServerSocket (backend/serversocket, porta 3000) pelo servidor.
 * --------------------------------------------------------------------------
 * - Guarda a sessão (token + usuário) no sessionStorage da aba.
 * - `AgroApi.request` faz o fetch com JSON e "Authorization: Bearer".
 * - Sem sessão nada é chamado: as telas operacionais seguem no modo local
 *   (localStorage) e o modo demonstração continua funcionando sem servidor.
 * - Com sessão, preenche nome e propriedade no rodapé da sidebar e, se o
 *   token deixar de valer (401), volta para o login.
 *
 * Front servido pelo próprio servidor (porta 8080): mesma origem.
 * Front em outra porta (ex.: Live Server): usa http://localhost:8080 (CORS).
 * Para outro endereço, defina window.AGRO_API_BASE antes deste script.
 */
(() => {
  "use strict";

  const STORAGE = {
    token: "agro.session.token",
    user: "agro.session.user",
  };

  const BASE = window.AGRO_API_BASE ?? (window.location.port === "8080" ? "" : "http://localhost:8080");

  function read(key) {
    try {
      return sessionStorage.getItem(key);
    } catch (error) {
      return null;
    }
  }

  function token() {
    return read(STORAGE.token);
  }

  function user() {
    try {
      return JSON.parse(read(STORAGE.user) || "null");
    } catch (error) {
      return null;
    }
  }

  function saveSession(newToken, usuario) {
    try {
      sessionStorage.setItem(STORAGE.token, newToken);
      sessionStorage.setItem(STORAGE.user, JSON.stringify(usuario || null));
    } catch (error) {
      // Sem sessionStorage não há como manter o login entre páginas.
    }
  }

  function clearSession() {
    try {
      sessionStorage.removeItem(STORAGE.token);
      sessionStorage.removeItem(STORAGE.user);
    } catch (error) {
      // Nada a limpar.
    }
  }

  /**
   * Chama a API e nunca lança: devolve { ok, status, data }.
   * status 0 = servidor fora do ar ou bloqueado.
   */
  async function request(method, path, body) {
    const headers = {};
    const current = token();
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (current) headers.Authorization = `Bearer ${current}`;

    let response;
    try {
      response = await fetch(BASE + path, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch (error) {
      return { ok: false, status: 0, data: { error: "Network error" } };
    }

    let data = {};
    try {
      const text = await response.text();
      if (text) data = JSON.parse(text);
    } catch (error) {
      data = {};
    }

    // Token expirado ou servidor reiniciado: a sessão local não vale mais.
    if (response.status === 401 && current && !path.startsWith("/api/auth/")) {
      clearSession();
      window.location.href = "login.html";
    }

    return { ok: response.ok, status: response.status, data };
  }

  /** Nome e propriedade reais no rodapé da sidebar (telas logadas). */
  async function fillSidebarUser() {
    const name = document.querySelector(".sidebar-user-name");
    if (!name || !token()) return;

    const result = await request("GET", "/api/me");
    if (!result.ok) return;

    const { usuario, propriedade } = result.data;
    name.textContent = usuario.name;

    const property = document.querySelector(".sidebar-user-property");
    if (property && propriedade) property.textContent = propriedade.farm;

    const avatar = document.querySelector(".sidebar-avatar");
    if (avatar && usuario.name) {
      avatar.textContent = usuario.name.split(/\s+/).slice(0, 2).map((part) => part.charAt(0).toUpperCase()).join("");
    }
  }

  window.AgroApi = { request, token, user, saveSession, clearSession };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", fillSidebarUser);
  } else {
    fillSidebarUser();
  }
})();
