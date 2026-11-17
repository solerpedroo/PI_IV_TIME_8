// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.operacao, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.operacao;

// Importa java.time.* para utilizar os tipos deste módulo nas operações abaixo.
import java.time.*;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa br.com.agrogestao.core.domain.atividades.Atividade para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.atividades.Atividade;
// Disponibiliza diretamente os utilitários de br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*, usados nas validações ou consultas abaixo.
import static br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*;

// Agrupa as verificações de DadosAtividade nesta classe.
public final class DadosAtividadeTest {
    // Executa a sequência de inicialização ou as verificações desta classe a partir da linha de comando.
    public static void main(String[] args) {
        // Define titular e propriedade que serão usados neste cenário de teste.
        EscopoPropriedade escopo = new EscopoPropriedade(new ObjectId(), new ObjectId());
        // Define o escopo e os vínculos de talhão e safra que serão usados neste cenário de teste.
        ContextoOperacao contexto = new ContextoOperacao(escopo, new ObjectId(), new ObjectId());
        // Cria atividade fictícia com identificador, contexto e dados operacionais conhecidos.
        Atividade a = new Atividade(new ObjectId(), contexto, "Aplicar NPK", "Adubação", "João fictício",
                // Define data, horário e prioridade do exemplo e usa o instante atual como criação.
                LocalDate.of(2026, 10, 23), LocalTime.of(8, 30), Prioridade.ALTA, Instant.now());
        // Confere a expectativa do teste: atividade deve preservar campos e vínculos no BSON.
        verificar(Atividade.doDocumento(a.paraDocumento()).equals(a), "atividade deve preservar campos e vínculos no BSON");
        // Confere a expectativa do teste: início permitido.
        verificar(Atividade.permiteTransicao("AGENDADA", "EM_ANDAMENTO"), "início permitido");
        // Confere a expectativa do teste: conclusão permitida.
        verificar(Atividade.permiteTransicao("EM_ANDAMENTO", "CONCLUIDA"), "conclusão permitida");
        // Confere a expectativa do teste: histórico concluído deve permanecer finalizado.
        verificar(!Atividade.permiteTransicao("CONCLUIDA", "EM_ANDAMENTO"), "histórico concluído deve permanecer finalizado");
        // Confere a expectativa do teste: conclusão exige execução.
        verificar(!Atividade.permiteTransicao("AGENDADA", "CONCLUIDA"), "conclusão exige execução");
        // Cria o primeiro evento de execução para verificar a conversão do histórico em BSON.
        EventoHistorico e = new EventoHistorico(1, Instant.now(), "EM_ANDAMENTO", "Aplicação iniciada");
        // Confere a expectativa do teste: evento deve preservar data e sequência.
        verificar(EventoHistorico.doDocumento(e.paraDocumento()).equals(e), "evento deve preservar data e sequência");
        // Confere que informar safra sem talhão é rejeitado na construção do contexto.
        invalido(() -> new ContextoOperacao(escopo, null, new ObjectId()));
        // Confere que uma sequência negativa de histórico é rejeitada.
        invalido(() -> new EventoHistorico(-1, Instant.now(), "CONCLUIDA", "Inválido"));
        // Confere que um evento com descrição inválida não pode ser criado.
        invalido(() -> new EventoHistorico(1, Instant.now(), "CONCLUIDA", " "));
        // Exibe o resultado desta etapa no console: DadosAtividadeTest: OK.
        System.out.println("DadosAtividadeTest: OK");
    }
}
