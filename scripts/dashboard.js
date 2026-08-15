/**
 * Agro — interações do Painel Geral (dados mock híbridos)
 * --------------------------------------------------------------------------
 * - CTA Nova Atividade (modal stub)
 * - Concluir atividade na tabela (G4: métrica 8→7 + toast)
 * - Busca local filtrando linhas da tabela
 *
 * Estrutura pronta para substituir mocks por fetch quando a API existir.
 */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

  /** Estado local — espelha valores iniciais do pen.dev */
  const state = {
    pendingActivities: 8,
    activeAreas: 12,
    lowStock: 3,
  };

  function toast(type, title, description) {
    if (window.AgroToast) {
      window.AgroToast.show({ type, title, description });
    }
  }

  function updateMetric(key, value) {
    const el = document.querySelector(`[data-metric="${key}"]`);
    if (el) el.textContent = String(value);
  }

  /* ---------------------------------------------------------------------- */
  /* Modal Nova Atividade                                                   */
  /* ---------------------------------------------------------------------- */
  function openModal(modal) {
    if (!modal) return;
    modal.classList.add("is-open");
    modal.setAttribute("aria-hidden", "false");
    modal.querySelector("input, select, button")?.focus();
  }

  function closeModal(modal) {
    if (!modal) return;
    modal.classList.remove("is-open");
    modal.setAttribute("aria-hidden", "true");
  }

  function initNewActivity() {
    const modal = $("[data-modal='new-activity']");
    const openBtn = $("[data-open-new-activity]");
    if (!modal || !openBtn) return;

    openBtn.addEventListener("click", () => openModal(modal));

    $$("[data-close-modal]", modal).forEach((btn) => {
      btn.addEventListener("click", () => closeModal(modal));
    });

    modal.addEventListener("click", (event) => {
      if (event.target === modal) closeModal(modal);
    });

    const form = $("[data-new-activity-form]", modal);
    if (!form) return;

    form.addEventListener("submit", (event) => {
      event.preventDefault();
      const name = $("[name='activity-name']", form);
      const plot = $("[name='activity-plot']", form);
      if (!name.value.trim() || !plot.value) {
        toast("error", "Falha ao salvar", "Preencha o nome e o talhão da atividade.");
        return;
      }

      // Mock G4: nova atividade aumenta pendentes
      state.pendingActivities += 1;
      updateMetric("pending", state.pendingActivities);

      closeModal(modal);
      form.reset();
      toast(
        "success",
        "Atividade registrada",
        `${name.value.trim()} agendada com sucesso.`
      );
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Concluir atividade (G4)                                                */
  /* ---------------------------------------------------------------------- */
  function initCompleteActions() {
    const table = $("[data-activities-table]");
    if (!table) return;

    table.addEventListener("click", (event) => {
      const btn = event.target.closest("[data-complete-activity]");
      if (!btn) return;

      const row = btn.closest("tr");
      if (!row || row.dataset.done === "true") return;

      const nameCell = row.querySelector(".col-activity");
      const name =
        [...(nameCell?.childNodes || [])]
          .find((node) => node.nodeType === Node.TEXT_NODE && node.textContent.trim())
          ?.textContent.trim() || "Atividade";

      const status = row.querySelector(".badge");
      if (status) {
        status.className = "badge badge-success";
        status.textContent = "Concluída";
      }

      row.dataset.done = "true";
      btn.remove();

      if (state.pendingActivities > 0) {
        state.pendingActivities -= 1;
        updateMetric("pending", state.pendingActivities);
      }

      toast("success", "Status atualizado", `${name} marcada como concluída.`);
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Busca local                                                            */
  /* ---------------------------------------------------------------------- */
  function initSearch() {
    const input = $("[data-dashboard-search]");
    const rows = $$("[data-activities-table] tbody tr");
    if (!input || !rows.length) return;

    input.addEventListener("input", () => {
      const q = input.value.trim().toLowerCase();
      rows.forEach((row) => {
        const text = row.textContent.toLowerCase();
        row.hidden = Boolean(q) && !text.includes(q);
      });
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Demo G4: botões de simulação (opcional no header)                      */
  /* ---------------------------------------------------------------------- */
  function initDemoTriggers() {
    const demoArea = $("[data-demo-area]");
    if (demoArea) {
      demoArea.addEventListener("click", () => {
        state.activeAreas += 1;
        updateMetric("areas", state.activeAreas);
        toast(
          "success",
          "Propriedade criada",
          "Fazenda Boa Vista foi salva. Áreas ativas atualizadas."
        );
      });
    }

    const demoOcc = $("[data-demo-occurrence]");
    if (demoOcc) {
      demoOcc.addEventListener("click", () => {
        const count = $("[data-alerts-count]");
        if (count) {
          const next = Number(count.textContent || "0") + 1;
          count.textContent = String(next);
        }
        toast(
          "warning",
          "Ocorrência registrada",
          "Praga detectada no Talhão Norte. Lista e dashboard atualizados."
        );
      });
    }
  }

  function init() {
    initNewActivity();
    initCompleteActions();
    initSearch();
    initDemoTriggers();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
