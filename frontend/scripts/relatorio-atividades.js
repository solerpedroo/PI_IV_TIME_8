/* Miguel — RA: 25003110 */
// Atraso é um indicador adicional e pode coexistir com pendente.
window.AgroRelatorioOperacional({
  path:'atividades',
  render(r,esc){
    const atividades=r.registros.length?r.registros.map(x=>`<p>${esc(x.data)} · ${esc(x.nome)} · ${esc(x.talhao||'Sem talhão')} · ${esc(x.status)}${x.atrasada?' · Atrasada':''}</p>`).join(''):'<p>Nenhuma atividade no período.</p>';
    const pendencias=r.pendencias.map(x=>`<p>${esc(x.nome)} · ${esc(x.data||'Sem data')} · ${esc(x.motivo)}</p>`).join('');
    return `<h3>${esc(r.propriedade.nome)}</h3><p>Datas planejadas: ${esc(r.periodo.inicio)} a ${esc(r.periodo.fim)} · inclusivo</p><p>Status atual e atraso em ${esc(r.dataConsulta)} (${esc(r.fuso)}). Atraso pode coincidir com pendente.</p><p>Concluídas: ${r.contagens.concluidas} · Pendentes: ${r.contagens.pendentes} · Atrasadas: ${r.contagens.atrasadas}</p>${atividades}<h3>Pendências (${r.pendencias.length})</h3>${pendencias}`;
  },
  rows(r){
    const rows=[['Propriedade',r.propriedade.nome,r.propriedade.id],['Período planejado inclusivo',r.periodo.inicio,r.periodo.fim],['Data da consulta',r.dataConsulta,r.fuso],['Concluídas',r.contagens.concluidas],['Pendentes',r.contagens.pendentes],['Atrasadas',r.contagens.atrasadas],['Conjunto','ID','Atividade','Talhão','Safra ID','Data planejada','Status atual','Concluída','Pendente','Atrasada','Motivo']];
    for(const [label,items] of [['Atividade',r.registros],['Pendência',r.pendencias]])for(const x of items)rows.push([label,x.id,x.nome,x.talhao,x.safraId,x.data,x.status,x.concluida?'Sim':'Não',x.pendente?'Sim':'Não',x.atrasada?'Sim':'Não',x.motivo]);
    return rows;
  }
});
