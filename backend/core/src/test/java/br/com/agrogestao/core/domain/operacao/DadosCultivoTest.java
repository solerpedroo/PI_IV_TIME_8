// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Miguel — RA: 25003110
// Define o pacote br.com.agrogestao.core.domain.operacao, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.operacao;

// Importa a aritmética decimal sem as aproximações de ponto flutuante binário.
import java.math.BigDecimal;
// Importa as datas de calendário sem horário ou fuso.
import java.time.LocalDate;
// Importa as listas mutáveis usadas ao reunir resultados das consultas.
import java.util.ArrayList;
// Importa as listas de vínculos, eventos e resultados.
import java.util.List;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa br.com.agrogestao.core.domain.cultivo.* para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.cultivo.*;

// Agrupa as verificações de DadosCultivo nesta classe.
public final class DadosCultivoTest {
    // Executa a sequência de inicialização ou as verificações desta classe a partir da linha de comando.
    public static void main(String[] args) {
        // Define titular e propriedade que serão usados neste cenário de teste.
        EscopoPropriedade escopo = new EscopoPropriedade(new ObjectId(), new ObjectId());
        // Reserva um identificador exclusivo para talhaoId, evitando colisão com dados de outras execuções.
        ObjectId talhaoId = new ObjectId();
        // Reserva um identificador exclusivo para culturaId, evitando colisão com dados de outras execuções.
        ObjectId culturaId = new ObjectId();
        // Cria talhão com área decimal e espaços no nome para verificar normalização e persistência.
        Talhao talhao = new Talhao(talhaoId, escopo, " Talhão A ", new BigDecimal("12.75"), Talhao.Status.ATIVO);
        // Confere a expectativa do teste: área decimal e titular devem sobreviver ao BSON.
        verificar(Talhao.doDocumento(talhao.paraDocumento()).equals(talhao), "área decimal e titular devem sobreviver ao BSON");
        // Cria uma cultura vinculada ao mesmo titular e propriedade.
        Cultura cultura = new Cultura(culturaId, escopo, "Soja");
        // Confere a expectativa do teste: cultura deve preservar seu escopo.
        verificar(Cultura.doDocumento(cultura.paraDocumento()).equals(cultura), "cultura deve preservar seu escopo");
        // Usa lista mutável na entrada para testar a cópia defensiva feita pela safra.
        List<ObjectId> talhoes = new ArrayList<>(List.of(talhaoId));
        // Cria safra com período conhecido e a lista de talhões preparada para o teste.
        Safra safra = safra(escopo, culturaId, talhoes, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        // Esvazia a lista original para provar que isso não altera os vínculos guardados na safra.
        talhoes.clear();
        // Confere a expectativa do teste: lista original não pode alterar uma safra já validada.
        verificar(safra.talhaoIds().size() == 1, "lista original não pode alterar uma safra já validada");
        // Confere a expectativa do teste: período e vínculos devem sobreviver ao BSON.
        verificar(Safra.doDocumento(safra.paraDocumento()).equals(safra), "período e vínculos devem sobreviver ao BSON");
        // Cria um ciclo novo para testar todas as etapas até a finalização.
        Safra ciclo = Safra.nova(new ObjectId(), escopo, "Soja ciclo", culturaId, List.of(talhaoId),
                // Define o ano completo de 2026 e a área decimal conhecida desse ciclo.
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), new BigDecimal("12.75"));
        // Percorre as etapas seguintes na ordem oficial do ciclo, começando pela preparação.
        for (Safra.Status etapa : new Safra.Status[] { Safra.Status.PREPARACAO, Safra.Status.PLANTIO,
                // Avança de plantio até finalização, substituindo o record imutável a cada transição.
                Safra.Status.MANEJO, Safra.Status.COLHEITA, Safra.Status.FINALIZADA }) ciclo = ciclo.avancar(etapa);
        // Guarda a safra finalizada em referência estável para as verificações e lambdas.
        Safra finalizada = ciclo;
        // Confere a expectativa do teste: finalização deve permanecer no ciclo consultável.
        verificar(finalizada.status() == Safra.Status.FINALIZADA, "finalização deve permanecer no ciclo consultável");
        // Confere a expectativa do teste: trilha deve registrar as seis etapas oficiais.
        verificar(finalizada.historicoEtapas().size() == 6, "trilha deve registrar as seis etapas oficiais");
        // Confere a expectativa do teste: última marca do histórico deve ser a finalização.
        verificar(finalizada.historicoEtapas().getLast().etapa() == Safra.Status.FINALIZADA,
                // Identifica a expectativa deste caso: última marca do histórico deve ser a finalização.
                "última marca do histórico deve ser a finalização");
        // Confere que o escopo não pode ser criado sem o titular obrigatório.
        invalido(() -> new EscopoPropriedade(null, new ObjectId()));
        // Confere a rejeição de talhão com campo obrigatório ou referência inválida neste cenário.
        invalido(() -> new Talhao(null, escopo, "A", BigDecimal.ZERO, Talhao.Status.ATIVO));
        // Confere a rejeição de talhão com campo obrigatório ou referência inválida neste cenário.
        invalido(() -> new Talhao(null, escopo, " ", BigDecimal.ONE, Talhao.Status.ATIVO));
        // Confere que o caso inválido de safra é rejeitado pela validação.
        invalido(() -> safra(escopo, culturaId, List.of(talhaoId), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
        // Confere que o caso inválido de safra é rejeitado pela validação.
        invalido(() -> safra(escopo, culturaId, List.of(), LocalDate.now(), LocalDate.now()));
        // Confere que o caso inválido de safra é rejeitado pela validação.
        invalido(() -> safra(escopo, culturaId, List.of(talhaoId, talhaoId), LocalDate.now(), LocalDate.now()));
        // Confere que referências inexistentes são rejeitadas em vez de produzir vínculos órfãos.
        invalido(() -> Safra.nova(new ObjectId(), escopo, "Salto", culturaId, List.of(talhaoId),
                // Tenta saltar do planejamento diretamente ao plantio, caso que deve ser rejeitado.
                LocalDate.now(), LocalDate.now(), BigDecimal.ONE).avancar(Safra.Status.PLANTIO));
        // Confere que uma safra finalizada não pode avançar novamente.
        invalido(() -> finalizada.avancar(Safra.Status.FINALIZADA));
        // Exibe o resultado desta etapa no console: DadosCultivoTest: OK.
        System.out.println("DadosCultivoTest: OK");
    }

    // Constrói uma safra fictícia com período e área conhecidos para os casos de teste.
    private static Safra safra(EscopoPropriedade escopo, ObjectId culturaId, List<ObjectId> talhoes,
            // Recebe o intervalo de datas que cada caso de teste quer validar.
            LocalDate inicio, LocalDate fim) {
        // Reconstrói Safra com os identificadores e valores recebidos para este cenário.
        return new Safra(new ObjectId(), escopo, "Soja 2026", culturaId, talhoes, inicio, fim,
                // Completa a safra fictícia com área conhecida, etapa de manejo e histórico inicialmente vazio.
                new BigDecimal("12.75"), Safra.Status.MANEJO, List.of());
    }

    // Verifica a condição recebida e lança AssertionError se a expectativa do teste não for atendida.
    public static void verificar(boolean condicao, String mensagem) {
        // Interrompe o teste com AssertionError quando a expectativa recebida não é verdadeira.
        if (!condicao) throw new AssertionError(mensagem);
    }

    // Exige que a operação rejeite seus dados por validação ou referência inexistente; aceitar a operação faz o teste falhar.
    public static void invalido(Runnable acao) {
        // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
        try {
            // Executa a operação que este caso de teste espera ver rejeitada.
            acao.run();
            // Faz o teste falhar: a operação inválida deveria ter sido rejeitada.
            throw new AssertionError("a operação inválida deveria ter sido rejeitada");
        // Considera esta exceção o resultado esperado para o caso inválido exercitado pelo teste.
        } catch (IllegalArgumentException | RecursoNaoEncontradoException esperado) {
            // validação (400) ou recurso ausente (404) — ambos rejeitam a operação
        }
    }
}
