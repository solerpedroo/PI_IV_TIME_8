// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.operacao, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.operacao;

// Importa os instantes de criação e dos eventos.
import java.time.Instant;
// Importa a unidade de milissegundos usada ao normalizar instantes para BSON.
import java.time.temporal.ChronoUnit;
// Importa a ponte entre Instant e o tipo de data aceito pelo driver BSON.
import java.util.Date;
// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Disponibiliza diretamente os utilitários de br.com.agrogestao.core.domain.operacao.RegrasDados.*, usados nas validações ou consultas abaixo.
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

/** Eventos persistidos começam em 1; o evento 0 é a criação guardada no documento principal. */
// Define o registro imutável EventoHistorico com os identificadores e dados que serão validados no construtor.
public record EventoHistorico(int ordem, Instant registradoEm, String status, String descricao) {
    // Valida os componentes de EventoHistorico antes que uma instância imutável possa ser utilizada.
    public EventoHistorico {
        // Impede sequência negativa; a posição zero permanece reservada à criação do registro.
        if (ordem < 0) throw new IllegalArgumentException("ordem não pode ser negativa");
        // Exige o instante do evento e o normaliza à precisão de milissegundos do BSON.
        registradoEm = obrigatorio(registradoEm, "registradoEm").truncatedTo(ChronoUnit.MILLIS);
        // Exige status não vazio e remove os espaços das extremidades.
        status = texto(status, "status");
        // Exige descricao não vazio e remove os espaços das extremidades.
        descricao = texto(descricao, "descricao");
    }

    // Converte o objeto de domínio em documento BSON, preservando os tipos exigidos na persistência.
    public Document paraDocumento() {
        // Reconstrói Document com os identificadores e valores recebidos para este cenário.
        return new Document("ordem", ordem).append("registradoEm", Date.from(registradoEm))
                // Preenche no BSON os campos status, descricao.
                .append("status", status).append("descricao", descricao);
    }

    // Reconstrói o objeto de domínio a partir do BSON, passando novamente pelas validações do construtor.
    public static EventoHistorico doDocumento(Document d) {
        // Reconstrói EventoHistorico com os campos lidos do documento BSON, submetendo-os às validações do modelo.
        return new EventoHistorico(d.getInteger("ordem"), d.getDate("registradoEm").toInstant(),
                // Lê status, descricao do BSON.
                d.getString("status"), d.getString("descricao"));
    }
}
