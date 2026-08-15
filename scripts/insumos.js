/**
 * Agro — interações da tela Insumos (dados mock híbridos)
 * --------------------------------------------------------------------------
 * Cobre o "Flow 5 — Ações do estoque" e o "Flow 6 — Insumo → Atividade →
 * Talhão → Custo" do board Fluxos de Interação:
 * - Ver detalhes (meta + rastreio operacional ilustrativo)
 * - Registrar entrada / saída (recalcula o status do item automaticamente)
 * - Editar (categoria / estoque mínimo) e Excluir (só com saldo = 0)
 * - Busca por nome + filtro por categoria + alerta de itens críticos
 *
 * Regra de status (reaplicada sempre que o saldo muda — os valores
 * iniciais vêm do mock do pen.dev e podem não seguir exatamente esta
 * fórmula, mas toda movimentação feita nesta tela passa a obedecê-la):
 *   saldo / mínimo <  0.6  → Crítico
 *   saldo / mínimo <  1.0  → Baixo
 *   saldo / mínimo >= 1.0  → Normal
 */
(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

  const state = {
    totalProducts: 47,
    entriesThisMonth: 12,
    activeRow: null, // linha da tabela em contexto (saída/editar/detalhe/excluir)
  };

  function toast(type, title, description) {
    if (window.AgroToast) window.AgroToast.show({ type, title, description });
  }

  function formatQty(value) {
    return value.toLocaleString("pt-BR", { maximumFractionDigits: 1 });
  }

  /* ---------------------------------------------------------------------- */
  /* Modal helpers                                                          */
  /* ---------------------------------------------------------------------- */
  function openModal(modal) {
    if (!modal) return;
    modal.classList.add("is-open");
    modal.setAttribute("aria-hidden", "false");
    modal.querySelector("input:not([disabled]), select, button")?.focus();
  }

  function closeModal(modal) {
    if (!modal) return;
    modal.classList.remove("is-open");
    modal.setAttribute("aria-hidden", "true");
  }

  function initGenericModals() {
    $$(".modal-backdrop").forEach((modal) => {
      $$("[data-close-modal]", modal).forEach((btn) => btn.addEventListener("click", () => closeModal(modal)));
      modal.addEventListener("click", (event) => {
        if (event.target === modal) closeModal(modal);
      });
    });
  }

  function clearErrors(form) {
    $$(".field-error", form).forEach((el) => (el.textContent = ""));
    $$("input, select", form).forEach((el) => el.removeAttribute("aria-invalid"));
  }

  function setError(form, field, message) {
    const errorEl = form.querySelector(`[data-error-for="${field}"]`);
    if (errorEl) errorEl.textContent = message;
    form.querySelector(`[name="${field}"]`)?.setAttribute("aria-invalid", "true");
  }

  /* ---------------------------------------------------------------------- */
  /* Status do item (recalculado a cada movimentação)                       */
  /* ---------------------------------------------------------------------- */
  function statusFor(qty, min) {
    const ratio = min > 0 ? qty / min : 1;
    if (ratio < 0.6) return { label: "Crítico", cls: "badge-error" };
    if (ratio < 1) return { label: "Baixo", cls: "badge-warning" };
    return { label: "Normal", cls: "badge-success" };
  }

  function applyRowStatus(row) {
    const qty = Number(row.dataset.qty);
    const min = Number(row.dataset.min);
    const { label, cls } = statusFor(qty, min);
    const badge = $("[data-status-badge]", row);
    const wasCritical = badge.classList.contains("badge-error");
    badge.className = `badge ${cls}`;
    badge.textContent = label;
    $("[data-qty-cell]", row).textContent = formatQty(qty);
    return { becameCritical: !wasCritical && cls === "badge-error", label };
  }

  /* ---------------------------------------------------------------------- */
  /* Alerta crítico + métrica "Estoque Crítico"                             */
  /* ---------------------------------------------------------------------- */
  function recomputeCriticalAlert() {
    const rows = $$("[data-stock-table] tbody tr");
    const critical = rows.filter((row) => $("[data-status-badge]", row)?.classList.contains("badge-error"));

    const metric = $("[data-metric='critical']");
    if (metric) metric.textContent = String(critical.length);

    const alertBox = $("[data-critical-alert]");
    const countEl = $("[data-critical-count]");
    const namesEl = $("[data-critical-names]");
    if (!alertBox) return;

    if (critical.length === 0) {
      alertBox.hidden = true;
      return;
    }

    alertBox.hidden = false;
    if (countEl) countEl.textContent = String(critical.length);
    const names = critical.slice(0, 3).map((row) => row.dataset.product);
    const extra = critical.length - names.length;
    let text = names.join(", ");
    if (extra > 0) text += ` e outros ${extra}`;
    if (namesEl) namesEl.textContent = `${text} precisam de reposição.`;
  }

  /* ---------------------------------------------------------------------- */
  /* Menu de ações por linha (construído via JS — evita repetir 7x no HTML) */
  /* ---------------------------------------------------------------------- */
  function actionMenuMarkup() {
    return `
      <button type="button" class="action-menu-item" role="menuitem" data-row-action="detail"><span class="action-menu-icon tone-info" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"/><circle cx="12" cy="12" r="3"/></svg></span><span class="action-menu-label">Ver detalhes</span></button>
      <button type="button" class="action-menu-item" role="menuitem" data-row-action="entrada"><span class="action-menu-icon tone-success" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M12 5v14"/><path d="m19 12-7 7-7-7"/></svg></span><span class="action-menu-label">Registrar entrada</span></button>
      <button type="button" class="action-menu-item" role="menuitem" data-row-action="saida"><span class="action-menu-icon tone-secondary" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M12 19V5"/><path d="m5 12 7-7 7 7"/></svg></span><span class="action-menu-label">Registrar saída</span></button>
      <button type="button" class="action-menu-item" role="menuitem" data-row-action="edit"><span class="action-menu-icon tone-primary" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg></span><span class="action-menu-label">Editar</span></button>
      <button type="button" class="action-menu-item is-danger" role="menuitem" data-row-action="delete"><span class="action-menu-icon tone-error" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg></span><span class="action-menu-label">Excluir</span></button>
    `;
  }

  function populateActionMenus() {
    $$("[data-stock-table] tbody tr").forEach((row) => {
      const menu = $("[data-popover]", row);
      if (menu) menu.innerHTML = actionMenuMarkup();
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Busca + filtro por categoria                                           */
  /* ---------------------------------------------------------------------- */
  function initFilters() {
    const search = $("[data-stock-search]");
    const chips = $$("[data-filter-category]");
    const count = $("[data-stock-count]");
    const empty = $("[data-stock-empty]");
    const table = $("[data-stock-table]");
    let activeCategory = "todas";

    function apply() {
      const rows = $$("[data-stock-table] tbody tr");
      const q = (search?.value || "").trim().toLowerCase();
      let visible = 0;

      rows.forEach((row) => {
        const matchesCategory = activeCategory === "todas" || row.dataset.category === activeCategory;
        const matchesQuery = !q || row.dataset.product.toLowerCase().includes(q);
        const show = matchesCategory && matchesQuery;
        row.hidden = !show;
        if (show) visible += 1;
      });

      if (count) count.textContent = `${visible} produto${visible === 1 ? "" : "s"} encontrado${visible === 1 ? "" : "s"}`;
      if (empty) empty.hidden = visible > 0;
      if (table) table.hidden = visible === 0;
    }

    search?.addEventListener("input", apply);
    chips.forEach((chip) => {
      chip.addEventListener("click", () => {
        chips.forEach((c) => c.classList.remove("is-active"));
        chip.classList.add("is-active");
        activeCategory = chip.dataset.filterCategory;
        apply();
      });
    });

    apply();
  }

  /* ---------------------------------------------------------------------- */
  /* Registrar entrada                                                      */
  /* ---------------------------------------------------------------------- */
  function initEntrada() {
    const modal = $("[data-modal='entrada']");
    const form = $("[data-entrada-form]", modal);
    const produtoSelect = $("[name='produto']", form);
    const produtoWrap = $("[data-entrada-produto-wrap]", modal);

    // Popula o select de produto (usado apenas quando aberto pelo cabeçalho).
    $$("[data-stock-table] tbody tr").forEach((row) => {
      const opt = document.createElement("option");
      opt.value = row.dataset.product;
      opt.textContent = row.dataset.product;
      produtoSelect.appendChild(opt);
    });
    window.AgroSelect?.enhance(produtoSelect);

    function openFor(row) {
      state.activeRow = row;
      clearErrors(form);
      form.reset();
      if (row) {
        produtoWrap.hidden = true;
        $("[data-entrada-desc]", modal).textContent = `Aumenta o saldo de ${row.dataset.product}.`;
      } else {
        produtoWrap.hidden = false;
        $("[data-entrada-desc]", modal).textContent = "Selecione o produto e informe a quantidade recebida.";
      }
      openModal(modal);
    }

    $$("[data-open-entrada]").forEach((btn) => {
      btn.addEventListener("click", () => openFor(btn.dataset.context === "header" ? null : btn.closest("tr")));
    });

    document.addEventListener("click", (event) => {
      const btn = event.target.closest("[data-row-action='entrada']");
      if (!btn) return;
      openFor(btn.closest("tr"));
    });

    form.addEventListener("submit", (event) => {
      event.preventDefault();
      clearErrors(form);

      const qty = Number(form.quantidade.value);
      let row = state.activeRow;

      if (!row) {
        const produtoNome = produtoSelect.value;
        if (!produtoNome) {
          setError(form, "produto", "Selecione um produto.");
          toast("error", "Falha ao salvar", "Selecione o produto para registrar a entrada.");
          return;
        }
        row = $$("[data-stock-table] tbody tr").find((r) => r.dataset.product === produtoNome);
      }

      if (!qty || qty <= 0) {
        setError(form, "quantidade", "Informe uma quantidade maior que zero.");
        toast("error", "Falha ao salvar", "Revise os campos destacados no formulário.");
        return;
      }

      row.dataset.qty = String(Number(row.dataset.qty) + qty);
      applyRowStatus(row);
      recomputeCriticalAlert();

      state.entriesThisMonth += 1;
      const metric = $("[data-metric='entries']");
      if (metric) metric.textContent = String(state.entriesThisMonth);

      closeModal(modal);
      toast("success", "Entrada registrada", `${formatQty(qty)} ${row.dataset.unit} de ${row.dataset.product} adicionados ao estoque.`);
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Registrar saída                                                        */
  /* ---------------------------------------------------------------------- */
  function initSaida() {
    const modal = $("[data-modal='saida']");
    const form = $("[data-saida-form]", modal);

    document.addEventListener("click", (event) => {
      const btn = event.target.closest("[data-row-action='saida']");
      if (!btn) return;
      const row = btn.closest("tr");
      state.activeRow = row;
      clearErrors(form);
      form.reset();
      $("[data-saida-desc]", modal).textContent = `Reduz o saldo de ${row.dataset.product} — opcionalmente vincule a uma atividade.`;
      openModal(modal);
    });

    form.addEventListener("submit", (event) => {
      event.preventDefault();
      clearErrors(form);
      const row = state.activeRow;
      if (!row) return;

      const qty = Number(form.quantidade.value);
      const currentQty = Number(row.dataset.qty);

      if (!qty || qty <= 0) {
        setError(form, "quantidade", "Informe uma quantidade maior que zero.");
        toast("error", "Falha ao salvar", "Revise os campos destacados no formulário.");
        return;
      }
      if (qty > currentQty) {
        setError(form, "quantidade", `Saldo disponível: ${formatQty(currentQty)} ${row.dataset.unit}.`);
        toast("error", "Falha ao salvar", "Quantidade maior que o saldo disponível.");
        return;
      }

      row.dataset.qty = String(currentQty - qty);
      const { becameCritical, label } = applyRowStatus(row);
      recomputeCriticalAlert();

      closeModal(modal);
      const destino = form.destino.value.trim();
      toast(
        "success",
        "Saída registrada",
        `${formatQty(qty)} ${row.dataset.unit} de ${row.dataset.product} baixados${destino ? ` · ${destino}` : ""} · custo lançado.`
      );

      if (becameCritical || label === "Baixo") {
        window.setTimeout(() => {
          toast("warning", "Estoque baixo", `${row.dataset.product} está ${label.toLowerCase()} (${formatQty(Number(row.dataset.qty))} ${row.dataset.unit}).`);
        }, 260);
      }
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Editar produto                                                         */
  /* ---------------------------------------------------------------------- */
  function initEditar() {
    const modal = $("[data-modal='produto-editar']");
    const form = $("[data-produto-editar-form]", modal);

    document.addEventListener("click", (event) => {
      const btn = event.target.closest("[data-row-action='edit']");
      if (!btn) return;
      const row = btn.closest("tr");
      state.activeRow = row;
      clearErrors(form);
      const categoryLabel = row.querySelector(".col-muted").textContent;
      form.categoria.value = categoryLabel;
      form.categoria.dispatchEvent(new Event("change"));
      form.minimo.value = row.dataset.min;
      openModal(modal);
    });

    form.addEventListener("submit", (event) => {
      event.preventDefault();
      clearErrors(form);
      const row = state.activeRow;
      if (!row) return;

      const categoria = form.categoria.value;
      const minimo = Number(form.minimo.value);
      let hasError = false;
      if (!categoria) { setError(form, "categoria", "Selecione a categoria."); hasError = true; }
      if (!minimo || minimo <= 0) { setError(form, "minimo", "Informe um estoque mínimo maior que zero."); hasError = true; }

      if (hasError) {
        toast("error", "Falha ao salvar", "Revise os campos destacados no formulário.");
        return;
      }

      row.dataset.category = categoria.toLowerCase();
      row.dataset.min = String(minimo);
      // Ordem das células .col-muted na linha: [0] Categoria, [1] Unidade, [2] Est. Mín.
      const mutedCells = $$(".col-muted", row);
      mutedCells[0].textContent = categoria;
      mutedCells[2].textContent = String(minimo);
      applyRowStatus(row);
      recomputeCriticalAlert();

      closeModal(modal);
      toast("success", "Produto atualizado", `${row.dataset.product} foi salvo com sucesso.`);
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Ver detalhes                                                           */
  /* ---------------------------------------------------------------------- */
  function initDetalhe() {
    const modal = $("[data-modal='produto-detalhe']");

    document.addEventListener("click", (event) => {
      const btn = event.target.closest("[data-row-action='detail']");
      if (!btn) return;
      const row = btn.closest("tr");

      $("[data-detalhe-title]", modal).textContent = row.dataset.product;
      $("[data-detalhe-subtitle]", modal).textContent = `${row.querySelector(".col-muted").textContent} · Unidade ${row.dataset.unit}`;
      $("[data-detalhe-meta]", modal).innerHTML = `
        <div class="meta-cell"><span class="meta-cell-label">Saldo atual</span><span class="meta-cell-value">${formatQty(Number(row.dataset.qty))} ${row.dataset.unit}</span></div>
        <div class="meta-cell"><span class="meta-cell-label">Estoque mínimo</span><span class="meta-cell-value">${row.dataset.min} ${row.dataset.unit}</span></div>
        <div class="meta-cell"><span class="meta-cell-label">Status</span><span class="meta-cell-value">${$("[data-status-badge]", row).textContent}</span></div>
      `;
      openModal(modal);
    });
  }

  /* ---------------------------------------------------------------------- */
  /* Excluir produto (só permitido com saldo = 0)                           */
  /* ---------------------------------------------------------------------- */
  function initExcluir() {
    const modal = $("[data-modal='produto-excluir']");

    document.addEventListener("click", (event) => {
      const btn = event.target.closest("[data-row-action='delete']");
      if (!btn) return;
      const row = btn.closest("tr");
      const qty = Number(row.dataset.qty);

      if (qty > 0) {
        toast(
          "error",
          "Não é possível excluir",
          `${row.dataset.product} ainda tem ${formatQty(qty)} ${row.dataset.unit} em estoque. Zere o saldo antes de excluir.`
        );
        return;
      }

      state.activeRow = row;
      $("[data-excluir-desc]", modal).textContent = `${row.dataset.product} será removido do inventário.`;
      openModal(modal);
    });

    $("[data-confirm-excluir]", modal)?.addEventListener("click", () => {
      const row = state.activeRow;
      if (!row) return;
      const name = row.dataset.product;
      row.remove();
      closeModal(modal);

      state.totalProducts = Math.max(0, state.totalProducts - 1);
      const metric = $("[data-metric='total-products']");
      if (metric) metric.textContent = String(state.totalProducts);

      recomputeCriticalAlert();
      $("[data-stock-search]")?.dispatchEvent(new Event("input"));
      toast("info", "Produto excluído", `${name} foi removido do inventário.`);
    });
  }

  function init() {
    populateActionMenus();
    initGenericModals();
    initFilters();
    initEntrada();
    initSaida();
    initEditar();
    initDetalhe();
    initExcluir();
    recomputeCriticalAlert();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
