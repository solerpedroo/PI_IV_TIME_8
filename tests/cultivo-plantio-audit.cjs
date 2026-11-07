const assert = require('node:assert/strict');
const {spawn} = require('node:child_process');
const path = require('node:path');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = 'http://127.0.0.1:18080';
(async () => {
 const server = spawn('java',['-cp','bin:lib/*','Servidor','18080','--memoria'],{cwd:path.join(__dirname,'../backend/api'),env:{...process.env,SMTP_HOST:'',SMTP_PORT:'',SMTP_USERNAME:'',SMTP_PASSWORD:'',SMTP_FROM:''}});
 let log=''; server.stdout.on('data',x=>log+=x); server.stderr.on('data',x=>log+=x);
 let browser;
 try {
  for(let i=0;i<100;i++){try{if((await fetch(base+'/health')).ok)break;}catch{} await new Promise(r=>setTimeout(r,100));}
  const api=async(url,body,token)=>{const r=await fetch(base+url,{method:body?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body?JSON.stringify(body):undefined});const data=await r.json();assert.ok(r.ok,JSON.stringify(data));return data;};
  await api('/api/auth/register',{name:'Auditoria Front',email:'audit@agro.test',phone:'(19) 99999-1234',password:'Senha@123',confirm:'Senha@123',farm:'Fazenda de teste',area:'50 ha',terms:true});
  await api('/api/auth/confirm-email',{token:log.match(/token: ([a-zA-Z0-9-]+)/)[1]});
  const session=await api('/api/auth/login',{email:'audit@agro.test',password:'Senha@123'});
  browser=await chromium.launch({headless:true});
  const context=await browser.newContext({viewport:{width:1440,height:1000}});
  await context.addInitScript(s=>{sessionStorage.setItem('agro.sessionToken',s.token);sessionStorage.setItem('agro.sessionUser',JSON.stringify(s.usuario));},session);
  const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const shot=async name=>{await page.waitForTimeout(500);if(process.env.AUDIT_SCREENSHOTS)await page.screenshot({path:path.join(process.env.AUDIT_SCREENSHOTS,name+'.png'),fullPage:true,animations:'disabled'});};
  await page.goto(base+'/areas-cultivo.html'); await page.waitForTimeout(500);
  assert.equal(await page.locator('[data-plots-table] tbody tr').count(),0);await shot('areas-vazia');
  const plot=await api('/api/talhoes',{nome:'Talhão de auditoria',cultura:'Milho',hectares:12,safra:'2026/27',status:'Ativo'},session.token);
  await page.reload();await page.locator('[data-edit-plot]').first().waitFor();await shot('areas-desktop');
  assert.ok((await page.locator('[data-plots-table] tbody').textContent()).includes('2026/27'));
  await page.locator('[data-filter-crop="status:Em preparo"]').click();assert.equal(await page.locator('[data-plots-table] tbody tr:visible').count(),0);
  await api('/api/talhoes',{nome:'Área em preparo',cultura:'Soja',hectares:2,safra:'2026/27',status:'Em preparo'},session.token);
  await page.reload();await page.locator('[data-edit-plot]').first().waitFor();
  await page.locator('[data-filter-crop="status:Em preparo"]').click();assert.equal(await page.locator('[data-plots-table] tbody tr:visible').count(),1);
  await page.locator('[data-filter-crop="todos"]').click();
  await page.locator('[data-edit-plot]').first().click();await page.locator('[data-modal="talhao-form"] [data-close-modal]').first().click();
  await page.locator('[data-open-modal="talhao-form"]').first().click();await page.waitForTimeout(100);
  const label=await page.locator('[name=cultura]').evaluate(s=>({text:s.closest('.select').querySelector('.select-trigger').textContent.trim(),selected:s.selectedOptions[0].textContent.trim()}));
  assert.ok(label.text.includes(label.selected),JSON.stringify(label));await shot('talhao-form');
  const safra=await api('/api/safras',{nome:'Safra de auditoria',cultura:'Milho',talhoes:[plot.id],dataInicio:'2026-01-01',dataFim:'2026-12-31'},session.token);
  await page.goto(base+'/safras.html');await page.locator('[data-safra-id]').waitFor();await shot('safras-desktop');
  await page.locator('[data-advance]').click();await page.waitForFunction(()=>document.querySelector('[data-current-stage]').textContent.includes('Preparação'));
  await page.locator('[data-advance]').click();await page.waitForFunction(()=>document.querySelector('[data-current-stage]').textContent.includes('Plantio'));await shot('plantio-desktop');
  await page.locator('[data-history]').click();await page.locator('[data-modal="safra-history"].is-open').waitFor();await shot('historico');await page.locator('[data-close-history]').click();
  await page.route('**/api/custos/resumo?*',r=>r.fulfill({status:503,contentType:'application/json',body:'{"error":"Falha de consulta de teste"}'}));
  await page.locator('[data-history]').click();await page.waitForTimeout(500);assert.equal(await page.locator('[data-modal="safra-history"].is-open').count(),0,'Falha de consulta não deve abrir histórico com totais inventados');
  await page.unroute('**/api/custos/resumo?*');
  await page.setViewportSize({width:390,height:844});await shot('plantio-mobile');
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Safras não deve transbordar no celular');
  await page.goto(base+'/areas-cultivo.html');await page.locator('[data-edit-plot]').first().waitFor();await shot('areas-mobile');
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Áreas não deve transbordar no celular');
  assert.deepEqual(errors,[]);console.log('OK: estado vazio, API real em memória, reset de cultura, avanço até Plantio, histórico, falha de consulta, desktop/mobile e console.');
 } finally {if(browser)await browser.close();server.kill();}
})().catch(e=>{console.error(e);process.exitCode=1;});
