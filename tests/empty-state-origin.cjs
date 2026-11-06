// Regressões: origem HTTPS, dados vazios, fonte local e falha temporária de sessão.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const {launchTestBrowser} = require('./browser-launch.cjs');
const {mockApi} = require('./mock-api.cjs');
const origin = 'https://agro.example:8443';
const root = path.resolve(__dirname, '../frontend');
(async () => {
  const browser = await launchTestBrowser(chromium);
  try {
    const context = await browser.newContext();
    const external = [], errors = [];
    await context.route('**/*', route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) { external.push(url.href); return route.abort(); }
      const file = path.resolve(root, '.' + url.pathname);
      if (!file.startsWith(root + path.sep) || !fs.existsSync(file)) return route.fulfill({status:404, body:''});
      const types = {'.js':'application/javascript', '.css':'text/css', '.html':'text/html', '.woff2':'font/woff2'};
      return route.fulfill({contentType: types[path.extname(file)] || 'application/octet-stream', body:fs.readFileSync(file)});
    });
    await context.addInitScript(() => {
      sessionStorage.setItem('agro.sessionToken','e2e-local');
      sessionStorage.setItem('agro.sessionUser',JSON.stringify({id:'e2e-local',propriedadeId:'e2e-property'}));
      // Cache antigo não deve voltar ao painel nem aos módulos operacionais.
      localStorage.setItem('agro.operational.v1.e2e-local.e2e-property.activities', JSON.stringify({version:1,data:[{activity:'Atividade fictícia antiga'}]}));
    });
    const api = await mockApi(context,origin,{visual:true});
    api.plots.splice(0);
    const page = await context.newPage();
    page.on('pageerror',error => errors.push(error.message));
    for (const screen of ['dashboard','areas-cultivo','atividades','insumos','ocorrencias']) {
      await page.goto(origin+'/'+screen+'.html');
      await page.waitForLoadState('networkidle');
      assert.equal(await page.evaluate(async () => {
        await document.fonts.ready;
        return document.fonts.check('16px Geist') && getComputedStyle(document.body).fontFamily.startsWith('Geist');
      }),true,'Geist carregada: '+screen);
      assert.equal(await page.locator('tbody tr, .kanban-card').count(),0,'conta vazia: '+screen);
      assert.ok(!(await page.locator('body').innerText()).includes('Atividade fictícia antiga'));
    }
    await context.route(origin+'/api/me',route => route.fulfill({status:503,contentType:'application/json',body:'{"error":"Indisponível"}'}));
    await page.goto(origin+'/dashboard.html');
    await page.waitForLoadState('networkidle');
    assert.equal(await page.evaluate(() => sessionStorage.getItem('agro.sessionToken')),'e2e-local');
    assert.ok(page.url().endsWith('/dashboard.html'),'falha temporária não desloga');
    await context.route(origin+'/api/me',route => route.fulfill({status:401,contentType:'application/json',body:'{}'}));
    await page.reload();
    await page.waitForURL('**/login.html');
    // addInitScript repõe a fixture ao navegar; verificamos o redirecionamento de 401.
    assert.deepEqual(external,[],'nenhuma chamada ao localhost/CDN em uma implantação HTTPS');
    assert.deepEqual(errors,[],'sem erros JavaScript');
    console.log('OK origem HTTPS, estados vazios, Geist local, cache legado ignorado, sessão 503/401');
  } finally { await browser.close(); }
})().catch(error => {console.error(error);process.exitCode=1;});
