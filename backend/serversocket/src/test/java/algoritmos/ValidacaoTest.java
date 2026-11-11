package algoritmos;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unitario")
class ValidacaoTest
{
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void textoObrigatorioRecusaAusencia (String valor)
    {
        Validacao v = new Validacao ();
        assertNull (v.texto ("nome", valor, "Obrigatório", 5));
        assertEquals ("Obrigatório", v.getErros ().get ("nome"));
    }

    @Test
    void textoAparaAntesDeConferirLimite ()
    {
        Validacao v = new Validacao ();
        assertEquals ("12345", v.texto ("nome", " 12345 ", "Obrigatório", 5));
        assertFalse (v.temErros ());
        assertNull (v.texto ("nome", "123456", "Obrigatório", 5));
        assertEquals ("Use no máximo 5 caracteres.", v.getErros ().get ("nome"));
    }

    @Test
    void textoOpcionalAusenteViraVazioMasExcessoGeraErro ()
    {
        Validacao v = new Validacao ();
        assertEquals ("", v.textoOpcional ("obs", null, 5));
        assertEquals ("", v.textoOpcional ("obs", "  ", 5));
        assertEquals ("12345", v.textoOpcional ("obs", " 12345 ", 5));
        assertFalse (v.temErros ());
        assertEquals ("", v.textoOpcional ("obs", "123456", 5));
        assertTrue (v.temErros ());
    }

    @Test
    void preservaPrimeiroErroDoCampoEProtegeMapaInterno ()
    {
        Validacao v = new Validacao ();
        v.erro ("nome", "Primeiro");
        v.erro ("nome", "Segundo");
        var copia = v.getErros ();
        copia.clear ();
        assertEquals ("Primeiro", v.getErros ().get ("nome"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"sem-arroba", "a@b", "a@@b.com", "a b@c.com", "a@b. com"})
    void emailRecusaFormatoInvalido (String email)
    {
        Validacao v = new Validacao ();
        assertNull (v.email ("email", email));
        assertTrue (v.getErros ().containsKey ("email"));
    }

    @Test
    void emailNormalizaSemDependerDoIdiomaDaMaquina ()
    {
        Locale anterior = Locale.getDefault ();
        try
        {
            Locale.setDefault (Locale.forLanguageTag ("tr-TR"));
            assertEquals ("iris@example.com", new Validacao ().email ("email", " IRIS@EXAMPLE.COM "));
        }
        finally
        {
            Locale.setDefault (anterior);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Ab1!xyz", "abcdef1!", "Abcdefg!", "Abcdef12"})
    void senhaRecusaAusenciaOuRegraNaoAtendida (String senha)
    {
        Validacao v = new Validacao ();
        assertNull (v.senha ("password", senha, "Obrigatória"));
        assertTrue (v.temErros ());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abcdef1!", "Senha 123", "Ábcdef1!", " Abcde1! "})
    void senhaFortePreservaConteudoInclusiveEspacos (String senha)
    {
        Validacao v = new Validacao ();
        assertEquals (senha, v.senha ("password", senha, "Obrigatória"));
        assertFalse (v.temErros ());
    }

    @ParameterizedTest
    @CsvSource({"128, true", "129, false"})
    void senhaRespeitaTamanhoMaximo (int tamanho, boolean aceita)
    {
        Validacao v = new Validacao ();
        String senha = "Ab1!" + "x".repeat (tamanho - 4);
        assertEquals (aceita ? senha : null, v.senha ("password", senha, "Obrigatória"));
        assertEquals (!aceita, v.temErros ());
    }
}
