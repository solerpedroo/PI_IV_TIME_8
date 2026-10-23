const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const {test} = require('node:test');

test('dados locais separados por conta e propriedade, inclusive após logout', () => {
  const local = new Map();
  function page(id, propriedadeId) {
    const session = new Map([['agro.sessionUser', JSON.stringify({id, propriedadeId})]]);
    const window = {localStorage: {
      getItem: key => local.get(key) ?? null,
      setItem: (key, value) => local.set(key, value),
    }};
    const context = vm.createContext({window, sessionStorage: {getItem: key => session.get(key) ?? null},
      document: {readyState: 'loading', addEventListener() {}}});
    for (const file of ['scripts/data/operational-defaults.js', 'scripts/agro-local.js']) {
      vm.runInContext(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'), context);
    }
    return {api: window.AgroLocal, session};
  }
  local.set('agro.operational.v1.notifications', JSON.stringify({version: 1, data: ['Legado sem dono']}));
  const a = page('conta-a', 'fazenda-a');
  assert.ok(!a.api.read('notifications').includes('Legado sem dono'));
  assert.equal(a.api.save('notifications', ['Privado A']), true);
  const b = page('conta-b', 'fazenda-a');
  assert.ok(!b.api.read('notifications').includes('Privado A'));
  assert.equal(b.api.save('notifications', ['Privado B']), true);
  assert.ok(!page('conta-a', 'fazenda-b').api.read('notifications').includes('Privado A'));
  assert.equal(page('conta-a', 'fazenda-a').api.read('notifications')[0], 'Privado A');
  a.session.set('agro.sessionUser', JSON.stringify({id: 'conta-a', propriedadeId: 'fazenda-b'}));
  assert.equal(a.api.save('notifications', ['Gravação da aba antiga']), false);
  a.session.delete('agro.sessionUser');
  assert.equal(a.api.save('notifications', ['Após logout']), false);
  assert.ok(local.has('agro.operational.v1.notifications'), 'legado deve continuar recuperável');
});
