// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.operacao, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.operacao;

// Importa java.time.* para utilizar os tipos deste módulo nas operações abaixo.
import java.time.*;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa br.com.agrogestao.core.domain.ocorrencias.Ocorrencia para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.ocorrencias.Ocorrencia;
// Disponibiliza diretamente os utilitários de br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*, usados nas validações ou consultas abaixo.
import static br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*;

// Agrupa as verificações de DadosOcorrencia nesta classe.
public final class DadosOcorrenciaTest {
    // Executa a sequência de inicialização ou as verificações desta classe a partir da linha de comando.
    public static void main(String[] args) {
        // Define titular e propriedade que serão usados neste cenário de teste.
        EscopoPropriedade escopo = new EscopoPropriedade(new ObjectId(), new ObjectId());
        // Define o escopo e os vínculos de talhão e safra que serão usados neste cenário de teste.
        ContextoOperacao contexto = new ContextoOperacao(escopo, new ObjectId(), new ObjectId());
        // Cria ocorrência fictícia com contexto operacional e data conhecida.
        Ocorrencia o = new Ocorrencia(new ObjectId(), contexto, "Praga no Talhão A", "Praga", LocalDate.of(2026, 10, 23),
                // Inclui prioridade, descrição e foto HTTPS para verificar a preservação desses dados.
                Prioridade.ALTA, "Folhas com sinais de praga", "https://exemplo.test/foto.jpg", Instant.now());
        // Confere a expectativa do teste: ocorrência e foto https preservadas no BSON.
        verificar(Ocorrencia.doDocumento(o.paraDocumento()).equals(o), "ocorrência e foto https preservadas no BSON");
        // Confere a expectativa do teste: acompanhamento permitido.
        verificar(Ocorrencia.permiteTransicao("ABERTA", "EM_ACOMPANHAMENTO"), "acompanhamento permitido");
        // Confere a expectativa do teste: novas anotações permitidas.
        verificar(Ocorrencia.permiteTransicao("EM_ACOMPANHAMENTO", "EM_ACOMPANHAMENTO"), "novas anotações permitidas");
        // Confere a expectativa do teste: resolução permitida.
        verificar(Ocorrencia.permiteTransicao("EM_ACOMPANHAMENTO", "RESOLVIDA"), "resolução permitida");
        // Confere a expectativa do teste: resolvida preserva desfecho.
        verificar(!Ocorrencia.permiteTransicao("RESOLVIDA", "ABERTA"), "resolvida preserva desfecho");
        // Cria ocorrência geral da propriedade sem exigir talhão ou safra.
        Ocorrencia geral = new Ocorrencia(null, new ContextoOperacao(escopo, null, null), "Equipamento", "Problema operacional",
                // Completa o registro geral com data atual, prioridade média e ausência de foto.
                LocalDate.now(), Prioridade.MEDIA, "Problema geral da propriedade", null, Instant.now());
        // Confere a expectativa do teste: ocorrência geral pode omitir talhão e safra.
        verificar(Ocorrencia.doDocumento(geral.paraDocumento()).equals(geral), "ocorrência geral pode omitir talhão e safra");
        // Confere que o caso inválido de dados do domínio é rejeitado pela validação.
        invalido(() -> new Ocorrencia(null, null, "Praga", "Praga", LocalDate.now(),
                // Completa o exemplo sem contexto obrigatório para confirmar a rejeição no construtor.
                Prioridade.ALTA, "Descrição", null, Instant.now()));
        // Esquema javascript: e caminhos relativos não entram no allowlist http(s).
        // Confere que o caso inválido de dados do domínio é rejeitado pela validação.
        invalido(() -> new Ocorrencia(null, contexto, "Praga", "Praga", LocalDate.now(),
                // Usa URL javascript: para conferir que um esquema ativo não entra na lista permitida.
                Prioridade.ALTA, "Descrição", "javascript:alert(1)", Instant.now()));
        // Confere que o caso inválido de dados do domínio é rejeitado pela validação.
        invalido(() -> new Ocorrencia(null, contexto, "Praga", "Praga", LocalDate.now(),
                // Usa caminho relativo para conferir que o modelo exige endereço HTTP ou HTTPS.
                Prioridade.ALTA, "Descrição", "/uploads/foto.jpg", Instant.now()));
        // Exibe o resultado desta etapa no console: DadosOcorrenciaTest: OK.
        System.out.println("DadosOcorrenciaTest: OK");
    }
}
