// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Importa as asserções estritas do Node para comparar os resultados sem coerções de tipo.
const assert = require('node:assert/strict');
// Importa a leitura dos arquivos JavaScript reais que serão executados no cenário simulado.
const fs = require('node:fs');
// Importa o isolamento de contextos JavaScript para simular abas e sessões diferentes.
const vm = require('node:vm');
// Importa a construção de caminhos dos scripts relativos ao diretório deste teste.
const path = require('node:path');
// Importa o executor nativo de testes do Node.
const {test} = require('node:test');

// Verifica que contas e propriedades distintas não compartilham os dados operacionais armazenados no navegador.
test('dados locais separados por conta e propriedade, inclusive após logout', () => {
  // Mantém um armazenamento local compartilhado entre os contextos, como acontece entre abas da mesma origem.
  const local = new Map();
  // Cria uma página simulada com sessão própria para o usuário e a propriedade recebidos.
  function page(id, propriedadeId) {
    // Prepara a identidade da sessão desta página sem misturá-la com as sessões das outras páginas.
    const session = new Map([['agro.sessionUser', JSON.stringify({id, propriedadeId})]]);
    // Expõe apenas a interface de localStorage necessária para executar o módulo real.
    const window = {localStorage: {
      // Simula leitura do armazenamento, usando null quando a chave não existe.
      getItem: key => local.get(key) ?? null,
      // Simula gravação no mesmo armazenamento que será compartilhado pelas páginas.
      setItem: (key, value) => local.set(key, value),
    }};
    // Cria contexto JavaScript isolado com armazenamento local compartilhado e sessionStorage específico da aba.
    const context = vm.createContext({window, sessionStorage: {getItem: key => session.get(key) ?? null},
      // Fornece o mínimo do DOM e mantém o documento carregando para evitar inicializações que dependam da página real.
      document: {readyState: 'loading', addEventListener() {}}});
    // Carrega as definições operacionais e a implementação de persistência local utilizadas pela aplicação.
    for (const file of ['frontend/scripts/data/operational-defaults.js', 'frontend/scripts/agro-local.js']) {
      // Executa os scripts reais dentro do contexto da página simulada para testar o comportamento efetivo do módulo.
      vm.runInContext(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'), context);
    }
    // Devolve a API de dados locais e a sessão para permitir simular trocas de propriedade e logout.
    return {api: window.AgroLocal, session};
  }
  // Prepara dados legados sem dono identificado para verificar que não vazam para contas autenticadas.
  local.set('agro.operational.v1.notifications', JSON.stringify({version: 1, data: ['Legado sem dono']}));
  // Abre a página da conta A na fazenda A.
  const a = page('conta-a', 'fazenda-a');
  // Confere que os dados legados sem proprietário não são incorporados à conta A.
  assert.ok(!a.api.read('notifications', []).includes('Legado sem dono'));
  // Confirma que a conta A consegue gravar sua própria notificação.
  assert.equal(a.api.save('notifications', ['Privado A']), true);
  // Abre outra conta na mesma fazenda para testar a separação pelo identificador do usuário.
  const b = page('conta-b', 'fazenda-a');
  // Confere que a conta B não lê a notificação privada da conta A.
  assert.ok(!b.api.read('notifications', []).includes('Privado A'));
  // Confirma que a conta B também pode manter seus próprios dados.
  assert.equal(b.api.save('notifications', ['Privado B']), true);
  // Confere que mudar somente a fazenda já impede ler os dados privados do contexto anterior.
  assert.ok(!page('conta-a', 'fazenda-b').api.read('notifications', []).includes('Privado A'));
  // Confirma que a notificação original permanece disponível no par correto de conta e fazenda.
  assert.equal(page('conta-a', 'fazenda-a').api.read('notifications')[0], 'Privado A');
  // Simula troca de propriedade na sessão da aba que ainda mantém uma API do contexto anterior.
  a.session.set('agro.sessionUser', JSON.stringify({id: 'conta-a', propriedadeId: 'fazenda-b'}));
  // Confere que a API antiga recusa gravar depois da mudança de contexto da sessão.
  assert.equal(a.api.save('notifications', ['Gravação da aba antiga']), false);
  // Remove a identidade da sessão para simular logout.
  a.session.delete('agro.sessionUser');
  // Confere que o módulo recusa persistir novos dados depois do logout.
  assert.equal(a.api.save('notifications', ['Após logout']), false);
  // Garante que a proteção de isolamento não apaga o legado, permitindo eventual recuperação explícita.
  assert.ok(local.has('agro.operational.v1.notifications'), 'legado deve continuar recuperável');
});
