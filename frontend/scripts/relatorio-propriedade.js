/* Miguel — RA: 25003110 */
// A tela e o CSV usam a mesma consulta, sem tratar falhas como dados vazios.
(() => {
  "use strict";
  const {escape, context, download} = window.AgroRelatorio;
  const modal=document.querySelector('[data-modal="property-report"]');
  const form=modal.querySelector('[data-property-report-form]');
  const content=modal.querySelector('[data-property-report-content]');
  const message=modal.querySelector('[data-property-report-message]');
  const exportButton=modal.querySelector('[data-export-property-report]');
  const base=location.port === "8080" ? location.origin : "http://127.0.0.1:8080";
  let snapshot=null, generation=0, loadedContext="", opener=null;
  const moeda=value => new Intl.NumberFormat("pt-BR", {style:"currency",currency:"BRL"}).format(value/100);
  function clear(text) { snapshot=null; exportButton.disabled=true; content.replaceChildren(); message.textContent=text; }
  function close() { generation++; clear(""); modal.classList.remove("is-open"); modal.setAttribute("aria-hidden","true"); opener?.focus(); }
  function lines(items, pending) {
    return items.length ? items.map(item => `<p>${escape(item.tipo)} · ${escape(item.descricao)} · ${escape(item.data || "Sem data")} ${item.valorCentavos != null ? `· ${escape(moeda(item.valorCentavos))}` : ""} ${pending ? `· ${escape(item.motivo)}` : ""}</p>`).join("") : `<p>${pending ? "Nenhuma pendência." : "Nenhum registro no período."}</p>`;
  }
  function render(r) {
    content.innerHTML=`<h3>${escape(r.propriedade.nome)}</h3><p>Período inclusivo: ${escape(r.periodo.inicio)} a ${escape(r.periodo.fim)}</p><h3>Situação atual</h3><p>Talhões: ${r.atuais.talhoes} · Área atual dos talhões: ${r.atuais.areaHectares} ha · Safras: ${r.atuais.safras} · Produtos em estoque: ${r.atuais.produtos}</p><h3>Registros no período</h3><p>Atividades: ${r.contagens.atividade} · Ocorrências: ${r.contagens.ocorrencia} · Custos: ${r.contagens.custo} · Total: <strong data-property-total>${escape(moeda(r.totalCentavos))}</strong></p>${lines(r.registros,false)}<h3>Pendências (${r.pendencias.length})</h3>${lines(r.pendencias,true)}`;
  }
  function csv(r) {
    const rows=[["Propriedade",r.propriedade.nome,r.propriedade.id],["Período inclusivo",r.periodo.inicio,r.periodo.fim],
      ["Talhões atuais",r.atuais.talhoes],["Área atual dos talhões (ha)",r.atuais.areaHectares],["Safras atuais",r.atuais.safras],["Produtos atuais em estoque",r.atuais.produtos],
      ["Atividades no período",r.contagens.atividade],["Ocorrências no período",r.contagens.ocorrencia],["Custos no período",r.contagens.custo],["Total de custos (BRL)",(r.totalCentavos/100).toFixed(2)],
      ["Conjunto","Tipo","ID","Descrição","Data","Valor (BRL)","Motivo"]];
    for (const [label, items] of [["Registro",r.registros],["Pendência",r.pendencias]]) for (const item of items)
      rows.push([label,item.tipo,item.id,item.descricao,item.data,item.valorCentavos == null ? "" : (item.valorCentavos/100).toFixed(2),item.motivo]);
    return rows;
  }
  document.querySelector('[data-open-property-report]').addEventListener('click', event => {
    opener=event.currentTarget; modal.classList.add('is-open'); modal.setAttribute('aria-hidden','false'); form.elements.de.focus();
  });
  modal.querySelector('[data-close-property-report]').addEventListener('click',close);
  modal.addEventListener('click',event=>{if(event.target===modal) close();});
  modal.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();event.stopPropagation();close();}});
  form.addEventListener('input',()=>{generation++;clear('Altere o período e consulte novamente.');});
  form.addEventListener('submit', async event => {
    event.preventDefault(); const de=form.elements.de.value, ate=form.elements.ate.value;
    const current=++generation; loadedContext=context(); const expected=loadedContext;
    clear('Consultando relatório…');
    if (!de || !ate || ate<de) {clear('Informe um período válido, com início até o fim.');return;}
    try {
      const query=new URLSearchParams({de,ate});
      const response=await fetch(`${base}/api/relatorios/propriedade?${query}`,{headers:{Authorization:'Bearer '+sessionStorage.getItem('agro.sessionToken')},cache:'no-store'});
      const result=await response.json();
      if(current!==generation)return;
      if(expected!==context())throw new Error('A sessão ou propriedade mudou. Consulte novamente.');
      if(!response.ok)throw new Error(result.error || 'Não foi possível consultar o relatório.');
      const user=JSON.parse(sessionStorage.getItem('agro.sessionUser') || 'null');
      if(result.propriedade.id!==user?.propriedadeId)throw new Error('A propriedade mudou. Recarregue a página.');
      snapshot=result; render(result); message.textContent='Consulta concluída.'; exportButton.disabled=false;
    } catch(error) {if(current===generation)clear(error.message || 'Relatório indisponível.');}
  });
  exportButton.addEventListener('click',()=>{
    if(context()!==loadedContext){clear('A sessão ou propriedade mudou. Consulte novamente.');return;}
    if(snapshot)download(csv(snapshot),`agrogestao-propriedade-${snapshot.propriedade.id}.csv`);
  });
  window.addEventListener('focus',()=>{if(snapshot && context()!==loadedContext)clear('A sessão ou propriedade mudou. Consulte novamente.');});
})();
