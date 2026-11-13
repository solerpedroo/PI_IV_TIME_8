/* Autor: Tomas de Paula Michelon Toniato - RA: 25004211 */
/* Identidade visual AgroGestao: simbolo, favicon e nome em todas as telas.
 * Sobe so o frontend estatico (sem Java e sem API) e confere no Chromium que
 * as imagens da marca carregam e que a marca antiga (folha generica) saiu. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const { launchTestBrowser } = require('./browser-launch.cjs');

const root = path.resolve(__dirname, '../frontend');
const mime = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'application/javascript; charset=utf-8', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.jpg': 'image/jpeg' };
const telasApp = ['dashboard', 'areas-cultivo', 'atividades', 'insumos', 'ocorrencias', 'custos', 'configuracoes', 'safras', 'culturas', 'manutencao'];
const telasAuth = ['login', 'criar-conta', 'recuperar-senha', 'redefinir-senha', 'confirmar-email'];

function servir() {
  return new Promise((resolve) => {
    const srv = http.createServer((req, res) => {
      const arquivo = path.join(root, decodeURIComponent(req.url.split('?')[0]));
      if (!arquivo.startsWith(root) || !fs.existsSync(arquivo) || fs.statSync(arquivo).isDirectory()) {
        res.statusCode = 404;
        res.end();
        return;
      }
      res.setHeader('content-type', mime[path.extname(arquivo)] || 'application/octet-stream');
      res.end(fs.readFileSync(arquivo));
    });
    srv.listen(0, '127.0.0.1', () => resolve(srv));
  });
}

(async () => {
  for (const arq of ['simbolo.svg', 'simbolo-claro.svg', 'favicon.svg']) {
    assert.ok(fs.existsSync(path.join(root, 'Assets/brand', arq)), 'falta Assets/brand/' + arq);
  }
  const srv = await servir();
  const base = 'http://127.0.0.1:' + srv.address().port;
  const browser = await launchTestBrowser(chromium);
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    const brandFalhou = [];
    page.on('response', (r) => {
      if (r.url().includes('/Assets/brand/') && r.status() !== 200) brandFalhou.push(r.url());
    });
    await page.addInitScript(() => {
      if (!location.pathname.match(/(login|criar-conta|recuperar-senha|redefinir-senha|confirmar-email)\.html$/)) {
        sessionStorage.setItem('agro.sessionToken', 'e2e-local');
        sessionStorage.setItem('agro.sessionUser', JSON.stringify({ id: 'e2e-local', propriedadeId: 'e2e-property' }));
      }
    });
    await page.route('**/api/**',(route) => route.fulfill({ status: 503, contentType: 'application/json', body: '{}' }));

    for (const tela of telasApp.concat(telasAuth)) {
      await page.goto(base + '/' + tela + '.html', { waitUntil: 'load' });
      assert.equal(await page.locator('link[rel="icon"][href="Assets/brand/favicon.svg"]').count(), 1, 'favicon ausente: ' + tela);
      assert.match(await page.title(), /AgroGestão$/, 'titulo sem o nome da marca: ' + tela);
      assert.doesNotMatch(await page.title(), /\u2014/, 'titulo com travessao: ' + tela);
      if (telasApp.includes(tela)) {
        await page.waitForSelector('.sidebar-brand-icon img', { timeout: 10000 });
        assert.equal(await page.locator('.sidebar-brand-name').innerText(), 'AgroGestão', 'nome na sidebar: ' + tela);
      }
      const imgs = page.locator('.brand-img, .sidebar-brand-icon img');
      const n = await imgs.count();
      for (let i = 0; i < n; i += 1) {
        assert.ok(await imgs.nth(i).evaluate((el) => el.complete && el.naturalWidth > 0), 'simbolo nao carregou: ' + tela);
      }
      const antigo = await page.locator('.brand-mark svg, .sidebar-brand-icon svg').count();
      assert.equal(antigo, 0, 'folha generica antiga ainda presente: ' + tela);
      console.log('OK ' + tela + ': marca nova (' + n + ' simbolo(s))');
    }
    assert.deepEqual(brandFalhou, [], 'arquivos de Assets/brand com erro');

    await page.goto(base + '/login.html', { waitUntil: 'load' });
    assert.equal(await page.locator('.auth-aside .brand-img').getAttribute('src'), 'Assets/brand/simbolo-claro.svg', 'painel escuro usa simbolo claro');
    await page.goto(base + '/recuperar-senha.html', { waitUntil: 'load' });
    assert.equal(await page.locator('.center-brand .brand-img').getAttribute('src'), 'Assets/brand/simbolo.svg', 'fundo claro usa simbolo colorido');
    console.log('OK variantes do simbolo por fundo');
  } finally {
    await browser.close();
    srv.close();
  }
})().catch((erro) => {
  console.error(erro);
  process.exit(1);
});
