/* Miguel — RA: 25003110 */
// Ajustes são mostrados separados: sua quantidade é o saldo contado, não uma entrada.
window.AgroRelatorioOperacional({
  path:'estoque',
  render(r,esc){
    const saldos=r.saldos.length?r.saldos.map(x=>`<p>${esc(x.nome)}: ${esc(x.saldo)} ${esc(x.unidade)}</p>`).join(''):'<p>Nenhum produto ativo.</p>';
    const movimentos=r.movimentos.length?r.movimentos.map(x=>`<p>${esc(x.data)} · ${esc(x.produto)}${x.arquivado?' (arquivado)':''} · ${esc(x.tipo)} · ${esc(x.quantidade)} ${esc(x.unidade)} · Saldo: ${esc(x.saldoAnterior)} → ${esc(x.saldoPosterior)}</p>`).join(''):'<p>Nenhuma movimentação no período.</p>';
    const pendencias=r.pendencias.map(x=>`<p>${esc(x.id)} · ${esc(x.motivo)}</p>`).join('');
    return `<h3>${esc(r.propriedade.nome)}</h3><p>Período inclusivo: ${esc(r.periodo.inicio)} a ${esc(r.periodo.fim)} · ${esc(r.fuso)}</p><h3>Saldos atuais</h3>${saldos}<h3>Movimentações no período</h3><p>Ajuste indica saldo contado, incluindo saldo inicial.</p>${movimentos}<h3>Pendências (${r.pendencias.length})</h3>${pendencias}`;
  },
  rows(r){
    const rows=[['Propriedade',r.propriedade.nome,r.propriedade.id],['Período inclusivo',r.periodo.inicio,r.periodo.fim,r.fuso],['Saldo atual','Produto ID','Produto','Saldo','Unidade']];
    for(const x of r.saldos)rows.push(['Saldo atual',x.id,x.nome,x.saldo,x.unidade]);
    rows.push(['Conjunto','ID','Produto ID','Produto','Arquivado','Data','Tipo','Quantidade ou saldo contado','Unidade','Saldo anterior','Saldo posterior','Motivo']);
    for(const [label,items] of [['Movimento',r.movimentos],['Pendência',r.pendencias]])for(const x of items)rows.push([label,x.id,x.produtoId,x.produto,x.arquivado?'Sim':'Não',x.data||x.criadoEm,x.tipo,x.quantidade,x.unidade,x.saldoAnterior,x.saldoPosterior,x.motivo]);
    return rows;
  }
});
