/* Miguel — RA: 25003110 */
/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */
/*
 * Painel Geral: cada indicador e cada lista é derivado dos mesmos registros
 * compartilhados pelos módulos operacionais. Não existem contadores paralelos:
 * ao salvar uma atividade, custo, ocorrência ou movimentação, o painel a lê
 * novamente e recalcula a interface.
 */
(() => {
  "use strict";
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const local = window.AgroLocal;
  const money = new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL'});
  let activities = [];

  // Centraliza a escrita para que a ausência de um card não interrompa o painel.
  function setMetric(name, value) {
    const target = $(`[data-metric="${name}"]`);
    if (target) target.textContent = value;
  }

  function render() {
    const search = ($('[data-dashboard-search]')?.value || '').trim().toLowerCase();
    const body = $('[data-activities-table] tbody');
    body.replaceChildren(...activities.map((activity) => {
      const row = document.createElement('tr');
      row.dataset.id = activity.id;
      row.hidden = !!search && !`${activity.activity} ${activity.talhao}`.toLowerCase().includes(search);
      ['activity', 'talhao', 'date', 'status'].forEach((key, index) => {
        const cell = document.createElement('td');
        cell.className = ['col-activity', 'col-plot', 'col-date', 'col-status'][index];
        if (key === 'status') {
          const badge = document.createElement('span');
          badge.className = 'badge ' + (activity.status === 'Concluída' ? 'badge-success' : 'badge-warning');
          badge.textContent = activity.status;
          cell.appendChild(badge);
        } else cell.textContent = activity[key];
        if (index === 0 && activity.status !== 'Concluída') {
          const actions = document.createElement('span');
          actions.className = 'row-actions';
          const button = document.createElement('button');
          button.type = 'button';
          button.className = 'row-action';
          button.dataset.completeActivity = '';
          button.textContent = 'Concluir';
          actions.appendChild(button);
          cell.appendChild(actions);
        }
        row.appendChild(cell);
      });
      return row;
    }));
    setMetric('pending', String(activities.filter((record) => ['Pendente', 'Agendada'].includes(record.status)).length));
  }

  // Alertas são links para a ocorrência e o talhão que lhes deram origem.
  function renderAlerts(occurrences) {
    const open = occurrences.filter((record) => record.status !== 'Resolvida');
    const alertHost = $('.alerts-list');
    const counter = $('[data-alerts-count]');
    if (counter) counter.textContent = String(open.length);
    if (!alertHost) return;

    alertHost.replaceChildren(...open.map((record) => {
      const article = document.createElement('article');
      article.className = 'alert-item';
      const link = document.createElement('a');
      link.href = 'ocorrencias.html?talhao=' + encodeURIComponent(record.talhao);
      link.className = 'alert-link';
      link.textContent = `${record.occ} · ${record.talhao}`;
      article.appendChild(link);
      return article;
    }));
  }

  // Produção é representada pela área cadastrada para cada cultura nos talhões.
  function renderCultures(plots) {
    const host = $('[data-cultures-list]');
    if (!host) return;
    const areas = new Map();
    plots.forEach((plot) => areas.set(plot.crop, (areas.get(plot.crop) || 0) + plot.hectares));
    const total = [...areas.values()].reduce((sum, hectares) => sum + hectares, 0);
    const tones = ['is-primary', 'is-secondary', 'is-accent', 'is-muted'];

    host.replaceChildren(...[...areas.entries()].map(([crop, hectares], index) => {
      const item = document.createElement('article');
      item.className = 'culture-item';
      const percent = total ? Math.round((hectares / total) * 100) : 0;
      const name = crop.charAt(0).toUpperCase() + crop.slice(1);
      item.innerHTML = `<div class="culture-meta"><span class="culture-name"></span><span class="culture-area"></span></div><div class="culture-track" aria-hidden="true"><div class="culture-fill ${tones[index % tones.length]}"></div></div><span class="culture-progress"></span>`;
      $('.culture-name', item).textContent = name;
      $('.culture-area', item).textContent = `${hectares} ha`;
      $('.culture-fill', item).style.width = `${percent}%`;
      $('.culture-progress', item).textContent = `${percent}% da área cadastrada`;
      return item;
    }));
  }

  function refresh() {
    activities = local.read('activities');
    const plots = local.read('plots');
    const stock = local.read('stock');
    const occurrences = local.read('occurrences');
    const costs = local.read('costs');
    const totalCosts = costs.entries.reduce((total, entry) => total + entry.value, 0);

    setMetric('areas', String(plots.filter((record) => record.status === 'Ativo').length));
    setMetric('stock', String(stock.filter((record) => record.qty < record.min).length));
    setMetric('cost', money.format(totalCosts));
    renderAlerts(occurrences);
    renderCultures(plots);
    render();
  }

  // Os quatro cards são atalhos de contexto e funcionam por mouse e teclado.
  function initMetricNavigation() {
    $$('[data-dashboard-link]').forEach((card) => {
      const navigate = () => { window.location.href = card.dataset.dashboardLink; };
      card.addEventListener('click', navigate);
      card.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          navigate();
        }
      });
    });
  }

  function init() {
    refresh();
    initMetricNavigation();
    const persist = () => local.save('activities', activities);
    const modal = $('[data-modal="new-activity"]');
    const close = () => { modal.classList.remove('is-open'); modal.setAttribute('aria-hidden', 'true'); };
    $('[data-open-new-activity]')?.addEventListener('click', () => {
      modal.classList.add('is-open'); modal.setAttribute('aria-hidden', 'false');
      $('input', modal)?.focus();
    });
    $$('[data-close-modal]', modal).forEach((button) => button.addEventListener('click', close));
    modal.addEventListener('click', (event) => { if (event.target === modal) close(); });
    $('[data-new-activity-form]').addEventListener('submit', (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      const name = form.elements['activity-name'].value.trim();
      const plot = form.elements['activity-plot'].selectedOptions[0]?.textContent.trim() || '';
      if (!name || !form.elements['activity-plot'].value) {
        window.AgroToast?.show({type: 'error', title: 'Falha ao salvar', description: 'Preencha o nome e o talhão da atividade.'});
        return;
      }
      const previous = activities.slice();
      activities.push({id: local.id(), activity: name, tipo: 'Manutenção', talhao: plot,
        date: new Date().toLocaleDateString('pt-BR'), status: 'Pendente'});
      if (!persist()) { activities = previous; render(); return; }
      render(); close(); form.reset();
      window.AgroToast?.show({type: 'success', title: 'Atividade registrada', description: name + ' salva neste navegador.'});
    });
    $('[data-activities-table]').addEventListener('click', (event) => {
      const button = event.target.closest('[data-complete-activity]');
      if (!button) return;
      const record = activities.find((activity) => activity.id === button.closest('tr').dataset.id);
      if (!record) return;
      const previous = record.status;
      record.status = 'Concluída';
      if (!persist()) { record.status = previous; render(); return; }
      render();
      window.AgroToast?.show({type: 'success', title: 'Status atualizado', description: record.activity + ' marcada como concluída.'});
    });
    $('[data-dashboard-search]')?.addEventListener('input', render);
    // Outra aba pode alterar os dados enquanto o painel estiver aberto.
    window.addEventListener('storage', refresh);
    window.addEventListener('pageshow', refresh);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
