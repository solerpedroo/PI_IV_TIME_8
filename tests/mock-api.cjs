/* Miguel — RA: 25003110 */
/* Autor Secundário: Pedro Henrique Contardi Soler - 25005592 */
const fs = require('node:fs');
/* Autor secundário: Pedro Henrique Contardi Soler - 25005592 */
const path = require('node:path');
const vm = require('node:vm');

// Contrato HTTP compartilhado pelas suítes de UI; os testes Java usam servidor real.
async function mockApi(context, origin, options = {}) {
  const sandbox = {window: {}};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../frontend/scripts/data/operational-defaults.js'), 'utf8'), sandbox);
  const plots = sandbox.window.AgroOperationalDefaults.plots.map(item => ({id: item.id, nome: item.plot,
    cultura: item.crop.charAt(0).toUpperCase() + item.crop.slice(1), hectares: item.hectares,
    status: item.status, safra: '2025/26'}));
  const history = [];
  const activities = [];
  const properties = [{id: 'e2e-property', nome: 'Fazenda Santa Rita', areaHectares: 1284,
    localizacao: 'Rodovia SP-101, km 12', municipio: 'Campinas, SP', safraAtual: '2025/26', selecionada: true}];
  const user = {id: 'e2e-local', name: 'João Silva', email: 'joao@fazendasantarita.com', phone: '(19) 99999-1234',
    propriedadeId: 'e2e-property', propriedadePadraoId: 'e2e-property', emailConfirmado: true};
  const preferences = {stock: true, weather: true, activities: true, occurrences: true, reports: false};
  await context.route('http://127.0.0.1:8080/api/**', async route => {
    const req = route.request();
    const url = new URL(req.url());
    const headers = {'Access-Control-Allow-Origin': origin.replace(/\/$/, ''),
      'Access-Control-Allow-Headers': 'Authorization, Content-Type',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, OPTIONS'};
    const reply = (status, body) => route.fulfill({status, headers, contentType: 'application/json', body: JSON.stringify(body)});
    if (req.method() === 'OPTIONS') return route.fulfill({status: 204, headers});
    if (req.headers().authorization !== 'Bearer e2e-local') return reply(401, {error: 'Unauthorized'});
    // A revisão visual usa respostas vazias válidas para telas já integradas.
    if (options.visual && req.method() === 'GET') {
      if (['/api/atividades', '/api/safras', '/api/insumos', '/api/insumos/atividades'].includes(url.pathname)) return reply(200, []);
      if (url.pathname === '/api/insumos/resumo') return reply(200, {totalProdutos:0,criticos:0,valorEmEstoque:0,entradasNoMes:0});
    }
    if (options.activities) {
      if (url.pathname === '/api/safras' && req.method() === 'GET') return reply(200, []);
      if (url.pathname === '/api/atividades' && req.method() === 'GET') return reply(200, activities);
      if (url.pathname === '/api/atividades' && req.method() === 'POST') {
        const item = {...req.postDataJSON(), id: 'activity-' + (activities.length + 1), status: 'Pendente'};
        activities.push(item); return reply(201, item);
      }
      const match = url.pathname.match(/^\/api\/atividades\/([^/]+)(?:\/(status|historico))?$/);
      if (match) {
        const item = activities.find(a => a.id === match[1]);
        if (!item) return reply(404, {error:'Atividade não encontrada'});
        if (match[2] === 'historico') return reply(200, []);
        if (match[2] === 'status' && req.method() === 'POST') {
          item.status = req.postDataJSON().status; return reply(200, item);
        }
        if (!match[2] && req.method() === 'PUT') { Object.assign(item, req.postDataJSON()); return reply(200, item); }
      }
    }
    // Listagens vazias válidas para o sino e telas que consultam sem CRUD neste mock.
    if (req.method() === 'GET' && url.pathname === '/api/atividades' && !options.activities) return reply(200, []);
    if (req.method() === 'GET' && url.pathname === '/api/insumos') return reply(200, options.insumos || []);
    if (req.method() === 'GET' && url.pathname === '/api/insumos/resumo') {
      return reply(200, {totalProdutos: 0, criticos: 0, valorEmEstoque: 0, entradasNoMes: 0});
    }
    if (req.method() === 'GET' && url.pathname === '/api/insumos/atividades') return reply(200, []);
    // Apenas inicialização das telas: o CRUD de ocorrências é testado no Java real.
    if (req.method() === 'GET' && url.pathname === '/api/ocorrencias') return reply(200, options.occurrences || []);
    if (req.method() === 'GET' && url.pathname === '/api/ocorrencias/resumo') return reply(200,
      {abertas: 0, emAcompanhamento: 0, resolvidasMes: 0, prioridadeAlta: 0, mes: url.searchParams.get('mes')});
    if (url.pathname === '/api/me') {
      const ativa = properties.find(item => item.selecionada);
      return reply(200, {usuario: {...user, propriedadeId: ativa.id}, propriedade: {id: ativa.id, farm: ativa.nome}});
    }
    if (url.pathname === '/api/perfil') {
      if (req.method() === 'PUT') {
        const body = req.postDataJSON();
        Object.assign(user, {name: body.name, email: body.email, phone: body.phone});
      }
      return reply(200, {usuario: user, preferencias: preferences});
    }
    if (url.pathname === '/api/perfil/preferencias' && req.method() === 'PUT') {
      Object.assign(preferences, req.postDataJSON()); return reply(200, {preferencias: preferences});
    }
    if (url.pathname === '/api/perfil/propriedade-padrao' && req.method() === 'PUT') {
      user.propriedadePadraoId = req.postDataJSON().propriedadeId;
      return reply(200, {propriedadePadraoId: user.propriedadePadraoId});
    }
    if (url.pathname === '/api/propriedades' && req.method() === 'GET') return reply(200, properties);
    if (url.pathname === '/api/propriedades' && req.method() === 'POST') {
      const item = {id: 'property-' + properties.length, ...req.postDataJSON(), selecionada: false};
      properties.push(item);
      return reply(201, item);
    }
    if (url.pathname.startsWith('/api/propriedades/')) {
      const item = properties.find(property => property.id === url.pathname.split('/')[3]);
      if (!item) return reply(404, {error: 'Not found'});
      if (req.method() === 'PUT') { Object.assign(item, req.postDataJSON()); return reply(200, item); }
      if (req.method() === 'POST' && url.pathname.endsWith('/selecionar')) {
        properties.forEach(property => { property.selecionada = property === item; });
        return reply(200, {propriedadeId: item.id});
      }
      return reply(200, item);
    }
    if (url.pathname.endsWith('/historico')) {
      if (req.method() === 'POST') { history.push(req.postDataJSON()); return reply(201, history.at(-1)); }
      return reply(200, history);
    }
    if (url.pathname === '/api/talhoes' && req.method() === 'GET') return reply(200, plots);
    if (url.pathname.startsWith('/api/talhoes') && ['POST', 'PUT'].includes(req.method())) {
      const body = req.postDataJSON();
      const id = req.method() === 'POST' ? 'plot-' + plots.length : url.pathname.split('/').at(-1);
      if (plots.some(item => item.id !== id && item.nome.toLowerCase() === body.nome.toLowerCase())) return reply(409, {error: 'Nome repetido'});
      const item = {id, ...body};
      const index = plots.findIndex(plot => plot.id === id);
      if (index < 0) plots.push(item); else plots[index] = item;
      return reply(req.method() === 'POST' ? 201 : 200, item);
    }
    return reply(404, {error: 'Not found'});
  });
  return {plots, history, properties, user, preferences};
}
module.exports = {mockApi};
