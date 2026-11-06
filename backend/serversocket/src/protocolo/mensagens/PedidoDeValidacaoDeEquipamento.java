/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package protocolo.mensagens;

import protocolo.Pedido;

/*
    Valida o cadastro de um equipamento agricola (nome, tipo, marca, modelo, identificacao, ano, horimetro, status).
    anoAtual vem da API: o servidor de algoritmos nao le relogio (funcao pura).
*/
public class PedidoDeValidacaoDeEquipamento extends Pedido
{
    private static final long serialVersionUID = 1L;

    private final String  nome;
    private final String  tipo;
    private final String  marca;
    private final String  modelo;
    private final String  identificacao;
    private final Integer ano;
    private final Double  horimetro;
    private final String  status;
    private final Integer anoAtual;

    public PedidoDeValidacaoDeEquipamento (String nome, String tipo, String marca, String modelo, String identificacao, Integer ano, Double horimetro, String status, Integer anoAtual)
    {
        this.nome          = nome;
        this.tipo          = tipo;
        this.marca         = marca;
        this.modelo        = modelo;
        this.identificacao = identificacao;
        this.ano           = ano;
        this.horimetro     = horimetro;
        this.status        = status;
        this.anoAtual      = anoAtual;
    }

    public String  getNome () { return this.nome; }
    public String  getTipo () { return this.tipo; }
    public String  getMarca () { return this.marca; }
    public String  getModelo () { return this.modelo; }
    public String  getIdentificacao () { return this.identificacao; }
    public Integer getAno () { return this.ano; }
    public Double  getHorimetro () { return this.horimetro; }
    public String  getStatus () { return this.status; }
    public Integer getAnoAtual () { return this.anoAtual; }
}
