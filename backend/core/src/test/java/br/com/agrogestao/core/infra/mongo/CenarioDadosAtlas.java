// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.infra.mongo, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.infra.mongo;

// Importa a aritmética decimal sem as aproximações de ponto flutuante binário.
import java.math.BigDecimal;
// Importa os instantes de criação e dos eventos.
import java.time.Instant;
// Importa as datas de calendário sem horário ou fuso.
import java.time.LocalDate;
// Importa as listas de vínculos, eventos e resultados.
import java.util.List;
// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa br.com.agrogestao.core.domain.cultivo.* para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.cultivo.*;
// Importa br.com.agrogestao.core.domain.operacao.EscopoPropriedade para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
// Importa br.com.agrogestao.core.domain.propriedades.Propriedade para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.propriedades.Propriedade;
// Importa br.com.agrogestao.core.domain.usuarios.Usuario para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.usuarios.Usuario;

/** IDs exclusivos são conhecidos antes da primeira escrita para permitir limpeza mesmo em caso de falha. */
// Agrupa as operações de CenarioDadosAtlas nesta classe.
final class CenarioDadosAtlas implements AutoCloseable {
    // Suíte Atlas só grava em banco cujo nome termina em _teste (ver MongoConexao.paraTestes).
    // Abre exclusivamente banco com sufixo _teste para isolar os dados fictícios do cenário.
    MongoConexao conexao = MongoConexao.paraTestes();
    // Reserva um identificador exclusivo para produtorA, evitando colisão com dados de outras execuções.
    final ObjectId produtorA = new ObjectId();
    // Reserva um identificador exclusivo para produtorB, evitando colisão com dados de outras execuções.
    final ObjectId produtorB = new ObjectId();
    // Reserva um identificador exclusivo para propriedadeA, evitando colisão com dados de outras execuções.
    final ObjectId propriedadeA = new ObjectId();
    // Reserva um identificador exclusivo para propriedadeB, evitando colisão com dados de outras execuções.
    final ObjectId propriedadeB = new ObjectId();
    // Reserva um identificador exclusivo para propriedadeExtra, evitando colisão com dados de outras execuções.
    final ObjectId propriedadeExtra = new ObjectId();
    // Define o escopo da propriedade principal do produtor A.
    final EscopoPropriedade a = new EscopoPropriedade(produtorA, propriedadeA);
    // Define o escopo do produtor B para os testes de isolamento entre usuários.
    final EscopoPropriedade b = new EscopoPropriedade(produtorB, propriedadeB);
    // Define outra propriedade de A para testar isolamento mesmo quando o titular é o mesmo.
    final EscopoPropriedade extra = new EscopoPropriedade(produtorA, propriedadeExtra);
    // Reserva referências dos registros operacionais que serão criados e removidos pela suíte.
    ObjectId talhaoA, talhaoB, talhaoExtra, culturaA, culturaB, safraA, atividadeA, insumoA, consumoA, ocorrenciaA;

    // Cria produtores, propriedades, talhões, culturas e safra fictícios com referências válidas para a suíte.
    void preparar() {
        // Executa ping no banco antes de continuar o cenário de persistência.
        conexao.verificar();
        // Cria o repositório UsuarioRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
        UsuarioRepositorioMongo usuarios = new UsuarioRepositorioMongo(conexao.banco());
        // Garante os índices de usuarios antes das gravações ou consultas do cenário.
        usuarios.criarIndices();
        // Prepara as duas contas fictícias com identificadores exclusivos desta execução.
        for (ObjectId id : List.of(produtorA, produtorB)) {
            // Grava email único e hash fictício para cada produtor do cenário.
            usuarios.inserir(new Usuario(id, "dados-" + id + "@teste.invalid", "Produtor fictício", "hash-ficticio", Instant.now()));
        }
        // Cria o repositório PropriedadeRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
        PropriedadeRepositorioMongo propriedades = new PropriedadeRepositorioMongo(conexao.banco());
        // Garante os índices de propriedades antes das gravações ou consultas do cenário.
        propriedades.criarIndices();
        // Prepara a propriedade de cada um dos três escopos que serão comparados.
        for (EscopoPropriedade escopo : List.of(a, b, extra)) {
            // Grava a propriedade com seu id exclusivo e a referência ao titular correto.
            propriedades.inserir(new Propriedade(escopo.propriedadeId(), escopo.proprietarioId(),
                    // Gera nome exclusivo e instante de criação para evitar conflito com outras execuções.
                    "Teste dados " + escopo.propriedadeId(), Instant.now()));
        }
        // Cria o repositório CultivoRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
        CultivoRepositorioMongo cultivo = new CultivoRepositorioMongo(conexao.banco());
        // Garante os índices de cultivo antes das gravações ou consultas do cenário.
        cultivo.criarIndices();
        // Cria o talhão principal de A com área decimal de 12,75 hectares.
        talhaoA = cultivo.inserir(new Talhao(null, a, "Talhão A", new BigDecimal("12.75"), Talhao.Status.ATIVO));
        // Cria um talhão de B que servirá como referência proibida nos testes de A.
        talhaoB = cultivo.inserir(new Talhao(null, b, "Talhão B", BigDecimal.TEN, Talhao.Status.ATIVO));
        // Cria um talhão na segunda propriedade de A para testar o isolamento por fazenda.
        talhaoExtra = cultivo.inserir(new Talhao(null, extra, "Talhão extra", BigDecimal.ONE, Talhao.Status.ATIVO));
        // Cria a cultura Soja no escopo principal de A.
        culturaA = cultivo.inserir(new Cultura(null, a, "Soja"));
        // Cria a cultura Milho no escopo de B para testar referências de outro titular.
        culturaB = cultivo.inserir(new Cultura(null, b, "Milho"));
        // Cria a safra de A com sua cultura e seu talhão válido.
        safraA = cultivo.inserir(safra(a, culturaA, List.of(talhaoA)));
    }

    // Constrói uma safra fictícia com período e área conhecidos para os casos de teste.
    Safra safra(EscopoPropriedade escopo, ObjectId cultura, List<ObjectId> talhoes) {
        // Inicia uma safra fictícia em planejamento, com início no primeiro dia de 2026.
        return Safra.nova(null, escopo, "Safra 2026", cultura, talhoes, LocalDate.of(2026, 1, 1),
                // Completa o período até o último dia de 2026 e mantém a área conhecida de 12,75 hectares.
                LocalDate.of(2026, 12, 31), new BigDecimal("12.75"));
    }

    // Fecha a conexão atual e abre outra para comprovar leitura persistida, sem depender do objeto anterior.
    void reabrir() {
        // Fecha o cliente anterior para evitar que a prova de persistência dependa da conexão criadora.
        conexao.close();
        // Abre outra conexão validada para o mesmo banco de testes.
        conexao = MongoConexao.paraTestes();
        // Executa ping no banco antes de continuar o cenário de persistência.
        conexao.verificar();
    }

    // Indica que este método implementa o contrato herdado pela classe.
    @Override public void close() {
        // Conexão própria: a limpeza funciona mesmo se uma tentativa de reconexão do teste falhar.
        // Abre o recurso para esta operação e garante seu fechamento ao sair do bloco, inclusive em caso de falha.
        try (MongoConexao limpeza = MongoConexao.paraTestes()) {
            // Percorre as coleções operacionais na ordem de remoção dos dados fictícios e de seus históricos.
            for (String colecao : List.of("custos", "ocorrencias_timeline", "ocorrencias", "movimentacoes_insumos", "insumos", "atividades_historico", "atividades", "safras", "culturas", "talhoes")) {
                // Repete a limpeza para os três escopos exclusivos da execução.
                for (EscopoPropriedade escopo : List.of(a, b, extra)) {
                    // Remove somente documentos que correspondem ao titular e à propriedade do cenário.
                    limpeza.banco().getCollection(colecao).deleteMany(escopo.documento());
                    // Confere que nenhum documento operacional desse escopo sobreviveu à remoção.
                    if (limpeza.banco().getCollection(colecao).countDocuments(escopo.documento()) != 0) {
                        // Faz o teste falhar: registro fictício não removido: .
                        throw new AssertionError("registro fictício não removido: " + colecao);
                    }
                }
            }
            // Depois dos registros operacionais, remove as propriedades criadas pela suíte.
            for (EscopoPropriedade escopo : List.of(a, b, extra)) {
                // Identifica a propriedade pelo id exclusivo e pelo titular antes de removê-la.
                limpeza.banco().getCollection("propriedades").deleteOne(new Document("_id", escopo.propriedadeId())
                        // Preenche no BSON os campos proprietarioId.
                        .append("proprietarioId", escopo.proprietarioId()));
            }
            // Percorre os dois produtores fictícios depois de remover suas propriedades.
            for (ObjectId id : List.of(produtorA, produtorB)) {
                // Remove a conta pelo id exclusivo, sem limpar a coleção inteira.
                limpeza.banco().getCollection("usuarios").deleteOne(new Document("_id", id));
            }
            // Confere a ausência das propriedades fictícias depois da remoção.
            for (EscopoPropriedade escopo : List.of(a, b, extra)) {
                // Confere que a propriedade fictícia foi efetivamente removida do banco.
                if (limpeza.banco().getCollection("propriedades").countDocuments(new Document("_id", escopo.propriedadeId())) != 0) {
                    // Faz o teste falhar: propriedade fictícia não removida.
                    throw new AssertionError("propriedade fictícia não removida");
                }
            }
            // Confere a ausência dos produtores fictícios depois da remoção.
            for (ObjectId id : List.of(produtorA, produtorB)) {
                // Confere que o produtor fictício não permanece na coleção depois da limpeza.
                if (limpeza.banco().getCollection("usuarios").countDocuments(new Document("_id", id)) != 0) {
                    // Faz o teste falhar: usuário fictício não removido.
                    throw new AssertionError("usuário fictício não removido");
                }
            }
        // Executa a limpeza mesmo se uma das operações anteriores lançar uma exceção.
        } finally { conexao.close(); }
    }
}
