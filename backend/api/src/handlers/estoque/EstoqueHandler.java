/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
/* Autor secundario: Tomas de Paula Michelon Toniato - RA: 25004211 */
package handlers.estoque;

import dados.estoque.CalculosEstoque;
import dados.estoque.RepositorioEstoque;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import seguranca.Sessoes;
import servicos.integracao.ClienteDeAlgoritmos;
import servicos.integracao.EspelhoHistoricoTalhao;
import servicos.financeiro.CustosDeConsumo;
import util.comum.Json;
import util.comum.Validacao;

/*
    Manipulador HTTP do estoque de insumos da fazenda ativa.

    O saldo de cada produto não é um número solto no cadastro. Ele só muda
    por movimentação: entrada soma, saída subtrai e ajuste informa o saldo
    novo. A saída é recusada quando a quantidade passa do saldo disponível.
    Catálogo, movimentações e indicadores ficam isolados pela propriedade
    da sessão: uma fazenda não lê nem grava o estoque de outra.

    Se a saída aponta para uma atividade, o handler registra o custo de
    consumo (a baixa guarda o custo médio daquele momento) e, quando o
    espelho está ligado, copia esse fato para o histórico do talhão.
    As atividades são apenas consultadas para validar o vínculo do consumo.
*/
public class EstoqueHandler
{
    public static final List<String> CATEGORIAS = List.of ("Herbicida", "Fertilizante", "Semente", "Fungicida", "Inseticida");
    private static final ZoneId FUSO = ZoneId.of ("America/Sao_Paulo");
    private final RepositorioEstoque estoque;
    private final Sessoes sessoes;
    private CustosDeConsumo consumo;
    private EspelhoHistoricoTalhao espelhoHistorico;

    /* Recebe o repositório (memória nos testes ou Mongo em produção) e as sessões. */
    public EstoqueHandler (RepositorioEstoque estoque, Sessoes sessoes) throws Exception
    {
        if (estoque == null || sessoes == null) throw new Exception ("Dependência de estoque ausente");
        this.estoque = estoque;
        this.sessoes = sessoes;
    }

    /*
        Liga o serviço financeiro. Sem ele a baixa de estoque ainda ocorre,
        mas não nasce o custo de Insumos da saída vinculada a uma atividade (#156).
    */
    public void setConsumo (CustosDeConsumo consumo)
    {
        this.consumo = consumo;
    }

    /*
        Liga o espelho do histórico do talhão. Ele só copia o fato depois que
        o custo de consumo foi gravado; se a cópia falhar, a movimentação permanece.
    */
    public void setEspelhoHistorico (EspelhoHistoricoTalhao espelho)
    {
        this.espelhoHistorico = espelho;
    }

    /* Publica as rotas. Leituras respondem direto; criações e baixas passam por gravar. */
    public void registrar (Roteador r) throws Exception
    {
        r.adicionar ("GET", "/api/insumos/resumo", this::resumo);
        r.adicionar ("GET", "/api/insumos/atividades", this::atividades);
        r.adicionar ("GET", "/api/insumos/{id}/movimentacoes", this::historico);
        r.adicionar ("GET", "/api/insumos/{id}", this::consultar);
        r.adicionar ("GET", "/api/insumos", this::listar);
        r.adicionar ("POST", "/api/insumos", q -> this.gravar (q, "criar"));
        r.adicionar ("PUT", "/api/insumos/{id}", q -> this.gravar (q, "editar"));
        r.adicionar ("DELETE", "/api/insumos/{id}", q -> this.gravar (q, "excluir"));
        r.adicionar ("POST", "/api/insumos/{id}/movimentacoes", q -> this.gravar (q, "movimentar"));
    }

    /*
        A mesma coordenação da seleção/exclusão de propriedades evita gravar
        em um contexto antigo enquanto a sessão está trocando de fazenda.
        O synchronized na sessão segura a escrita até a propriedade ativa
        ser a mesma que a requisição carregou.
    */
    private Resposta gravar (Requisicao q, String operacao) throws Exception
    {
        synchronized (this.sessoes)
        {
            seguranca.Sessao atual = this.sessoes.buscar (q.getToken ());
            if (atual == null) return Resposta.erro (401, "Sessão expirada.");
            if (!atual.getPropriedadeId ().equals (propriedade (q)))
                return Resposta.erro (409, "A propriedade mudou. Recarregue antes de salvar.");
            return switch (operacao)
            {
                case "criar" -> this.criar (q);
                case "editar" -> this.atualizar (q);
                case "excluir" -> this.excluir (q);
                default -> this.movimentar (q);
            };
        }
    }

    /* Identificador da fazenda ativa. Todo acesso ao repositório usa este valor. */
    private static String propriedade (Requisicao q)
    {
        return q.getSessao ().getPropriedadeId ();
    }

    /*
        JSON enviado ao cliente. O status nasce do saldo, não de um campo gravado:
        Crítico se o saldo chegou ao limiar informado, Baixo se ficou abaixo do
        mínimo e Normal nos demais casos. propriedadeId não sai na resposta.
    */
    private static Document publico (Document item)
    {
        Document ret = Json.publico (item, "propriedadeId");
        double saldo = CalculosEstoque.numero (item, "saldo");
        double minimo = CalculosEstoque.numero (item, "minimo");
        Number limiar = item.get ("limiarCritico", Number.class);
        // Não assume a proporção 0,6 do mock. Sem limiar explícito, usa apenas
        // o alerta verificável de saldo abaixo do mínimo cadastrado.
        String status = limiar != null && saldo <= limiar.doubleValue () ? "Crítico" : saldo < minimo ? "Baixo" : "Normal";
        return ret.append ("status", status);
    }

    /* Lê um número do corpo: faixa de zero a um bilhão e no máximo as casas pedidas. */
    private static double numero (Document corpo, String campo, Validacao v, boolean opcional, int casas)
    {
        Object valor = corpo.get (campo);
        if (valor == null && opcional) return 0;
        if (!(valor instanceof Number) || !Double.isFinite (((Number) valor).doubleValue ()) ||
            ((Number) valor).doubleValue () < 0 || ((Number) valor).doubleValue () > 1000000000)
        {
            v.erro (campo, "Informe um número entre zero e um bilhão.");
            return 0;
        }
        double ret = ((Number) valor).doubleValue ();
        if (CalculosEstoque.arredondar (ret, casas) != ret)
            v.erro (campo, "Use no máximo " + casas + " casas decimais.");
        return ret;
    }

    /* Texto que pode faltar. Se vier, precisa ser string dentro do tamanho máximo. */
    private static String textoOpcional (Document corpo, String campo, Validacao v, int maximo)
    {
        Object valor = corpo.get (campo);
        if (valor == null) return "";
        if (!(valor instanceof String) || ((String) valor).trim ().length () > maximo)
        {
            v.erro (campo, "Use no máximo " + maximo + " caracteres.");
            return "";
        }
        return ((String) valor).trim ();
    }

    /*
        Monta o cadastro do insumo. Na criação o saldo inicial entra no documento;
        na edição este método não reescreve o saldo, porque só a movimentação muda quantidade.
    */
    private static Document lerInsumo (Document corpo, Validacao v, boolean criar)
    {
        String nome = v.texto ("nome", "Informe o nome.", 100);
        String categoria = v.texto ("categoria", "Selecione a categoria.", 30);
        String unidade = v.texto ("unidade", "Informe a unidade.", 12);
        if (categoria != null && !CATEGORIAS.contains (categoria)) v.erro ("categoria", "Categoria inválida.");
        double minimo = numero (corpo, "minimo", v, false, 6);
        double custo = numero (corpo, "custoUnitario", v, true, 6);
        Double limiar = corpo.get ("limiarCritico") == null ? null : numero (corpo, "limiarCritico", v, false, 6);
        if (limiar != null && limiar > minimo) v.erro ("limiarCritico", "O limiar crítico não pode superar o mínimo.");
        double saldo = criar ? numero (corpo, "saldo", v, false, 6) : 0;
        if (v.temErros ()) return null;
        /*
            append monta o cadastro do insumo ainda na memória. criar() em seguida
            coloca propriedadeId e o repositório grava na coleção insumos.

            {
              "nome": "Ureia",
              "categoria": "Fertilizante",   // uma de CATEGORIAS
              "unidade": "kg",
              "minimo": 4.0,                 // alerta de estoque baixo
              "custoUnitario": 2.5,          // custo médio; a movimentação pode mudar
              "limiarCritico": 1.0 ou null,  // se saldo <= limiar, status vira Crítico
              "atualizadoEm": "2026-...Z",
              "saldo": 10.0,                 // só na criação; edição não manda este campo
              "criadoEm": o mesmo instante de atualizadoEm, só na criação
            }
        */
        Document ret = new Document ("nome", nome).append ("categoria", categoria).append ("unidade", unidade)
            .append ("minimo", minimo).append ("custoUnitario", custo).append ("limiarCritico", limiar)
            .append ("atualizadoEm", Instant.now ().toString ());
        if (criar) ret.append ("saldo", saldo).append ("criadoEm", ret.getString ("atualizadoEm"));
        return ret;
    }

    /* Busca o insumo desta propriedade e trata o excluído logicamente como inexistente. */
    private Document ativo (Requisicao q) throws Exception
    {
        Document item = this.estoque.buscarPorId (propriedade (q), q.getParametro ("id"));
        return item == null || Boolean.TRUE.equals (item.getBoolean ("excluido")) ? null : item;
    }

    /* Lista o catálogo ativo da propriedade, já com o status calculado. */
    private Resposta listar (Requisicao q) throws Exception
    {
        ArrayList<Document> lista = new ArrayList<> ();
        for (Document item : this.estoque.listar (propriedade (q))) lista.add (publico (item));
        return Resposta.json (200, Json.escrever (lista));
    }

    /* Devolve um insumo ativo. Arquivado ou de outra fazenda responde 404. */
    private Resposta consultar (Requisicao q) throws Exception
    {
        Document item = this.ativo (q);
        return item == null ? Resposta.erro (404, "Produto não encontrado.") : Resposta.json (200, publico (item));
    }

    /* Cria o produto nesta propriedade. O repositório registra o saldo inicial no histórico. */
    private Resposta criar (Requisicao q) throws Exception
    {
        Document corpo = q.getCorpoJson ();
        Validacao v = new Validacao (corpo);
        Document item = lerInsumo (corpo, v, true);
        if (item == null) return v.resposta ();
        item.append ("propriedadeId", propriedade (q));
        this.estoque.criar (item);
        return Resposta.json (201, publico (item));
    }

    /* Atualiza nome, categoria, unidade, mínimo e custos. O saldo fica fora desta rota. */
    private Resposta atualizar (Requisicao q) throws Exception
    {
        Document anterior = this.ativo (q);
        if (anterior == null) return Resposta.erro (404, "Produto não encontrado.");
        Document corpo = q.getCorpoJson ();
        Validacao v = new Validacao (corpo);
        Document campos = lerInsumo (corpo, v, false);
        /*
            Trocar kg por L reinterpretaria saldos antigos. A unidade só muda
            antes da primeira movimentação e com saldo zero.
        */
        if (campos != null && !anterior.getString ("unidade").equals (campos.getString ("unidade")) &&
            (CalculosEstoque.numero (anterior, "saldo") != 0 || !this.estoque.listarMovimentacoes (propriedade (q), q.getParametro ("id")).isEmpty ()))
            v.erro ("unidade", "Crie outro produto para mudar a unidade de um item com saldo ou histórico.");
        if (v.temErros ()) return v.resposta ();
        if (!this.estoque.atualizar (propriedade (q), q.getParametro ("id"), campos)) return Resposta.erro (404, "Produto não encontrado.");
        return this.consultar (q);
    }

    /* Exclusão lógica. O repositório recusa se ainda houver saldo, para não sumir quantidade. */
    private Resposta excluir (Requisicao q) throws Exception
    {
        if (this.ativo (q) == null) return Resposta.erro (404, "Produto não encontrado.");
        if (!this.estoque.excluir (propriedade (q), q.getParametro ("id"))) return Resposta.erro (409, "Zere o saldo antes de excluir.");
        return Resposta.semConteudo ();
    }

    /* O cadastro de atividade usa titulo ou nome, conforme a origem do documento. */
    private static String tituloAtividade (Document atividade)
    {
        return atividade.getString ("titulo") != null ? atividade.getString ("titulo") : atividade.getString ("nome");
    }

    /* Lista atividades do usuário nesta propriedade para o cliente escolher o vínculo da saída. */
    private Resposta atividades (Requisicao q) throws Exception
    {
        ArrayList<Document> lista = new ArrayList<> ();
        for (Document item : this.estoque.listarAtividades (q.getSessao ().getUsuarioId (), propriedade (q)))
            lista.add (new Document ("id", item.getObjectId ("_id").toHexString ()).append ("nome", tituloAtividade (item)));
        return Resposta.json (200, Json.escrever (lista));
    }

    /*
        Registra entrada, saída ou ajuste e atualiza o saldo na mesma operação
        do repositório. Tipos: entrada aumenta, saída diminui, ajuste substitui
        o saldo pela quantidade informada. Só a saída pode citar uma atividade.
        Se o corpo não manda custo, a saída vinculada usa o custo médio atual
        do produto. Esse valor fica na movimentação: compras futuras não
        reescrevem o custo já gerado.

        Depois da baixa confirmada, o custo de consumo é lançado no financeiro
        e o espelho copia o evento para o histórico do talhão. Uma falha nessa
        etapa não desfaz o saldo; o handler tenta sincronizar e, se ainda
        faltar o custo, devolve custoPendente para o cliente saber.
    */
    private Resposta movimentar (Requisicao q) throws Exception
    {
        Document produto = this.ativo (q);
        if (produto == null) return Resposta.erro (404, "Produto não encontrado.");
        Document corpo = q.getCorpoJson ();
        Validacao v = new Validacao (corpo);
        String tipo = v.texto ("tipo", "Informe o tipo.", 10);
        if (tipo != null && !List.of ("entrada", "saida", "ajuste").contains (tipo)) v.erro ("tipo", "Tipo inválido.");
        double quantidade = numero (corpo, "quantidade", v, false, 6);
        if (!"ajuste".equals (tipo) && quantidade == 0) v.erro ("quantidade", "Informe quantidade maior que zero.");
        String observacao = textoOpcional (corpo, "observacao", v, 240);
        if ("ajuste".equals (tipo) && observacao.isBlank ()) v.erro ("observacao", "Explique o motivo do ajuste.");
        String fornecedor = textoOpcional (corpo, "fornecedor", v, 120);
        String atividadeId = textoOpcional (corpo, "atividadeId", v, 24);
        Double custo = corpo.get ("custoUnitario") == null ? null : numero (corpo, "custoUnitario", v, false, 6);
        Document atividade = null;
        if (!atividadeId.isBlank ())
        {
            if (!"saida".equals (tipo)) v.erro ("atividadeId", "Somente saídas podem consumir para uma atividade.");
            atividade = this.estoque.buscarAtividade (q.getSessao ().getUsuarioId (), propriedade (q), atividadeId);
            if (atividade == null) v.erro ("atividadeId", "Selecione uma atividade existente desta propriedade.");
        }
        if (v.temErros ()) return v.resposta ();
        /* O consumo guarda o custo médio da baixa: o custo gerado não muda com compras futuras. */
        if (atividade != null && custo == null) custo = CalculosEstoque.numero (produto, "custoUnitario");

        ClienteDeAlgoritmos.ResultadoMovimentacao remoto = ClienteDeAlgoritmos.calcularMovimentacao (
            CalculosEstoque.numero (produto, "saldo"),
            CalculosEstoque.numero (produto, "custoUnitario"),
            tipo,
            quantidade,
            custo,
            observacao);

        if (remoto.respostaErro != null)
            return remoto.respostaErro;

        /*
            Documento da movimentação, ainda sem saldoAnterior/saldoPosterior.
            CalculosEstoque.movimentar completa esses campos e o repositório
            insere o resultado em movimentacoes_insumos, junto com o update
            do saldo em insumos.

            {
              "tipo": "entrada" | "saida" | "ajuste",
              "quantidade": 5.0,
              "observacao": "",
              "fornecedor": "",
              "atividadeId": null ou o hex da atividade (só saída),
              "atividade": "" ou o título, para o histórico continuar legível,
              "custoUnitario": preço desta baixa/compra, ou null,
              "criadoEm": "2026-...Z"
            }

            O append de custoId mais abaixo NÃO entra nesse documento do banco.
            Ele entra só no JSON da resposta 201, para a tela saber qual custo nasceu.
        */
        Document movimento = new Document ("tipo", tipo).append ("quantidade", quantidade).append ("observacao", observacao)
            .append ("fornecedor", fornecedor).append ("atividadeId", atividadeId.isBlank () ? null : atividadeId)
            .append ("atividade", atividade == null ? "" : tituloAtividade (atividade)).append ("custoUnitario", custo)
            .append ("criadoEm", Instant.now ().toString ());
        Document atualizado = this.estoque.movimentar (propriedade (q), q.getParametro ("id"), movimento);
        if (atualizado == null) return Resposta.json (409, new Document ("error", "Saldo insuficiente.")
            .append ("fields", new Document ("quantidade", "A saída supera o saldo disponível.")));
        Document ret = Json.publico (movimento, "propriedadeId", "insumoId");
        if (this.consumo != null && atividade != null)
        {
            /*
                A baixa já está gravada: uma falha aqui não pode virar erro e induzir outra baixa.
                Se o registro pontual falhar, sincronizar () tenta recuperar na própria escrita.
                O espelho só roda quando o custo existe, para o histórico do talhão não ficar sem valor.
            */
            String propriedadeId = propriedade (q);
            try
            {
                Document gerado = this.consumo.registrar (propriedadeId, movimento, atualizado, atividade);
                if (gerado != null)
                {
                    ret.append ("custoId", Json.publico (gerado).getString ("id")).append ("custoValor", gerado.get ("value"));
                    if (this.espelhoHistorico != null)
                        this.espelhoHistorico.consumoDeInsumo (propriedadeId, atividade, gerado);
                }
            }
            catch (Exception erro)
            {
                Document recuperado = null;
                try
                {
                    this.consumo.sincronizar (q.getSessao ().getUsuarioId (), propriedadeId);
                    recuperado = this.consumo.daMovimentacao (propriedadeId, Json.publico (movimento).getString ("id"));
                }
                catch (Exception ignorado)
                {
                    recuperado = null;
                }
                if (recuperado != null)
                {
                    ret.append ("custoId", Json.publico (recuperado).getString ("id")).append ("custoValor", recuperado.get ("value"));
                    if (this.espelhoHistorico != null)
                        this.espelhoHistorico.consumoDeInsumo (propriedadeId, atividade, recuperado);
                }
                else
                    ret.append ("custoPendente", true);
            }
        }
        return Resposta.json (201, ret);
    }

    /* Histórico de movimentações do insumo, inclusive depois da exclusão lógica do produto. */
    private Resposta historico (Requisicao q) throws Exception
    {
        if (this.estoque.buscarPorId (propriedade (q), q.getParametro ("id")) == null) return Resposta.erro (404, "Produto não encontrado.");
        ArrayList<Document> lista = new ArrayList<> ();
        for (Document item : this.estoque.listarMovimentacoes (propriedade (q), q.getParametro ("id")))
            lista.add (Json.publico (item, "propriedadeId", "insumoId"));
        return Resposta.json (200, Json.escrever (lista));
    }

    /*
        Indicadores da propriedade: produtos, críticos, abaixo do mínimo,
        entradas do mês corrente e valor em estoque (saldo vezes custo médio).
        YearMonth compara mês e ano no fuso da operação, inclusive na virada UTC.
    */
    private Resposta resumo (Requisicao q) throws Exception
    {
        List<Document> itens = this.estoque.listar (propriedade (q));
        int criticos = 0, abaixoMinimo = 0, entradas = 0;
        double valor = 0;
        YearMonth mes = YearMonth.now (FUSO);
        for (Document item : itens)
        {
            if (CalculosEstoque.numero (item, "saldo") < CalculosEstoque.numero (item, "minimo")) abaixoMinimo++;
            if ("Crítico".equals (publico (item).getString ("status"))) criticos++;
            valor += CalculosEstoque.numero (item, "saldo") * CalculosEstoque.numero (item, "custoUnitario");
        }
        for (Document m : this.estoque.listarMovimentacoes (propriedade (q), null))
            if ("entrada".equals (m.getString ("tipo")) && mes.equals (YearMonth.from (Instant.parse (m.getString ("criadoEm")).atZone (FUSO)))) entradas++;
        return Resposta.json (200, new Document ("totalProdutos", itens.size ()).append ("criticos", criticos)
            .append ("abaixoMinimo", abaixoMinimo).append ("entradasNoMes", entradas)
            .append ("valorEmEstoque", CalculosEstoque.arredondar (valor, 2)));
    }
}
