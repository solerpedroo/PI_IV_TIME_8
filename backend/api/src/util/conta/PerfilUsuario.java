/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package util.conta;

import org.bson.Document;

/*
    Monta o que a API pode devolver sobre um usuario.

    O documento no Mongo tem hash de senha, codigos de verificacao e outros
    campos internos. Nenhum metodo desta classe espalha o documento inteiro:
    cada resposta e uma projecao explicita. Se um campo novo e secreto nascer
    no banco, ele nao vaza so porque alguem esqueceu de lista-lo num "ocultar".
*/
public class PerfilUsuario
{
    /*
        Widgets do painel que o usuario pode ligar ou desligar.

        A lista e fechada. Chave desconhecida gravada no banco e ignorada,
        para um cliente nao inventar preferencia que o front nao desenha.
    */
    public static final java.util.List<String> PREFERENCIAS = java.util.List.of ("stock", "weather", "activities", "occurrences", "reports");

    /*
        Devolve as cinco preferencias sempre, mesmo se o usuario nunca salvou nenhuma.

        Sem documento "preferencias", o padrao e tudo ligado, exceto "reports":
        relatorios comecam desligados ate a pessoa optar. Com documento salvo,
        so Boolean.TRUE conta como ligado. Valor ausente, false ou outro tipo
        vira false, para lixo no banco nao acender o widget.
    */
    public static Document preferencias (Document usuario)
    {
        Document salvas = usuario.get ("preferencias", Document.class);
        Document ret = new Document ();
        /*
            append aqui monta a resposta de GET /api/perfil (e o trecho de preferências
            do login). Não escreve no Mongo. O objeto fica assim, sempre com as cinco chaves:

            {
              "stock": true,
              "weather": true,
              "activities": true,
              "occurrences": true,
              "reports": false     // único que nasce desligado
            }

            O documento do usuário no banco, coleção usuarios, guarda isso aninhado
            em usuario.preferencias. Se esse subdocumento não existir, usamos o padrão.
        */
        for (String chave : PREFERENCIAS)
            ret.append (chave, salvas == null ? !chave.equals ("reports") : Boolean.TRUE.equals (salvas.get (chave)));
        return ret;
    }

    /*
        Diz se o tour inicial ja foi concluido.

        Conta antiga, criada antes deste campo existir, nao deve ser forcada
        a refazer o onboarding: usuario null ou campo ausente devolve true.
        O valor pode ter sido gravado como boolean ou como texto "true"
        (formularios as vezes mandam string). Qualquer outro tipo tambem
        conta como concluido: e um passeio de interface, nao uma trava de seguranca.
    */
    public static boolean onboardingConcluido (Document usuario)
    {
        if (usuario == null)
            return true;

        if (!usuario.containsKey ("onboardingConcluido"))
            return true;

        Object valor = usuario.get ("onboardingConcluido");
        if (valor instanceof Boolean)
            return Boolean.TRUE.equals (valor);

        if (valor instanceof String)
            return "true".equalsIgnoreCase (((String) valor).trim ());

        return true;
    }

    /*
        Projecao publica do usuario para login, /me e respostas de conta.

        So entram dados que a tela de perfil mostra. propriedadePadraoId
        repete propriedadeId porque o front ainda le o nome antigo; os dois
        apontam para a mesma propriedade padrao. emailPendente fica visivel
        para a UI avisar que a troca de e-mail ainda espera o codigo.
    */
    public static Document publico (Document usuario)
    {
        /*
            Cada append acrescenta um campo neste Document de RESPOSTA HTTP.
            Ele não volta para a coleção usuarios. senhaHash, tokenConfirmacao
            e tokenRedefinicao ficam de fora de propósito: não estão na lista.

            JSON que o front recebe em login e em /api/me:

            {
              "id": "24 caracteres hex do _id",
              "name": "Maria",
              "email": "maria@agro.com",
              "phone": "(19) 99999-0000",
              "emailConfirmado": false,
              "emailPendente": null ou o e-mail novo ainda não confirmado,
              "propriedadeId": "hex da fazenda ativa",
              "propriedadePadraoId": o mesmo hex (nome antigo que a tela ainda lê),
              "onboardingConcluido": false
            }
        */
        return new Document ("id", usuario.getObjectId ("_id").toHexString ())
            .append ("name", usuario.getString ("name")).append ("email", usuario.getString ("email"))
            .append ("phone", usuario.getString ("phone")).append ("emailConfirmado", usuario.getBoolean ("emailConfirmado"))
            .append ("emailPendente", usuario.getString ("emailPendente"))
            .append ("propriedadeId", usuario.getString ("propriedadeId"))
            .append ("propriedadePadraoId", usuario.getString ("propriedadeId"))
            .append ("onboardingConcluido", PerfilUsuario.onboardingConcluido (usuario));
    }
}
