/* Miguel — RA: 25003110 */
/* Painel Geral: consulta os mesmos registros das telas operacionais.
 * Tudo é recuperado no navegador; nenhuma chamada ao backend é necessária.
 * Métricas e tabela são derivadas dos registros, sem contadores paralelos. */
(() => {
  "use strict";
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const local = window.AgroLocal;
  let activities = [];

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
    $('[data-metric="pending"]').textContent = String(activities.filter((record) => ['Pendente', 'Agendada'].includes(record.status)).length);
  }

  function refresh() {
    activities = local.read('activities');
    const plots = local.read('plots');
    const stock = local.read('stock');
    const occurrences = local.read('occurrences');
    $('[data-metric="areas"]').textContent = String(plots.filter((record) => record.status === 'Ativo').length);
    $('[data-metric="stock"]').textContent = String(stock.filter((record) => record.qty < record.min).length);
    const open = occurrences.filter((record) => record.status !== 'Resolvida');
    $('[data-alerts-count]').textContent = String(open.length);
    // Os alertas também mostram ocorrências reais, inclusive as criadas offline.
    const alertHost = $('.alerts-list');
    if (alertHost) {
      alertHost.replaceChildren(...open.map((record) => {
        const article = document.createElement('article');
        article.className = 'alert-item';
        const link = document.createElement('a');
        link.href = 'ocorrencias.html?talhao=' + encodeURIComponent(record.talhao);
        link.textContent = record.occ + ' · ' + record.talhao;
        article.appendChild(link);
        return article;
      }));
    }
    render();
  }

  function init() {
    refresh();
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
