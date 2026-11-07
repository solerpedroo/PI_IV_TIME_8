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
  const screens=['dashboard','areas-cultivo','safras','atividades','insumos','manutencao','ocorrencias','custos','configuracoes','login','criar-conta','confirmar-email','recuperar-senha','redefinir-senha'];
  const findings=[];
  for(const width of [1440,390]) {
   await page.setViewportSize({width,height:width===390?844:1000});
   for(const screen of screens) {
    const responses=[]; const listen=r=>{if(r.status()>=400)responses.push(r.status()+' '+new URL(r.url()).pathname);};page.on('response',listen);
    await page.goto(base+'/'+screen+'.html');await page.waitForTimeout(700);
    await shot(screen+'-'+width);
    const info=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth,title:document.querySelector('h1')?.textContent,loading:document.body.innerText.includes('Carregando propriedade')}));
    findings.push({screen,width,...info,responses});page.off('response',listen);
   }
  }
  await page.goto(base+'/atividades.html');
  await page.locator('[name=data]').evaluate(input=>{input.value='data-invalida';});
  assert.ok((await page.locator('[data-date-label]').allTextContents()).includes('Data inválida'));
  console.log(JSON.stringify({findings,errors},null,2));assert.deepEqual(errors,[]);assert.ok(findings.every(r=>!r.overflow && !r.loading && !r.responses.length),'Todas as telas devem carregar sem overflow, erros HTTP ou placeholders permanentes');
 } finally {if(browser)await browser.close();server.kill();}
})().catch(e=>{console.error(e);process.exitCode=1;});
