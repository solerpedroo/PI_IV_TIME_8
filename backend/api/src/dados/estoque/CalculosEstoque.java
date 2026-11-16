/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package dados.estoque;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.bson.Document;

/*
    Regras de saldo e custo compartilhadas pela memória e pelo Mongo.
    Os dois repositórios chamam estes métodos para o resultado não divergir
    conforme o modo de persistência. Aqui não há coleção nem transação:
    o método só calcula. Quem grava é o repositório, depois de receber
    o documento novo ou null.

    Quantidades usam seis casas decimais; custo médio usa seis e totais
    monetários duas. O arredondamento é half-up (meio para cima).
*/
public class CalculosEstoque
{
    /*
        Movimentação de abertura. O saldo inicial também tem origem auditável
        e não desaparece do histórico: tipo ajuste, saldo anterior zero e
        saldo posterior igual ao cadastrado.

        append NÃO grava no banco sozinho. Ele só acrescenta uma chave dentro
        do Document que está na memória (um mapa ordenado, tipo JSON). Quem
        persiste é RepositorioEstoqueMongo.criar, na coleção movimentacoes_insumos.

        O documento que sai daqui tem esta forma:

        {
          "_id": ObjectId novo,          // id só desta movimentação
          "propriedadeId": "...",        // copiado do insumo, para isolar a fazenda
          "insumoId": "hex do _id",      // aponta para a coleção insumos
          "tipo": "ajuste",              // abertura conta como ajuste, não como compra
          "quantidade": 10.0,            // saldo digitado no cadastro
          "saldoAnterior": 0.0,
          "saldoPosterior": 10.0,        // igual à quantidade, porque partiu de zero
          "unidade": "kg",
          "observacao": "Saldo inicial do cadastro",
          "custoUnitario": 2.5,          // custo médio no momento do cadastro
          "criadoEm": "2026-...T...Z"    // mesmo instante do insumo
        }
    */
    public static Document saldoInicial (Document insumo)
    {
        return new Document ("_id", new org.bson.types.ObjectId ())
            .append ("propriedadeId", insumo.getString ("propriedadeId"))
            .append ("insumoId", insumo.getObjectId ("_id").toHexString ())
            .append ("tipo", "ajuste").append ("quantidade", numero (insumo, "saldo"))
            .append ("saldoAnterior", 0.0).append ("saldoPosterior", numero (insumo, "saldo"))
            .append ("unidade", insumo.getString ("unidade")).append ("observacao", "Saldo inicial do cadastro")
            .append ("custoUnitario", numero (insumo, "custoUnitario"))
            .append ("criadoEm", insumo.getString ("criadoEm"));
    }

    /* Lê um Number do documento. Campo ausente vale zero, para a conta não quebrar. */
    public static double numero (Document documento, String campo)
    {
        Number valor = documento.get (campo, Number.class);
        return valor == null ? 0 : valor.doubleValue ();
    }

    /* Arredonda com BigDecimal para evitar o erro binário do double em saldo e custo. */
    public static double arredondar (double valor, int casas)
    {
        return BigDecimal.valueOf (valor).setScale (casas, RoundingMode.HALF_UP).doubleValue ();
    }

    /*
        Aplica a movimentação no documento do insumo, sem gravar.
        Entrada soma a quantidade. Saída subtrai e devolve null se faltar saldo,
        antes de alterar qualquer campo. Ajuste troca o saldo pela quantidade
        informada (o novo estoque contado, não um delta).

        Na entrada com custo informado, o custo médio é ponderado:
        (saldo anterior * custo anterior + quantidade * custo da compra) / saldo novo.
        Entrada sem custo conserva o custo médio que já estava no produto.
        Saída e ajuste não recalculam esse custo.
    */
    public static Document movimentar (Document insumo, Document movimento)
    {
        double anterior = numero (insumo, "saldo");
        double quantidade = numero (movimento, "quantidade");
        String tipo = movimento.getString ("tipo");
        if ("saida".equals (tipo) && quantidade > anterior) return null;

        double novo = "entrada".equals (tipo) ? anterior + quantidade :
                      "saida".equals (tipo) ? anterior - quantidade : quantidade;
        novo = arredondar (novo, 6);
        double custo = numero (insumo, "custoUnitario");

        /*
            Uma entrada sem custo informado conserva o custo médio anterior.
            Com preço informado, pondera o estoque anterior e a compra recebida.
        */
        if ("entrada".equals (tipo) && movimento.get ("custoUnitario") != null)
            custo = arredondar ((anterior * custo + quantidade * numero (movimento, "custoUnitario")) / novo, 6);

        /*
            Dois documentos diferentes, dois destinos.

            movimento já veio do handler com tipo, quantidade, observação e criadoEm.
            Estes append entram NELE, ainda na memória. O repositório depois faz
            insertOne na coleção movimentacoes_insumos. O histórico passa a ter:

            {
              "tipo": "entrada" | "saida" | "ajuste",
              "quantidade": ...,
              "saldoAnterior": saldo de antes desta operação,
              "saldoPosterior": saldo depois,
              "unidade": copiada do produto (kg, L...),
              ... demais campos que o handler já tinha colocado
            }

            atualizado é uma CÓPIA do insumo. Os append abaixo mudam só essa cópia,
            que o repositório grava de volta na coleção insumos (update do mesmo _id):

            {
              ...cadastro antigo (nome, categoria, minimo, propriedadeId),
              "saldo": novo,
              "custoUnitario": custo médio recalculado ou o anterior,
              "atualizadoEm": o mesmo criadoEm da movimentação
            }

            Se a saída não couber, este método já devolveu null lá em cima e
            nenhum dos dois documentos é gravado.
        */
        movimento.append ("saldoAnterior", anterior).append ("saldoPosterior", novo)
                 .append ("unidade", insumo.getString ("unidade"));
        Document atualizado = new Document (insumo);
        atualizado.append ("saldo", novo).append ("custoUnitario", custo)
                  .append ("atualizadoEm", movimento.getString ("criadoEm"));
        return atualizado;
    }
}
