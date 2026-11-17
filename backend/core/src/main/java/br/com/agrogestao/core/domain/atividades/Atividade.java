// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.atividades, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.atividades;

// Importa os instantes de criação e dos eventos.
import java.time.Instant;
// Importa as datas de calendário sem horário ou fuso.
import java.time.LocalDate;
// Importa os horários locais das atividades.
import java.time.LocalTime;
// Importa a unidade de milissegundos usada ao normalizar instantes para BSON.
import java.time.temporal.ChronoUnit;
// Importa a ponte entre Instant e o tipo de data aceito pelo driver BSON.
import java.util.Date;
// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa br.com.agrogestao.core.domain.operacao.* para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.operacao.*;
// Disponibiliza diretamente os utilitários de br.com.agrogestao.core.domain.operacao.RegrasDados.*, usados nas validações ou consultas abaixo.
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

// Define o registro imutável Atividade com os identificadores e dados que serão validados no construtor.
public record Atividade(ObjectId id, ContextoOperacao contexto, String titulo, String tipo,
        // Completa os dados com responsável, data, horário, prioridade e instante de criação.
        String responsavel, LocalDate data, LocalTime horario, Prioridade prioridade, Instant criadaEm) {
    // Restringe o estado da atividade às quatro situações previstas pelo domínio.
    public enum Status { AGENDADA, EM_ANDAMENTO, CONCLUIDA, CANCELADA }

    // Valida os componentes de Atividade antes que uma instância imutável possa ser utilizada.
    public Atividade {
        // Exige o preenchimento de contexto antes de utilizar esse componente.
        obrigatorio(contexto, "contexto");
        // Exige titulo não vazio e remove os espaços das extremidades.
        titulo = texto(titulo, "titulo");
        // Exige tipo não vazio e remove os espaços das extremidades.
        tipo = texto(tipo, "tipo");
        // Exige responsavel não vazio e remove os espaços das extremidades.
        responsavel = texto(responsavel, "responsavel");
        // Exige o preenchimento de data antes de utilizar esse componente.
        obrigatorio(data, "data");
        // Exige o preenchimento de horario antes de utilizar esse componente.
        obrigatorio(horario, "horario");
        // Exige o preenchimento de prioridade antes de utilizar esse componente.
        obrigatorio(prioridade, "prioridade");
        // Exige o instante de criação e reduz a precisão a milissegundos, como nas datas BSON.
        criadaEm = obrigatorio(criadaEm, "criadaEm").truncatedTo(ChronoUnit.MILLIS);
    }

    // Decide se a mudança entre o estado anterior e o solicitado é permitida pelo domínio.
    public static boolean permiteTransicao(String anterior, String proximo) {
        // Converte o nome do estado anterior para o enum, rejeitando nomes desconhecidos.
        Status de = Status.valueOf(anterior);
        // Converte o estado solicitado para compará-lo com as transições permitidas.
        Status para = Status.valueOf(proximo);
        // Uma atividade agendada pode iniciar a execução ou ser cancelada.
        return (de == Status.AGENDADA && (para == Status.EM_ANDAMENTO || para == Status.CANCELADA))
                // Uma atividade em execução pode terminar ou ser cancelada; estados finais não avançam.
                || (de == Status.EM_ANDAMENTO && (para == Status.CONCLUIDA || para == Status.CANCELADA));
    }

    // Converte o objeto de domínio em documento BSON, preservando os tipos exigidos na persistência.
    public Document paraDocumento() {
        // Preenche no BSON os campos titulo, tipo.
        Document d = contexto.documento().append("titulo", titulo).append("tipo", tipo)
                // Preenche no BSON os campos responsavel, data, horario e representa a data ou o horário como texto ISO.
                .append("responsavel", responsavel).append("data", data.toString()).append("horario", horario.toString())
                // Preenche no BSON os campos prioridade, criadaEm e converte o instante para a data BSON; persiste o enum pelo seu nome.
                .append("prioridade", prioridade.name()).append("criadaEm", Date.from(criadaEm))
                // Preenche no BSON os campos statusInicial e persiste o enum pelo seu nome.
                .append("statusInicial", Status.AGENDADA.name());
        // Inclui _id somente quando já existe identificador; caso contrário, a inserção poderá gerá-lo.
        if (id != null) d.put("_id", id);
        // Entrega o documento BSON completo ao chamador para persistência ou comparação.
        return d;
    }

    // Reconstrói o objeto de domínio a partir do BSON, passando novamente pelas validações do construtor.
    public static Atividade doDocumento(Document d) {
        // Reconstrói Atividade com os campos lidos do documento BSON, submetendo-os às validações do modelo.
        return new Atividade(d.getObjectId("_id"), ContextoOperacao.doDocumento(d), d.getString("titulo"),
                // Lê tipo, responsavel, data do BSON; interpreta data/horário ISO.
                d.getString("tipo"), d.getString("responsavel"), LocalDate.parse(d.getString("data")),
                // Lê horario, prioridade do BSON; interpreta data/horário ISO; recupera o enum pelo nome.
                LocalTime.parse(d.getString("horario")), Prioridade.valueOf(d.getString("prioridade")),
                // Lê criadaEm do BSON; restaura o instante.
                d.getDate("criadaEm").toInstant());
    }
}
