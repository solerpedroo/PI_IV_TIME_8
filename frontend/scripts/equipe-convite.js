// Miguel — RA: 25003110
(() => {
  "use strict";

  const $ = (selector) => document.querySelector(selector);
  const base = window.location.origin;
  const params = new URLSearchParams(window.location.search);
  const token = params.get("token");

  function toast(type, title, description) {
    window.AgroToast?.show({ type, title, description });
  }

  function sessao() {
    return sessionStorage.getItem("agro.sessionToken");
  }

  async function api(path, options = {}) {
    const headers = { "Content-Type": "application/json" };
    const t = sessao();
    if (t) headers.Authorization = "Bearer " + t;
    const response = await fetch(base + path, { ...options, headers, signal: AbortSignal.timeout(15000) });
    const data = response.status === 204 ? null : await response.json().catch(() => ({}));
    if (!response.ok) throw Object.assign(new Error(data.error || "Não foi possível concluir."), { status: response.status, fields: data.fields || {} });
    return data;
  }

  function renderResumo(info) {
    const dl = $("[data-convite-resumo]");
  if (!dl) return;
    dl.hidden = false;
    dl.innerHTML =
      `<div><dt>Propriedade</dt><dd>${info.propriedade || "—"}</dd></div>` +
      `<div><dt>Convidado por</dt><dd>${info.convidadoPor || "—"}</dd></div>` +
      `<div><dt>E-mail do convite</dt><dd>${info.emailConvidado || "—"}</dd></div>` +
      (info.cargo ? `<div><dt>Função</dt><dd>${info.cargo}</dd></div>` : "");
  }

  async function carregar() {
    const status = $("[data-convite-status]");
    if (!token) {
      status.textContent = "Link inválido: token ausente.";
      return;
    }

    try {
      const info = await api(`/api/equipe/convite?token=${encodeURIComponent(token)}`);
      if (info.expirado) {
        status.textContent = "Este convite expirou. Peça um novo link ao proprietário.";
        return;
      }
      status.textContent = "Convite válido. Entre com o e-mail indicado abaixo e aceite para colaborar.";
      renderResumo(info);

      const logado = Boolean(sessao());
      $("[data-convite-login]").hidden = logado;
      $("[data-convite-aceitar]").hidden = !logado;

      if (!logado) {
        sessionStorage.setItem("agro.convitePendente", token);
        $("[data-convite-login]").href = `login.html?redirect=${encodeURIComponent(`equipe-convite.html?token=${token}`)}`;
      }
    } catch (erro) {
      status.textContent = erro.message;
    }
  }

  async function aceitar() {
    const botao = $("[data-convite-aceitar]");
    botao.disabled = true;
    try {
      const resposta = await api("/api/equipe/convite/aceitar", {
        method: "POST",
        body: JSON.stringify({ token }),
      });
      sessionStorage.removeItem("agro.convitePendente");
      toast("success", "Convite aceito", resposta.message || "Você agora colabora nesta propriedade.");
      $("[data-convite-status]").textContent =
        "Convite aceito. Use o seletor Propriedade ativa na barra lateral para abrir essa fazenda.";
      botao.hidden = true;
      $("[data-convite-ir]").hidden = false;
    } catch (erro) {
      toast("error", "Não foi possível aceitar", erro.message);
      botao.disabled = false;
    }
  }

  $("[data-convite-aceitar]")?.addEventListener("click", aceitar);
  document.addEventListener("DOMContentLoaded", carregar);
})();