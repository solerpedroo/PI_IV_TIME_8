/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
import dados.estoque.RepositorioEstoqueMongo;
import dados.infra.ConexaoMongo;
import java.time.Instant;
import java.util.concurrent.*;
import org.bson.Document;
import org.bson.types.ObjectId;

/* Integração real do estoque. Prova isolamento por propriedade, duas saídas
   concorrentes em que só uma passa, saldo que não se perde, custo médio
   ponderado, histórico preservado na exclusão e os mesmos números depois de
   reconectar. A limpeza atinge só os IDs gerados aqui. Não apaga banco nem coleções. */
public class TesteEstoqueMongo
{
    private static void conferir (boolean condicao, String mensagem) throws Exception
    {
        if (!condicao) throw new Exception (mensagem);
        System.out.println ("ok Mongo: " + mensagem);
    }

    /* Movimento mínimo: tipo e quantidade. A observação marca o registro deste teste. */
    private static Document movimento (String tipo, double quantidade)
    {
        return new Document ("tipo", tipo).append ("quantidade", quantidade).append ("observacao", "Teste isolado")
            .append ("criadoEm", Instant.now ().toString ());
    }

    public static void main (String[] args) throws Exception
    {
        String uri = CenarioEstoqueTeste.uriSegura ();
        String pid = new ObjectId ().toHexString ();
        ConexaoMongo conexao = new ConexaoMongo (uri);
        ExecutorService grupo = Executors.newFixedThreadPool (2);
        try
        {
            RepositorioEstoqueMongo estoque = new RepositorioEstoqueMongo (conexao);
            /*
                Insumo de teste, mesma forma que EstoqueHandler grava em insumos:
                nome, propriedadeId, categoria, unidade, minimo, saldo, custoUnitario, criadoEm.
                criar() gera o _id e, como saldo > 0, também a movimentação de abertura.
            */
            Document produto = new Document ("nome", "Teste isolado Mongo").append ("propriedadeId", pid)
                .append ("categoria", "Fertilizante").append ("unidade", "kg").append ("minimo", 5.0)
                .append ("saldo", 10.0).append ("custoUnitario", 2.0).append ("criadoEm", Instant.now ().toString ());
            estoque.criar (produto);
            String id = produto.getObjectId ("_id").toHexString ();
            /* Outra propriedade não enxerga o insumo, mesmo conhecendo o id. */
            conferir (estoque.buscarPorId (new ObjectId ().toHexString (), id) == null, "isolamento por propriedade");
            /* Duas saídas de 7 sobre saldo 10: só uma conexão grava; a outra volta null. O saldo fica 3 e a rejeição não cria histórico extra. */
            CountDownLatch inicio = new CountDownLatch (1);
            Callable<Document> consumir = () -> {
                try (AutoCloseableMongo outro = new AutoCloseableMongo (uri))
                {
                    RepositorioEstoqueMongo repo = new RepositorioEstoqueMongo (outro.conexao);
                    inicio.await ();
                    return repo.movimentar (pid, id, movimento ("saida", 7));
                }
            };
            Future<Document> um = grupo.submit (consumir), dois = grupo.submit (consumir);
            inicio.countDown ();
            Document a = um.get (30, TimeUnit.SECONDS), b = dois.get (30, TimeUnit.SECONDS);
            conferir ((a == null) != (b == null), "duas conexões concorrentes aceitam somente uma saída");
            conferir (estoque.buscarPorId (pid, id).getDouble ("saldo") == 3, "saldo 3 sem perda de atualização");
            conferir (estoque.listarMovimentacoes (pid, id).size () == 2, "rejeição não gera histórico extra");
            /* Entrada de 3 a R$ 4 sobre 3 unidades a R$ 2: o custo médio ponderado fica em R$ 3. */
            estoque.movimentar (pid, id, movimento ("entrada", 3).append ("custoUnitario", 4.0));
            conferir (estoque.buscarPorId (pid, id).getDouble ("custoUnitario") == 3, "custo médio ponderado real");
            Document atividade = new Document ("_id", new ObjectId ()).append ("proprietarioId", new ObjectId (pid))
                .append ("propriedadeId", new ObjectId (pid)).append ("titulo", "Atividade persistida de teste");
            conexao.getBanco ().getCollection ("atividades").insertOne (atividade);
            /* A atividade só é aceita se titular e propriedade batem com o documento BSON. */
            conferir (estoque.buscarAtividade (pid, pid, atividade.getObjectId ("_id").toHexString ()) != null, "compatibilidade com IDs BSON da modelagem");
            conferir (estoque.buscarAtividade (new ObjectId ().toHexString (), pid, atividade.getObjectId ("_id").toHexString ()) == null, "atividade de outro titular é rejeitada");
            estoque.movimentar (pid, id, movimento ("saida", 1).append ("atividadeId", atividade.getObjectId ("_id").toHexString ()));
            conexao.fechar ();
            conexao = new ConexaoMongo (uri);
            estoque = new RepositorioEstoqueMongo (conexao);
            /* Fechar e reabrir a conexão não perde o saldo nem o vínculo da saída com a atividade. */
            conferir (estoque.buscarPorId (pid, id).getDouble ("saldo") == 5, "saldo preservado após fechar e reabrir conexão");
            conferir (estoque.listarMovimentacoes (pid, id).stream ().anyMatch (m -> atividade.getObjectId ("_id").toHexString ().equals (m.getString ("atividadeId"))), "vínculo do consumo preservado após reconectar");
            estoque.movimentar (pid, id, movimento ("ajuste", 0));
            /* Saldo zero permite a exclusão lógica, e as cinco movimentações continuam no histórico. */
            conferir (estoque.excluir (pid, id), "exclusão com saldo zerado");
            conferir (estoque.listar (pid).isEmpty () && estoque.listarMovimentacoes (pid, id).size () == 5, "exclusão preserva todas as movimentações");
            System.out.println ("TesteEstoqueMongo: OK");
        }
        finally
        {
            grupo.shutdownNow ();
            // Apenas o contexto aleatório desta execução é removido.
            for (String colecao : new String[] {"insumos", "movimentacoes_insumos"})
                conexao.getBanco ().getCollection (colecao).deleteMany (new Document ("propriedadeId", pid));
            conexao.getBanco ().getCollection ("atividades").deleteMany (new Document ("propriedadeId", new ObjectId (pid)));
            conexao.fechar ();
        }
    }

    /* Segunda conexão real, fechada ao sair do try, para a disputa concorrente. */
    private static class AutoCloseableMongo implements AutoCloseable
    {
        final ConexaoMongo conexao;
        AutoCloseableMongo (String uri) throws Exception { this.conexao = new ConexaoMongo (uri); }
        public void close () { this.conexao.fechar (); }
    }
}
