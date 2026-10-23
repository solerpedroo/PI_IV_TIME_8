const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Contrato HTTP compartilhado pelas suítes de UI; os testes Java usam servidor real.
async function mockApi(context, origin) {
  const sandbox = {window: {}};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../scripts/data/operational-defaults.js'), 'utf8'), sandbox);
  const plots = sandbox.window.AgroOperationalDefaults.plots.map(item => ({id: item.id, nome: item.plot,
    cultura: item.crop.charAt(0).toUpperCase() + item.crop.slice(1), hectares: item.hectares,
    status: item.status, safra: '2025/26'}));
  const history = [];
  await context.route('http://127.0.0.1:8080/api/**', async route => {
    const req = route.request();
    const url = new URL(req.url());
    const headers = {'Access-Control-Allow-Origin': origin.replace(/\/$/, ''),
      'Access-Control-Allow-Headers': 'Authorization, Content-Type',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, OPTIONS'};
    const reply = (status, body) => route.fulfill({status, headers, contentType: 'application/json', body: JSON.stringify(body)});
    if (req.method() === 'OPTIONS') return route.fulfill({status: 204, headers});
    if (req.headers().authorization !== 'Bearer e2e-local') return reply(401, {error: 'Unauthorized'});
    if (url.pathname === '/api/me') return reply(200, {usuario: {id: 'e2e-local', propriedadeId: 'e2e-property'}});
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
  return {plots, history};
}
module.exports = {mockApi};
