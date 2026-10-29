/* Miguel — RA: 25003110 */
/* Autor secundário: Pedro Henrique Contardi Soler - 25005592 */
/* Estoque: o servidor confirma toda gravação. Não há dados operacionais locais
   nem alteração antecipada de saldo; falhas de consulta limpam a apresentação. */
(() => {
  "use strict";
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const base = window.location.port === "8080" ? window.location.origin : "http://127.0.0.1:8080";
  let products = [], activities = [], active = null, category = "todas", ready = false;
  const qty = value => Number(value).toLocaleString("pt-BR", { maximumFractionDigits: 6 });
  const money = value => Number(value).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
  const esc = value => String(value ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const toast = (type, title, description) => window.AgroToast?.show({ type, title, description });

  async function api(path, options = {}) {
    const token = sessionStorage.getItem("agro.sessionToken");
    if (!token) throw new Error("Entre na sua conta para acessar o estoque.");
    const response = await fetch(base + path, {
      ...options, signal: AbortSignal.timeout(15000),
      headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
    });
    const data = response.status === 204 ? null : await response.json();
    if (!response.ok) throw Object.assign(new Error(data.error || "Não foi possível concluir a operação."), { fields: data.fields || {} });
    return data;
  }

  function modal(name, open) {
    const box = $(`[data-modal='${name}']`);
    box.classList.toggle("is-open", open);
    box.setAttribute("aria-hidden", String(!open));
    if (open) box.querySelector("input, select, button")?.focus();
  }

  function errors(form, fields = {}) {
    $$("[data-error-for]", form).forEach(el => { el.textContent = ""; });
    $$("[aria-invalid]", form).forEach(el => el.removeAttribute("aria-invalid"));
    Object.entries(fields).forEach(([key, message]) => {
      const el = $(`[data-error-for='${key}']`, form);
      if (el) el.textContent = message;
      form.elements.namedItem(key)?.setAttribute("aria-invalid", "true");
    });
    if (Object.keys(fields).length) $("[aria-invalid]", form)?.focus();
  }

  function busy(form, saving) {
    form.setAttribute("aria-busy", String(saving));
    $$('button[type="submit"], button:not([type])', form).forEach(button => { button.disabled = saving; });
  }

  // Uma confirmação HTTP e uma consulta posterior são etapas diferentes.
  // Se a gravação deu certo mas a atualização falhou, informa isso sem sugerir
  // repetir a operação (uma nova tentativa poderia duplicar a movimentação).
  async function save(form, name, action) {
    if (form.getAttribute("aria-busy") === "true") return;
    errors(form); busy(form, true);
    let confirmed = false;
    try {
      await action();
      confirmed = true;
      modal(name, false);
      await load();
      toast("success", "Estoque atualizado", "Operação registrada com sucesso.");
    } catch (error) {
      if (!confirmed) errors(form, error.fields);
      toast("error", confirmed ? "Gravação confirmada" : "Não foi possível confirmar", confirmed
        ? "O registro foi salvo, mas a consulta falhou. Use Tentar novamente para atualizar a lista."
        : error.message + " Se a conexão caiu durante o envio, atualize o histórico antes de repetir.");
    } finally { busy(form, false); }
  }

  function unavailable(message) {
    ready = false; products = []; active = null;
    $("[data-stock-table] tbody").replaceChildren();
    $("[data-stock-table]").hidden = true;
    $("[data-stock-empty]").hidden = true;
    $("[data-critical-alert]").hidden = true;
    $$("[data-metric]").forEach(el => { el.textContent = "—"; });
    $("[data-stock-count]").textContent = "Estoque não carregado";
    $("[data-stock-message]").textContent = message;
    $("[data-stock-retry]").hidden = false;
    $$("[data-open-produto], [data-open-entrada]").forEach(button => { button.disabled = true; });
  }

  function render() {
    const query = $("[data-stock-search]").value.trim().toLowerCase();
    const shown = products.filter(p => (category === "todas" || p.categoria.toLowerCase() === category) && p.nome.toLowerCase().includes(query));
    const body = $("[data-stock-table] tbody");
    body.replaceChildren(...shown.map(p => {
      const row = document.createElement("tr");
      row.dataset.id = p.id;
      const cls = p.status === "Crítico" ? "badge-error" : p.status === "Baixo" ? "badge-warning" : "badge-success";
      const menuId = "stock-menu-" + p.id;
      row.innerHTML = `<td class="col-product-name">${esc(p.nome)}</td><td class="col-muted">${esc(p.categoria)}</td>
        <td>${qty(p.saldo)}</td><td class="col-muted">${esc(p.unidade)}</td><td class="col-muted">${qty(p.minimo)}</td>
        <td><span class="badge ${cls}">${esc(p.status)}</span></td><td class="col-actions"><div class="action-menu-wrap">
        <button type="button" class="icon-btn icon-btn--sm" data-popover-trigger aria-controls="${menuId}" aria-expanded="false" aria-label="Ações de ${esc(p.nome)}">⋮</button>
        <div class="action-menu" id="${menuId}" data-popover role="menu">
        ${[["detail", "Ver detalhes"], ["entrada", "Registrar entrada"], ["saida", "Registrar saída"], ["ajuste", "Ajustar saldo"], ["editar", "Editar produto"], ["excluir", "Excluir produto"]].map(([key, label]) =>
          `<button type="button" class="action-menu-item" role="menuitem" data-operation="${key}">${label}</button>`).join("")}
        </div></div></td>`;
      return row;
    }));
    $("[data-stock-table]").hidden = shown.length === 0;
    $("[data-stock-empty]").hidden = shown.length > 0;
    $("[data-stock-count]").textContent = `${shown.length} produto(s) encontrado(s)`;
    const low = products.filter(p => p.saldo < p.minimo || p.status === "Crítico");
    $("[data-critical-alert]").hidden = !low.length;
    $("[data-critical-count]").textContent = low.length;
    $("[data-critical-names]").textContent = low.map(p => p.nome).join(", ") + " precisam de reposição.";
  }

  async function load() {
    try {
      const [items, summary, options, me] = await Promise.all([
        api("/api/insumos"), api("/api/insumos/resumo"), api("/api/insumos/atividades"), api("/api/me"),
      ]);
      products = items; activities = options; ready = true;
      $("[data-metric='total-products']").textContent = summary.totalProdutos;
      $("[data-metric='critical']").textContent = summary.criticos;
      $("[data-metric='value']").textContent = money(summary.valorEmEstoque);
      $("[data-metric='entries']").textContent = summary.entradasNoMes;
      $("[data-stock-context]").textContent = "Controle de estoque · " + (me.propriedade.nome || me.propriedade.farm);
      $(".sidebar-user-name").textContent = me.usuario.name;
      $(".sidebar-user-property").textContent = me.propriedade.nome || me.propriedade.farm;
      $(".sidebar-avatar").textContent = me.usuario.name.split(/\s+/).slice(0, 2).map(part => part[0]).join("");
      $("[data-stock-message]").textContent = "Estoque atualizado.";
      $("[data-stock-retry]").hidden = true;
      $$("[data-open-produto], [data-open-entrada]").forEach(button => { button.disabled = false; });
      render();
      // Destino do alerta de estoque no sino (#167).
      const insumoId = new URLSearchParams(location.search).get("insumoId");
      if (insumoId) {
        const product = products.find((item) => item.id === insumoId);
        if (product) {
          active = product;
          details(product).catch(() => {});
        }
      }
    } catch (error) { unavailable("Não foi possível consultar o estoque. " + error.message); throw error; }
  }

  function productPayload(form, creating) {
    const value = name => form.elements.namedItem(name).value;
    const data = { nome: value("nome"), categoria: value("categoria"), unidade: value("unidade"),
      minimo: value("minimo") === "" ? null : Number(value("minimo")),
      custoUnitario: value("custoUnitario") === "" ? null : Number(value("custoUnitario")),
      limiarCritico: value("limiarCritico") === "" ? null : Number(value("limiarCritico")) };
    if (creating) data.saldo = value("saldo") === "" ? null : Number(value("saldo"));
    return data;
  }

  function openMovement(type, product = null) {
    active = product;
    const form = $(`[data-${type}-form]`);
    form.reset(); errors(form);
    if (type === "entrada") {
      const select = form.elements.namedItem("produto");
      select.replaceChildren(new Option("Selecione o produto", ""), ...products.map(p => new Option(p.nome, p.id)));
      if (product) select.value = product.id;
    }
    if (type === "saida") {
      form.elements.namedItem("atividadeId").replaceChildren(new Option("Saída manual (sem atividade)", ""), ...activities.map(a => new Option(a.nome, a.id)));
      $("[data-saida-desc]").textContent = `${product.nome} · Saldo: ${qty(product.saldo)} ${product.unidade}.`;
    }
    if (type === "ajuste") {
      form.elements.namedItem("quantidade").value = product.saldo;
      $("[data-ajuste-desc]").textContent = `Informe o saldo contado de ${product.nome} e o motivo do ajuste.`;
    }
    modal(type, true);
  }

  async function details(product) {
    $("[data-detalhe-title]").textContent = product.nome;
    $("[data-detalhe-subtitle]").textContent = `${product.categoria} · Unidade ${product.unidade}`;
    $("[data-detalhe-meta]").textContent = `Saldo: ${qty(product.saldo)} ${product.unidade} · Mínimo: ${qty(product.minimo)} · Custo médio: ${money(product.custoUnitario)}`;
    const box = $("[data-movement-history]");
    box.textContent = "Carregando histórico…";
    modal("produto-detalhe", true);
    try {
      const history = await api(`/api/insumos/${product.id}/movimentacoes`);
      box.replaceChildren();
      const heading = document.createElement("h3"); heading.textContent = "Histórico de movimentações"; box.append(heading);
      if (!history.length) { box.append("Nenhuma movimentação registrada."); return; }
      const list = document.createElement("ul");
      history.forEach(m => {
        const item = document.createElement("li");
        item.textContent = [new Date(m.criadoEm).toLocaleString("pt-BR"), m.tipo,
          `${qty(m.quantidade)} ${m.unidade}`, `Saldo: ${qty(m.saldoAnterior)} → ${qty(m.saldoPosterior)}`,
          m.fornecedor && "Fornecedor: " + m.fornecedor,
          m.atividadeId && `Atividade: ${m.atividade} (${m.atividadeId})`,
          m.custoUnitario != null && "Custo unitário: " + money(m.custoUnitario), m.observacao].filter(Boolean).join(" · ");
        list.append(item);
      });
      box.append(list);
    } catch (error) { box.textContent = "Histórico indisponível. " + error.message; }
  }

  function init() {
    // Fecha os modais por botão, fundo ou Escape; o shell cuida dos popovers.
    $$('[data-close-modal]').forEach(button => button.addEventListener("click", () => modal(button.closest("[data-modal]").dataset.modal, false)));
    $$(".modal-backdrop").forEach(box => box.addEventListener("click", e => { if (e.target === box) modal(box.dataset.modal, false); }));
    document.addEventListener("keydown", e => { if (e.key === "Escape") $$(".modal-backdrop.is-open").forEach(box => modal(box.dataset.modal, false)); });
    $("[data-stock-search]").addEventListener("input", () => { if (ready) render(); });
    $$('[data-filter-category]').forEach(button => button.addEventListener("click", () => {
      category = button.dataset.filterCategory;
      $$('[data-filter-category]').forEach(other => other.classList.toggle("is-active", other === button));
      if (ready) render();
    }));
    $("[data-stock-retry]").addEventListener("click", () => load().catch(() => {}));
    $("[data-open-produto]").addEventListener("click", () => {
      const form = $("[data-produto-novo-form]"); form.reset(); errors(form); modal("produto-novo", true);
    });
    $("[data-open-entrada]").addEventListener("click", () => openMovement("entrada"));

    for (const [selector, name, creating] of [["[data-produto-novo-form]", "produto-novo", true], ["[data-produto-editar-form]", "produto-editar", false]]) {
      $(selector).addEventListener("submit", e => {
        e.preventDefault(); const form = e.currentTarget; const id = active?.id;
        save(form, name, () => api("/api/insumos" + (creating ? "" : "/" + id), {
          method: creating ? "POST" : "PUT", body: JSON.stringify(productPayload(form, creating)),
        }));
      });
    }
    for (const type of ["entrada", "saida", "ajuste"]) {
      $(`[data-${type}-form]`).addEventListener("submit", e => {
        e.preventDefault(); const form = e.currentTarget;
        const field = name => form.elements.namedItem(name)?.value;
        const product = type === "entrada" ? products.find(p => p.id === field("produto")) : active;
        if (!product) { errors(form, { produto: "Selecione um produto." }); return; }
        const payload = { tipo: type, quantidade: field("quantidade") === "" ? null : Number(field("quantidade")),
          fornecedor: field("fornecedor") || "", atividadeId: field("atividadeId") || "", observacao: field("observacao") || "",
          custoUnitario: field("custoUnitario") ? Number(field("custoUnitario")) : null };
        save(form, type, () => api(`/api/insumos/${product.id}/movimentacoes`, { method: "POST", body: JSON.stringify(payload) }));
      });
    }

    $("[data-stock-table] tbody").addEventListener("click", e => {
      const button = e.target.closest("[data-operation]"); if (!button) return;
      const product = products.find(p => p.id === button.closest("tr").dataset.id); if (!product) return;
      active = product;
      const operation = button.dataset.operation;
      if (operation === "detail") { details(product); return; }
      if (["entrada", "saida", "ajuste"].includes(operation)) { openMovement(operation, product); return; }
      if (operation === "editar") {
        const form = $("[data-produto-editar-form]"); errors(form);
        for (const field of ["nome", "categoria", "unidade", "minimo", "custoUnitario", "limiarCritico"])
          form.elements.namedItem(field).value = product[field] ?? "";
        form.elements.namedItem("categoria").dispatchEvent(new Event("change"));
        modal("produto-editar", true);
      }
      if (operation === "excluir") {
        $("[data-excluir-desc]").textContent = `${product.nome} sairá do catálogo se o saldo estiver zerado. O histórico será preservado.`;
        modal("produto-excluir", true);
      }
    });
    $("[data-confirm-excluir]").addEventListener("click", async e => {
      const button = e.currentTarget; if (button.disabled) return;
      const id = active?.id; button.disabled = true;
      try { await api(`/api/insumos/${id}`, { method: "DELETE" }); modal("produto-excluir", false); await load(); toast("success", "Produto excluído", "Histórico preservado."); }
      catch (error) { toast("error", "Não foi possível atualizar o catálogo", error.message); }
      finally { button.disabled = false; }
    });
    unavailable("Carregando estoque…");
    load().catch(() => {});
  }
  document.readyState === "loading" ? document.addEventListener("DOMContentLoaded", init) : init();
})();
