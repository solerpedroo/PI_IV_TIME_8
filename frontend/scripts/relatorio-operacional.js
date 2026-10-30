/* Miguel — RA: 25003110 */
// Um controlador comum mantém consulta, segurança e exportação coerentes nas telas.
window.AgroRelatorioOperacional = function iniciar(config) {
  const {escape,context,download}=window.AgroRelatorio;
  const modal=document.querySelector('[data-modal="report-operational"]'), form=modal.querySelector('form');
  const out=modal.querySelector('[data-report-output]'), msg=modal.querySelector('[data-report-message]'), btn=modal.querySelector('[data-report-export]');
  const base=location.port==='8080' ? location.origin : 'http://127.0.0.1:8080';
  let snapshot=null, geracao=0, contexto='', opener;
  function limpar(texto) {snapshot=null;btn.disabled=true;out.replaceChildren();msg.textContent=texto;}
  function fechar(){geracao++;limpar('');modal.classList.remove('is-open');modal.setAttribute('aria-hidden','true');opener?.focus();}
  document.querySelector('[data-report-open]').addEventListener('click',e=>{opener=e.currentTarget;modal.classList.add('is-open');modal.setAttribute('aria-hidden','false');form.elements.de.focus();});
  modal.querySelector('[data-report-close]').addEventListener('click',fechar);
  modal.addEventListener('click',e=>{if(e.target===modal)fechar();});
  modal.addEventListener('keydown',e=>{if(e.key==='Escape'){e.preventDefault();e.stopPropagation();fechar();}});
  form.addEventListener('input',()=>{geracao++;limpar('Consulte novamente após alterar o período.');});
  form.addEventListener('submit',async e=>{
    e.preventDefault(); const de=form.elements.de.value,ate=form.elements.ate.value, atual=++geracao;
    contexto=context(); const esperado=contexto; limpar('Consultando relatório…');
    if(!de||!ate||ate<de){limpar('Informe um período válido.');return;}
    try {
      const query=new URLSearchParams({de,ate});
      const response=await fetch(`${base}/api/relatorios/${config.path}?${query}`,{headers:{Authorization:'Bearer '+sessionStorage.getItem('agro.sessionToken')},cache:'no-store'});
      const data=await response.json(); if(atual!==geracao)return;
      if(esperado!==context())throw new Error('A sessão ou propriedade mudou. Consulte novamente.');
      if(!response.ok)throw new Error(data.error||'Não foi possível consultar.');
      const user=JSON.parse(sessionStorage.getItem('agro.sessionUser')||'null');
      if(data.propriedade.id!==user?.propriedadeId)throw new Error('A propriedade mudou. Recarregue a página.');
      snapshot=data; out.innerHTML=config.render(data,escape);msg.textContent='Consulta concluída.';btn.disabled=false;
    }catch(error){if(atual===geracao)limpar(error.message||'Relatório indisponível.');}
  });
  btn.addEventListener('click',()=>{if(contexto!==context()){limpar('A sessão ou propriedade mudou. Consulte novamente.');return;}if(snapshot)download(config.rows(snapshot),`agrogestao-${config.path}-${snapshot.propriedade.id}.csv`);});
  window.addEventListener('focus',()=>{if(snapshot&&contexto!==context())limpar('A sessão ou propriedade mudou. Consulte novamente.');});
};
