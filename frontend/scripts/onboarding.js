/* Pedro Henrique Contardi Soler — RA: 25005592 */
/**
 * Tour de boas-vindas no dashboard para contas com onboardingConcluido=false (API).
 */
(() => {
  "use strict";

  const STORAGE_USER = "agro.sessionUser";
  const STEPS = [
    {
      title: "Bem-vindo ao Agro Gestão",
      html: `<p>Este painel concentra a operação da <strong>propriedade ativa</strong>: indicadores, atividades recentes e atalhos do dia a dia.</p>
        <p>Tudo o que você registrar fica salvo no servidor — pode recarregar a página ou trocar de dispositivo sem perder dados.</p>`,
    },
    {
      title: "Comece pelo catálogo de culturas",
      html: `<p>Antes de talhões e safras, cadastre as <strong>culturas</strong> que você realmente planta (Soja, Milho, Cana, pastagem…).</p>
        <ul><li>Menu lateral → <strong>Culturas</strong></li><li>Nomes únicos por propriedade</li><li>Usados em talhões, mapa e ciclos produtivos</li></ul>`,
    },
    {
      title: "Áreas de cultivo e mapa",
      html: `<p>Em <strong>Áreas de Cultivo</strong> você define talhões, hectares, safra e status.</p>
        <p>O mapa mostra polígonos e ocorrências quando você informa a localização de cada talhão.</p>`,
    },
    {
      title: "Safras e ciclo produtivo",
      html: `<p><strong>Safras</strong> agrupam talhões, cultura e período em etapas: planejamento → colheita → finalização.</p>
        <p>Atividades, ocorrências e custos podem ser consultados no contexto de cada safra.</p>`,
    },
    {
      title: "Rotina do campo",
      html: `<ul>
        <li><strong>Atividades</strong> — planeje e conclua tarefas por talhão</li>
        <li><strong>Insumos</strong> — estoque e movimentações</li>
        <li><strong>Ocorrências</strong> — pragas, clima, manutenções</li>
        <li><strong>Custos</strong> — lançamentos e indicadores</li>
        <li><strong>Manutenção</strong> — equipamentos e preventivas</li>
      </ul>`,
    },
    {
      title: "Pronto para começar",
      html: `<p>Sugestão de primeiro fluxo:</p>
        <ol style="margin:12px 0 0;padding-left:20px;color:var(--text-secondary);font-size:14px;line-height:1.5">
          <li>Cadastre culturas</li>
          <li>Crie um talhão</li>
          <li>Abra uma safra vinculada</li>
        </ol>
        <p style="margin-top:14px">Use o menu lateral para navegar entre os módulos a qualquer momento.</p>`,
    },
  ];

  const root = () => document.querySelector("[data-onboarding]");
  const $ = (sel, el = document) => el.querySelector(sel);
  const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];

  function parseUser() {
    try {
      return JSON.parse(sessionStorage.getItem(STORAGE_USER) || "null");
    } catch {
      return null;
    }
  }

  function isDashboardPage() {
    const file = (window.location.pathname.split("/").pop() || "").toLowerCase().replace(/\.html$/i, "");
    return file === "dashboard" || file === "" || file === "index";
  }

  function shouldShow(user) {
    if (!user) return false;
    // Só exibe quando a API confirma pendente (evita tour em contas legadas sem o campo).
    return user.onboardingConcluido === false;
  }

  async function fetchUsuarioAtual() {
    const token = sessionStorage.getItem("agro.sessionToken");
    if (!token) return null;
    const response = await fetch(`${window.location.origin}/api/me`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: AbortSignal.timeout(15000),
    });
    if (!response.ok) return null;
    const data = await response.json();
    const usuario = data.usuario;
    if (usuario) sessionStorage.setItem(STORAGE_USER, JSON.stringify(usuario));
    return usuario;
  }

  function refreshSessionUser(patch) {
    const user = parseUser();
    if (!user) return;
    Object.assign(user, patch);
    sessionStorage.setItem(STORAGE_USER, JSON.stringify(user));
  }

  async function persistConclusion() {
    const token = sessionStorage.getItem("agro.sessionToken");
    if (!token) throw new Error("Sessão ausente.");
    const response = await fetch(`${window.location.origin}/api/perfil/onboarding`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: "{}",
    });
    const body = response.status === 204 ? {} : await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.error || "Não foi possível salvar o onboarding.");
    refreshSessionUser({ onboardingConcluido: true });
    return true;
  }

  function renderProgress(host, index, total) {
    host.innerHTML = "";
    for (let i = 0; i < total; i++) {
      const bar = document.createElement("span");
      if (i < index) bar.classList.add("is-done");
      if (i === index) bar.classList.add("is-current");
      host.append(bar);
    }
  }

  function renderSlide(track, step, direction) {
    const current = $(".onboarding-slide.is-active", track);
    const next = document.createElement("article");
    next.className = "onboarding-slide";
    next.innerHTML = step.html;
    next.setAttribute("role", "group");
    next.setAttribute("aria-roledescription", "slide");

    if (current) {
      current.classList.add("is-leaving");
      current.classList.remove("is-active");
      const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      const delay = reduced ? 0 : 220;
      setTimeout(() => current.remove(), delay);
    }

    track.append(next);
    requestAnimationFrame(() => next.classList.add("is-active"));

    const title = $("[data-onboarding-title]");
    if (title) title.textContent = step.title;

    const label = $("[data-onboarding-step-label]");
    if (label) label.textContent = `Passo ${direction.index + 1} de ${STEPS.length}`;

    const back = $("[data-onboarding-back]");
    const nextBtn = $("[data-onboarding-next]");
    const finish = $("[data-onboarding-finish]");
    if (back) back.hidden = direction.index === 0;
    if (nextBtn) nextBtn.hidden = direction.index >= STEPS.length - 1;
    if (finish) finish.hidden = direction.index < STEPS.length - 1;

    renderProgress($("[data-onboarding-progress]"), direction.index, STEPS.length);
  }

  function close() {
    const el = root();
    if (!el) return;
    el.classList.remove("is-open");
    el.setAttribute("aria-hidden", "true");
    el.hidden = true;
    document.body.classList.remove("has-onboarding");
  }

  function open() {
    const el = root();
    if (!el) return;
    el.hidden = false;
    el.setAttribute("aria-hidden", "false");
    requestAnimationFrame(() => el.classList.add("is-open"));
    document.body.classList.add("has-onboarding");
    $("[data-onboarding-next]")?.focus();
  }

  function bindTour() {
    const el = root();
    if (!el || el.dataset.bound === "1") return;
    el.dataset.bound = "1";

    const track = $("[data-onboarding-track]", el);
    let index = 0;
    let busy = false;

    const go = (newIndex) => {
      if (busy || newIndex < 0 || newIndex >= STEPS.length) return;
      index = newIndex;
      renderSlide(track, STEPS[index], { index });
    };

    renderSlide(track, STEPS[0], { index: 0 });

    const conclude = async () => {
      if (busy) return;
      busy = true;
      try {
        await persistConclusion();
        close();
        window.AgroToast?.show({
          type: "success",
          title: "Tour concluído",
          description: "Você já pode explorar o painel. Cadastre culturas quando quiser.",
        });
      } catch (error) {
        window.AgroToast?.show({
          type: "error",
          title: "Não foi possível concluir",
          description: error.message || "Tente novamente.",
        });
      } finally {
        busy = false;
      }
    };

    $("[data-onboarding-back]", el)?.addEventListener("click", () => go(index - 1));
    $("[data-onboarding-next]", el)?.addEventListener("click", () => go(index + 1));
    $("[data-onboarding-finish]", el)?.addEventListener("click", conclude);
    $("[data-onboarding-skip]", el)?.addEventListener("click", conclude);

    el.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        conclude();
      }
    });
  }

  async function tryStart(event) {
    if (!isDashboardPage()) return;
    if (!root()) return;

    let user = event?.detail?.usuario || parseUser();
    if (!shouldShow(user)) {
      user = await fetchUsuarioAtual();
    }
    if (!shouldShow(user)) return;

    bindTour();
    open();
  }

  document.addEventListener("agro:authenticated", (event) => {
    tryStart(event).catch(() => {});
  });
  if (window.__agroAuthenticated) {
    tryStart().catch(() => {});
  }
})();
