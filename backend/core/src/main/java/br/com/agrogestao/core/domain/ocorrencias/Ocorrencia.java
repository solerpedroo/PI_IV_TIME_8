// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.ocorrencias, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.ocorrencias;

// Importa os instantes de criação e dos eventos.
import java.time.Instant;
// Importa as datas de calendário sem horário ou fuso.
import java.time.LocalDate;
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

// Define o registro imutável Ocorrencia com os identificadores e dados que serão validados no construtor.
public record Ocorrencia(ObjectId id, ContextoOperacao contexto, String titulo, String tipo,
        // Acrescenta data, prioridade, descrição, foto opcional e instante de criação da ocorrência.
        LocalDate data, Prioridade prioridade, String descricao, String fotoUrl, Instant criadaEm) {
    // Define as situações possíveis, mantendo RESOLVIDA como estado final da ocorrência.
    public enum Status { ABERTA, EM_ACOMPANHAMENTO, RESOLVIDA }

    // Valida os componentes de Ocorrencia antes que uma instância imutável possa ser utilizada.
    public Ocorrencia {
        // Exige o preenchimento de contexto antes de utilizar esse componente.
        obrigatorio(contexto, "contexto");
        // Exige titulo não vazio e remove os espaços das extremidades.
        titulo = texto(titulo, "titulo");
        // Exige tipo não vazio e remove os espaços das extremidades.
        tipo = texto(tipo, "tipo");
        // Exige o preenchimento de data antes de utilizar esse componente.
        obrigatorio(data, "data");
        // Exige o preenchimento de prioridade antes de utilizar esse componente.
        obrigatorio(prioridade, "prioridade");
        // Exige descricao não vazio e remove os espaços das extremidades.
        descricao = texto(descricao, "descricao");
        // Foto é opcional; se vier, só aceitamos http(s) para evitar javascript: e esquemas internos.
        // Normaliza a foto opcional e rejeita esquemas de URL fora de HTTP e HTTPS.
        fotoUrl = normalizarFoto(fotoUrl);
        // Normaliza o instante de criação a milissegundos para preservar o valor ao converter para BSON.
        criadaEm = obrigatorio(criadaEm, "criadaEm").truncatedTo(ChronoUnit.MILLIS);
    }

    // Aceita foto opcional e limita os endereços informados aos esquemas HTTP e HTTPS.
    private static String normalizarFoto(String fotoUrl) {
        // Representa a ausência de foto por null, inclusive quando o texto só contém espaços.
        if (fotoUrl == null || fotoUrl.isBlank()) return null;
        // Remove espaços nas extremidades da URL informada.
        String url = fotoUrl.trim();
        // Prepara uma cópia em minúsculas somente para comparar o esquema da URL.
        String minuscula = url.toLowerCase();
        // Permite exclusivamente os prefixos HTTP e HTTPS no endereço da foto.
        if (!(minuscula.startsWith("https://") || minuscula.startsWith("http://"))) {
            // Rejeita endereços relativos e esquemas como javascript: antes de armazená-los.
            throw new IllegalArgumentException("fotoUrl deve começar com http:// ou https://");
        }
        // Devolve a URL aparada, preservando a capitalização do caminho original.
        return url;
    }

    // Decide se a mudança entre o estado anterior e o solicitado é permitida pelo domínio.
    public static boolean permiteTransicao(String anterior, String proximo) {
        // Interpreta o nome do estado atual conforme o enum de ocorrências.
        Status de = Status.valueOf(anterior);
        // Interpreta o próximo estado solicitado conforme o mesmo enum.
        Status para = Status.valueOf(proximo);
        // Permite acompanhamento ou resolução enquanto a ocorrência não estiver resolvida, inclusive novas anotações de acompanhamento.
        return de != Status.RESOLVIDA && (para == Status.EM_ACOMPANHAMENTO || para == Status.RESOLVIDA);
    }

    // Converte o objeto de domínio em documento BSON, preservando os tipos exigidos na persistência.
    public Document paraDocumento() {
        // Preenche no BSON os campos titulo, tipo, data e representa a data ou o horário como texto ISO.
        Document d = contexto.documento().append("titulo", titulo).append("tipo", tipo).append("data", data.toString())
                // Preenche no BSON os campos prioridade, descricao, fotoUrl e persiste o enum pelo seu nome.
                .append("prioridade", prioridade.name()).append("descricao", descricao).append("fotoUrl", fotoUrl)
                // Preenche no BSON os campos criadaEm, statusInicial e converte o instante para a data BSON; persiste o enum pelo seu nome.
                .append("criadaEm", Date.from(criadaEm)).append("statusInicial", Status.ABERTA.name());
        // Inclui _id somente quando já existe identificador; caso contrário, a inserção poderá gerá-lo.
        if (id != null) d.put("_id", id);
        // Entrega o documento BSON completo ao chamador para persistência ou comparação.
        return d;
    }

    // Reconstrói o objeto de domínio a partir do BSON, passando novamente pelas validações do construtor.
    public static Ocorrencia doDocumento(Document d) {
        // Reconstrói Ocorrencia com os campos lidos do documento BSON, submetendo-os às validações do modelo.
        return new Ocorrencia(d.getObjectId("_id"), ContextoOperacao.doDocumento(d), d.getString("titulo"),
                // Lê tipo, data, prioridade do BSON; interpreta data/horário ISO; recupera o enum pelo nome.
                d.getString("tipo"), LocalDate.parse(d.getString("data")), Prioridade.valueOf(d.getString("prioridade")),
                // Lê descricao, fotoUrl, criadaEm do BSON; restaura o instante.
                d.getString("descricao"), d.getString("fotoUrl"), d.getDate("criadaEm").toInstant());
    }
}
