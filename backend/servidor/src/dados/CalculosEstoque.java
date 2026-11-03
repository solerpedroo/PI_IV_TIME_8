/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package dados;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.bson.Document;

/* Regras compartilhadas pelos dois repositórios para obter o mesmo resultado.
   Quantidades usam seis casas decimais; custo médio usa seis e totais monetários duas. */
public class CalculosEstoque
{
    // O saldo inicial também tem origem auditável e não desaparece do histórico.
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

    public static double numero (Document documento, String campo)
    {
        Number valor = documento.get (campo, Number.class);
        return valor == null ? 0 : valor.doubleValue ();
    }

    public static double arredondar (double valor, int casas)
    {
        return BigDecimal.valueOf (valor).setScale (casas, RoundingMode.HALF_UP).doubleValue ();
    }

    // Devolve null se faltar saldo. Não altera nada antes de validar a saída.
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

        // Uma entrada sem custo informado conserva o custo médio anterior.
        // Com preço informado, pondera o estoque anterior e a compra recebida.
        if ("entrada".equals (tipo) && movimento.get ("custoUnitario") != null)
            custo = arredondar ((anterior * custo + quantidade * numero (movimento, "custoUnitario")) / novo, 6);

        movimento.append ("saldoAnterior", anterior).append ("saldoPosterior", novo)
                 .append ("unidade", insumo.getString ("unidade"));
        Document atualizado = new Document (insumo);
        atualizado.append ("saldo", novo).append ("custoUnitario", custo)
                  .append ("atualizadoEm", movimento.getString ("criadoEm"));
        return atualizado;
    }
}
