// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Importa as asserções estritas para validar respostas da API e resultados apresentados pela interface.
const assert = require('node:assert/strict');
// Importa a construção do caminho do diretório usado ao iniciar a API Java.
const path = require('node:path');
// Importa a criação de processos para executar uma instância real do servidor durante o teste.
const {spawn} = require('node:child_process');
// Importa a espera por evento usada para aguardar o encerramento do processo Java.
const {once} = require('node:events');
// Carrega o Chromium do Playwright pelo módulo configurado ou pelo pacote padrão.
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
// Importa a configuração compartilhada de lançamento do navegador de testes.
const {launchTestBrowser} = require('./browser-launch.cjs');
// Define a origem local da instância real da API usada exclusivamente neste cenário.
const base = 'http://127.0.0.1:8080';

// Executa o fluxo assíncrono completo e deixa o tratamento final converter falhas em saída de processo.
(async () => {
  // Não usa serviços externos nem dados existentes: servidor próprio em memória.
  // Exige que a porta esteja livre antes de iniciar a instância própria para evitar usar um servidor alheio.
  await assert.rejects(fetch(base + '/health'), 'porta 8080 deve estar livre para o teste');
  // Inicia o servidor Java real na porta do teste com repositórios em memória.
  const server = spawn('java', ['-cp', 'bin:lib/*', 'Servidor', '8080', '--memoria'], {
    // Usa o diretório da API como base para localizar as classes compiladas e as bibliotecas.
    cwd: path.join(__dirname, '../backend/api'),
    // Desabilita a configuração SMTP no processo filho para usar a confirmação local registrada no console.
    env: {...process.env, SMTP_HOST: '', SMTP_USERNAME: '', SMTP_PASSWORD: '', SMTP_FROM: '', SMTP_PORT: ''},
  });
  // Acumula a saída do servidor para localizar o token de confirmação do cadastro.
  let log = '';
  // Acrescenta ao log os dados enviados pelo servidor à saída padrão.
  server.stdout.on('data', chunk => { log += chunk.toString(); });
  // Acrescenta também a saída de erro para preservar os diagnósticos da instância Java.
  server.stderr.on('data', chunk => { log += chunk.toString(); });
  // Reserva a referência ao navegador para fechá-lo no bloco de limpeza, mesmo se o cenário falhar.
  let browser;
  // Protege o cenário com limpeza de navegador e servidor ao finalizar a execução.
  try {
    // Consulta a saúde do servidor até ele ficar pronto, com limite de tentativas.
    for (let attempt = 0; ; attempt++) {
      // Encerra a espera assim que a rota de saúde responde com sucesso; falhas transitórias ainda são toleradas.
      try { if ((await fetch(base + '/health')).ok) break; } catch {}
      // Aborta após cinquenta tentativas ou se o processo Java já tiver encerrado.
      if (attempt === 50 || server.exitCode !== null) throw new Error('Servidor de teste não iniciou');
      // Aguarda cem milissegundos entre consultas sem bloquear o event loop.
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    // Centraliza chamadas JSON à API real com autenticação opcional para os passos do cenário.
    const api = async (endpoint, body, token) => {
      // Usa POST quando há corpo e GET nas consultas sem corpo.
      const response = await fetch(base + endpoint, {method: body ? 'POST' : 'GET',
        // Declara JSON e acrescenta Bearer somente quando o passo possui token autenticado.
        headers: {'Content-Type': 'application/json', ...(token ? {Authorization: 'Bearer ' + token} : {})},
        // Serializa os dados recebidos ou omite o corpo nas consultas GET.
        body: body ? JSON.stringify(body) : undefined});
      // Exige resposta de sucesso e inclui a rota e o status no diagnóstico da falha.
      assert.ok(response.ok, endpoint + ': HTTP ' + response.status);
      // Lê e devolve o JSON efetivamente retornado pelo servidor.
      return response.json();
    };
    // Cadastra usuário fictício com os dados de contato e a propriedade usados neste fluxo.
    await api('/api/auth/register', {name: 'Teste Real', email: 'real@agro.test', phone: '(19) 99999-1234',
      // Completa o cadastro com senha fictícia, confirmação, área e aceite dos termos.
      password: 'Senha@123', confirm: 'Senha@123', farm: 'Fazenda Real', area: '50 ha', terms: true});
    // Extrai do log o código de confirmação emitido pelo servidor em modo local.
    const confirmation = log.match(/token: ([a-zA-Z0-9-]+)/);
    // Exige que o modo local tenha disponibilizado o token antes de continuar.
    assert.ok(confirmation, 'modo local deve entregar código no console');
    // Confirma o email pelo endpoint real usando o token capturado no console.
    await api('/api/auth/confirm-email', {token: confirmation[1]});
    // Autentica a conta criada para obter o token de sessão e os dados do usuário.
    const session = await api('/api/auth/login', {email: 'real@agro.test', password: 'Senha@123'});
    // Abre o Chromium com a configuração comum das suítes.
    browser = await launchTestBrowser(chromium);
    // Cria contexto novo de navegador para isolar cookies e armazenamento deste fluxo.
    const context = await browser.newContext();
    // Prepara a sessão antes da execução dos scripts de cada página aberta.
    await context.addInitScript(session => {
      // Evita substituir uma sessão já atualizada pela própria aplicação ao navegar novamente.
      if (!sessionStorage.getItem('agro.sessionToken')) {
        // Disponibiliza o token real para as requisições autenticadas feitas pela interface.
        sessionStorage.setItem('agro.sessionToken', session.token);
        // Disponibiliza o usuário e sua propriedade para o isolamento dos dados locais.
        sessionStorage.setItem('agro.sessionUser', JSON.stringify(session.usuario));
      }
    // Transmite a sessão obtida pela API ao script de inicialização do navegador.
    }, session);
    // Abre a página que realizará as ações reais da interface.
    const page = await context.newPage();
    // Acumula erros JavaScript para conferir que nenhum ocorreu ao longo do cenário.
    const errors = [];
    // Registra mensagens de exceções não tratadas emitidas pela página.
    page.on('pageerror', error => errors.push(error.message));
    // Inicia a espera pela resposta antes da navegação para não perder a requisição inicial.
    await Promise.all([
      // Aguarda a consulta real dos talhões que abastece a tela.
      page.waitForResponse(response => response.url().endsWith('/api/talhoes')),
      // Navega até a tela de áreas de cultivo servida pela API Java.
      page.goto(base + '/areas-cultivo.html'),
    ]);
    // Abre o formulário de cadastro de talhão pela ação visível da interface.
    await page.locator('[data-open-modal="talhao-form"]').click();
    // Preenche o nome pelo qual o talhão será localizado nas telas e na API.
    await page.locator('[name=nome]').fill('Talhão Real');
    // Seleciona a cultura Soja no cadastro do talhão.
    await page.locator('[name=cultura]').selectOption('Soja');
    // Define a área do novo talhão como doze hectares.
    await page.locator('[name=hectares]').fill('12');
    // Preenche a identificação da safra exigida no cadastro do talhão.
    await page.locator('[name=safra]').fill('2026/27');
    // Dispara o submit do formulário com as validações e os listeners reais da página.
    await page.locator('[data-talhao-form]').evaluate(form => form.requestSubmit());
    // Espera o talhão persistido aparecer na tabela antes de seguir para outra tela.
    await page.locator('[data-plots-table] tr[data-plot="Talhão Real"]').waitFor();
    // Consulta a API autenticada para guardar o id do talhão realmente persistido.
    const plot = (await api('/api/talhoes', null, session.token))[0];
    // Abre o dashboard para conferir a propagação do cadastro entre telas.
    await page.goto(base + '/dashboard.html');
    // Espera o indicador do painel refletir exatamente uma área cadastrada.
    await page.waitForFunction(() => document.querySelector('[data-metric=areas]').textContent === '1');
    // Confere que o seletor de atividades do painel contém o talhão criado.
    assert.equal(await page.locator('[name=activity-plot] option[value="Talhão Real"]').count(), 1);
    // Abre a página de atividades para conferir o mesmo vínculo fora do dashboard.
    await page.goto(base + '/atividades.html');
    // Aguarda a opção carregada de forma assíncrona no select nativo.
    await page.waitForFunction(() => document.querySelector('[name=talhao] option[value="Talhão Real"]'));
    // O dropdown visual também deve receber a opção carregada depois do DOM.
    // Confere que o componente visual de select também refletiu a opção recebida da API.
    assert.equal(await page.locator('.select-option[data-value="Talhão Real"]').count(), 1);
    // Abre o formulário de atividade para o talhão existente.
    await page.locator('[data-open-modal="activity-form"]').first().click();
    // Preenche um nome conhecido para identificar a operação no histórico.
    await page.locator('[name=nome]').fill('Plantio Real');
    // Seleciona Manutenção como tipo da atividade deste cenário.
    await page.locator('[name=tipo]').selectOption('Manutenção');
    // Associa a atividade ao talhão confirmado pela API.
    await page.locator('[name=talhao]').selectOption('Talhão Real');
    // Preenche a data no formato aceito pelo campo da tela.
    await page.locator('[name=data]').fill('23 out');
    // Instala uma falha controlada de armazenamento no contexto da página.
    await page.evaluate(() => {
      // Guarda a implementação original de setItem para restaurá-la depois do teste de falha.
      window.__setItem = Storage.prototype.setItem;
      // Intercepta as tentativas de gravação no armazenamento do navegador.
      Storage.prototype.setItem = function(key, value) {
        // Simula quota excedida somente nas chaves operacionais, mantendo os outros usos do Storage disponíveis.
        if (key.startsWith('agro.operational.v1.')) throw new DOMException('Quota', 'QuotaExceededError');
        // Delega as demais gravações à implementação original com o mesmo objeto receptor.
        return window.__setItem.call(this, key, value);
      };
    });
    // Registra antecipadamente a espera pelo POST que escreve no histórico do talhão.
    const historyResponse = page.waitForResponse(response => response.url().endsWith('/historico') && response.request().method() === 'POST');
    // Envia a atividade pelo formulário da interface com a falha local ainda instalada.
    await page.locator('[data-activity-form]').evaluate(form => form.requestSubmit());
    // Exige HTTP 201 na gravação do histórico para comprovar a operação remota.
    assert.equal((await historyResponse).status(), 201);
    // Confere pela API que só um evento foi gravado no histórico do talhão.
    assert.equal((await api('/api/talhoes/' + plot.id + '/historico', null, session.token)).length, 1);
    // Confere que a falha do cache local não duplicou nem desfez a atividade persistida pela API.
    assert.equal((await api('/api/atividades', null, session.token)).length, 1, 'falha do cache não duplica nem desfaz operação remota');
    // Restaura setItem para que os próximos passos não herdem a falha artificial.
    await page.evaluate(() => { Storage.prototype.setItem = window.__setItem; });
    // Consulta se o servidor oferece o CRUD de propriedades para exercitar também a troca de contexto.
    const properties = await fetch(base + '/api/propriedades', {headers: {Authorization: 'Bearer ' + session.token}});
    // O mesmo teste continua útil nas branches anteriores ao CRUD de propriedades.
    // Executa o fluxo adicional de propriedades somente quando essa funcionalidade está disponível.
    if (properties.ok) {
      // Guarda o id da propriedade original para permitir voltar ao contexto inicial.
      const originalId = session.usuario.propriedadeId;
      // Grava notificação privada da primeira propriedade para verificar o isolamento após a troca.
      await page.evaluate(() => window.AgroLocal.save('notifications', ['Somente propriedade original']));
      // Navega até a configuração da propriedade pelo mesmo endereço usado na aplicação.
      await page.goto(base + '/configuracoes.html#settings-property');
      // Espera a primeira propriedade chegar ao seletor antes de interagir com o formulário.
      await page.locator('.property-context-select option').first().waitFor({state: 'attached'});
      // Aciona a criação de outra propriedade pela ação visível na página.
      await page.locator('[data-new-property]').click();
      // Preenche o nome da segunda fazenda usada no teste de isolamento.
      await page.locator('[name=farm]').fill('Fazenda Segunda');
      // Define vinte e cinco hectares para a nova propriedade.
      await page.locator('[name=area]').fill('25');
      // Preenche a localização exigida no cadastro da propriedade.
      await page.locator('[name=location]').fill('Rodovia 1');
      // Preenche o município exigido no cadastro da propriedade.
      await page.locator('[name=city]').fill('Campinas');
      // Preenche a safra da nova propriedade no campo do formulário.
      await page.locator('[name=harvest]').fill('2026/27');
      // Espera o POST de criação antes de enviar o formulário para capturar a resposta correta.
      const createdResponse = page.waitForResponse(response => response.url().endsWith('/api/propriedades') && response.request().method() === 'POST');
      // Envia o formulário real da propriedade com os dados preenchidos.
      await page.locator('#property-form').evaluate(form => form.requestSubmit());
      // Obtém o id e os dados da nova propriedade da resposta efetiva do servidor.
      const newProperty = await (await createdResponse).json();
      // Aguarda a opção da nova propriedade ser inserida no seletor.
      await page.locator('.property-context-select option[value="' + newProperty.id + '"]').waitFor({state: 'attached'});
      // Seleciona a segunda propriedade para trocar o contexto ativo no servidor e no navegador.
      await page.locator('.property-context-select').selectOption(newProperty.id);
      // Aguarda a sessão local passar a apontar para o novo id de propriedade.
      await page.waitForFunction(id => JSON.parse(sessionStorage.getItem('agro.sessionUser')).propriedadeId === id
        // Confere também o nome preenchido pela página para garantir que a interface acompanhou a sessão.
        && document.querySelector('[name=farm]')?.value === 'Fazenda Segunda', newProperty.id);
      // Consulta o perfil autenticado no servidor para comparar o contexto remoto com o local.
      const me = await api('/api/me', null, session.token);
      // Exige que o servidor reconheça a nova propriedade como contexto da sessão.
      assert.equal(me.usuario.propriedadeId, newProperty.id);
      // Exige que a resposta contenha os dados da fazenda selecionada.
      assert.equal(me.propriedade.farm, 'Fazenda Segunda');
      // Confere que a notificação privada da fazenda original ficou invisível no novo contexto.
      assert.equal(await page.evaluate(() => window.AgroLocal.read('notifications', []).includes('Somente propriedade original')), false);
      // Prepara a espera pela consulta de talhões antes de retornar à tela de áreas.
      await Promise.all([
        // Espera a requisição que carregará os talhões do contexto atual.
        page.waitForResponse(response => response.url().endsWith('/api/talhoes')),
        // Abre áreas de cultivo com a segunda propriedade selecionada.
        page.goto(base + '/areas-cultivo.html'),
      ]);
      // Confere pela API que a nova propriedade começa sem herdar o talhão da primeira.
      assert.equal((await api('/api/talhoes', null, session.token)).length, 0);
      // Retorna à configuração para restaurar a propriedade original.
      await page.goto(base + '/configuracoes.html#settings-property');
      // Espera a opção da propriedade original estar disponível no seletor.
      await page.locator('.property-context-select option[value="' + originalId + '"]').waitFor({state: 'attached'});
      // Seleciona o contexto original da conta.
      await page.locator('.property-context-select').selectOption(originalId);
      // Aguarda a sessão do navegador voltar ao id da primeira propriedade.
      await page.waitForFunction(id => JSON.parse(sessionStorage.getItem('agro.sessionUser')).propriedadeId === id
        // Confere que o formulário também voltou a mostrar o nome da fazenda original.
        && document.querySelector('[name=farm]')?.value === 'Fazenda Real', originalId);
      // Confirma que a notificação privada reaparece quando o contexto correto é restaurado.
      assert.equal(await page.evaluate(() => window.AgroLocal.read('notifications', [])[0]), 'Somente propriedade original');
      // Confirma que o talhão original continuou persistido e isolado durante as trocas.
      assert.equal((await api('/api/talhoes', null, session.token))[0].nome, 'Talhão Real');
      // Registra o sucesso do fluxo adicional de cadastro, seleção e isolamento entre propriedades.
      console.log('OK propriedades reais: cadastro pela UI, seleção, me e isolamento de cache entre contextos');
    }
    // Faz o cenário falhar se alguma página tiver emitido erro JavaScript não tratado.
    assert.deepEqual(errors, []);
    // Registra o sucesso do fluxo principal executado contra a API Java real.
    console.log('OK API Java real: cadastro, confirmação, login, criação, painel, dropdown e histórico somente após salvar');
  // Garante o fechamento dos recursos mesmo quando uma asserção ou requisição falhar.
  } finally {
    // Fecha o navegador somente se ele chegou a ser aberto.
    if (browser) await browser.close();
    // Solicita o encerramento da instância Java ainda ativa e aguarda o evento de saída.
    if (server.exitCode === null) { server.stdin.write('desativar\n'); await once(server, 'exit'); }
  }
// Registra qualquer falha final e define código de saída não zero para o executor da suíte.
})().catch(error => { console.error(error); process.exitCode = 1; });
