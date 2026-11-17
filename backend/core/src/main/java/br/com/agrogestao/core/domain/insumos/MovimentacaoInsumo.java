// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.insumos, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.insumos;

// Importa a aritmética decimal sem as aproximações de ponto flutuante binário.
import java.math.BigDecimal;
// Importa os instantes de criação e dos eventos.
import java.time.Instant;
// Importa a unidade de milissegundos usada ao normalizar instantes para BSON.
import java.time.temporal.ChronoUnit;
// Importa a ponte entre Instant e o tipo de data aceito pelo driver BSON.
import java.util.Date;
// Importa a normalização textual independente do idioma do sistema.
import java.util.Locale;
// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Importa a representação decimal exata do BSON.
import org.bson.types.Decimal128;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa br.com.agrogestao.core.domain.operacao.EscopoPropriedade para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
// Disponibiliza diretamente os utilitários de br.com.agrogestao.core.domain.operacao.RegrasDados.*, usados nas validações ou consultas abaixo.
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

// Define o registro imutável MovimentacaoInsumo com os identificadores e dados que serão validados no construtor.
public record MovimentacaoInsumo(ObjectId id, EscopoPropriedade escopo, ObjectId insumoId, Tipo tipo,
        // Acrescenta quantidade, unidade, atividade consumidora, instante e justificativa da movimentação.
        BigDecimal quantidade, String unidade, ObjectId atividadeId, Instant ocorridaEm, String motivo) {
    // Diferencia entrada, saída e ajuste para determinar o efeito da quantidade no estoque.
    public enum Tipo { ENTRADA, SAIDA, AJUSTE }

    // Valida os componentes de MovimentacaoInsumo antes que uma instância imutável possa ser utilizada.
    public MovimentacaoInsumo {
        // Exige o preenchimento de escopo antes de utilizar esse componente.
        obrigatorio(escopo, "escopo");
        // Exige o preenchimento de insumoId antes de utilizar esse componente.
        obrigatorio(insumoId, "insumoId");
        // Exige o preenchimento de tipo antes de utilizar esse componente.
        obrigatorio(tipo, "tipo");
        // Exige o preenchimento de quantidade antes de utilizar esse componente.
        obrigatorio(quantidade, "quantidade");
        // Impede quantidade zero e valores negativos nas entradas e saídas; ajustes podem ter sinal negativo.
        if (quantidade.signum() == 0 || (tipo != Tipo.AJUSTE && quantidade.signum() < 0)) {
            // Informa que somente o ajuste aceita uma diferença negativa de quantidade.
            throw new IllegalArgumentException("quantidade deve ser positiva; ajuste admite diferença negativa");
        }
        // Confere a representabilidade da quantidade como Decimal128 antes da persistência.
        new Decimal128(quantidade);
        // Exige unidade não vazio e remove os espaços das extremidades; normaliza também a unidade em minúsculas.
        unidade = texto(unidade, "unidade").toLowerCase(Locale.ROOT);
        // Quando existe atividade consumidora, a movimentação precisa ser uma saída de insumo.
        if (atividadeId != null && tipo != Tipo.SAIDA) {
            // Rejeita associação de uma atividade consumidora a uma entrada ou a um ajuste.
            throw new IllegalArgumentException("atividade consumidora só pode acompanhar uma saída");
        }
        // Preserva o instante da operação na precisão de milissegundos suportada pelas datas BSON.
        ocorridaEm = obrigatorio(ocorridaEm, "ocorridaEm").truncatedTo(ChronoUnit.MILLIS);
        // Exige motivo não vazio e remove os espaços das extremidades.
        motivo = texto(motivo, "motivo");
    }

    // Converte saídas em variação negativa; entradas e ajustes mantêm o sinal informado.
    public BigDecimal variacao() { return tipo == Tipo.SAIDA ? quantidade.negate() : quantidade; }

    // Converte o objeto de domínio em documento BSON, preservando os tipos exigidos na persistência.
    public Document paraDocumento() {
        // Preenche no BSON os campos insumoId, tipo e persiste o enum pelo seu nome.
        Document d = escopo.documento().append("insumoId", insumoId).append("tipo", tipo.name())
                // Preenche no BSON os campos quantidade, unidade e preserva os números decimais com Decimal128.
                .append("quantidade", new Decimal128(quantidade)).append("unidade", unidade)
                // Preenche no BSON os campos atividadeId, ocorridaEm, motivo e converte o instante para a data BSON.
                .append("atividadeId", atividadeId).append("ocorridaEm", Date.from(ocorridaEm)).append("motivo", motivo);
        // Inclui _id somente quando já existe identificador; caso contrário, a inserção poderá gerá-lo.
        if (id != null) d.put("_id", id);
        // Entrega o documento BSON completo ao chamador para persistência ou comparação.
        return d;
    }

    // Reconstrói o objeto de domínio a partir do BSON, passando novamente pelas validações do construtor.
    public static MovimentacaoInsumo doDocumento(Document d) {
        // Reconstrói MovimentacaoInsumo com os campos lidos do documento BSON, submetendo-os às validações do modelo.
        return new MovimentacaoInsumo(d.getObjectId("_id"), EscopoPropriedade.doDocumento(d), d.getObjectId("insumoId"),
                // Lê tipo, quantidade do BSON; converte o decimal para BigDecimal; recupera o enum pelo nome.
                Tipo.valueOf(d.getString("tipo")), d.get("quantidade", Decimal128.class).bigDecimalValue(),
                // Lê unidade, atividadeId, ocorridaEm, motivo do BSON; restaura o instante.
                d.getString("unidade"), d.getObjectId("atividadeId"), d.getDate("ocorridaEm").toInstant(), d.getString("motivo"));
    }
}
