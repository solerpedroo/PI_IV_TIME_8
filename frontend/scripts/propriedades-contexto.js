// Miguel — RA: 25003110
/* Lista e troca de propriedade ativa (próprias e colaboração), compartilhado pela sidebar e Configurações. */
(() => {
  "use strict";

  const base = () => window.location.origin;
  const token = () => sessionStorage.getItem("agro.sessionToken");

  function rotuloOpcao(propriedade) {
    const nome = propriedade.nome || propriedade.farm || "Propriedade";
    return propriedade.papel === "colaborador" ? `${nome} — colaboração` : `${nome} — minha`;
  }

  async function listar() {
    if (!token()) return [];
    const response = await fetch(`${base()}/api/propriedades`, {
      headers: { Authorization: `Bearer ${token()}` },
      signal: AbortSignal.timeout(15000),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw Object.assign(new Error(body.error || "Não foi possível listar propriedades."), { body });
    return Array.isArray(body) ? body : [];
  }

  async function selecionar(propriedadeId) {
    if (!token() || !propriedadeId) throw new Error("Sessão ou propriedade inválida.");
    const response = await fetch(`${base()}/api/propriedades/${encodeURIComponent(propriedadeId)}/selecionar`, {
      method: "POST",
      body: "{}",
      headers: { Authorization: `Bearer ${token()}`, "Content-Type": "application/json" },
      signal: AbortSignal.timeout(15000),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw Object.assign(new Error(body.error || "Não foi possível trocar a propriedade."), { body });

    const user = JSON.parse(sessionStorage.getItem("agro.sessionUser") || "null");
    if (user) {
      user.propriedadeId = body.propriedadeId || propriedadeId;
      sessionStorage.setItem("agro.sessionUser", JSON.stringify(user));
    }

    window.location.reload();
  }

  function preencherSelect(select, propriedades, propriedadeAtivaId) {
    if (!select) return propriedadeAtivaId;
    const ativa = propriedades.find((p) => p.selecionada) || propriedades.find((p) => p.id === propriedadeAtivaId);
    const idAtivo = ativa?.id || propriedadeAtivaId;
    select.replaceChildren(
      ...propriedades.map((p) => new Option(rotuloOpcao(p), p.id, false, p.id === idAtivo)),
    );
    if (idAtivo) select.value = idAtivo;
    return idAtivo;
  }

  function ligarTroca(select, propriedadeAtivaId) {
    if (!select || select.dataset.contextBound === "true") return;
    select.dataset.contextBound = "true";
    let atual = propriedadeAtivaId;
    select.addEventListener("change", async () => {
      if (!select.value || select.value === atual) return;
      const anterior = atual;
      select.disabled = true;
      try {
        await selecionar(select.value);
      } catch (erro) {
        select.value = anterior;
        select.disabled = false;
        window.AgroToast?.show({
          type: "error",
          title: "Não foi possível trocar",
          description: erro.message,
        });
      }
    });
  }

  function propriedadesProprias(propriedades) {
    return propriedades.filter((p) => p.papel !== "colaborador");
  }

  window.AgroContextoPropriedade = {
    listar,
    selecionar,
    rotuloOpcao,
    preencherSelect,
    ligarTroca,
    propriedadesProprias,
  };
})();