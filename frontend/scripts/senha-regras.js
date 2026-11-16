/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */
/**
 * Uma só cópia das regras que o medidor mostra.
 * O aceite continua em algoritmos.Validacao.senha: 8 a 128, maiúscula, número e símbolo.
 */
(() => {
  "use strict";

  /* Textos da barra, do mais fraco ao mais forte, conforme quantas regras foram cumpridas. */
  const ROTULOS = ["—", "Fraca", "Fraca", "Média", "Boa", "Forte"];

  /* Confere no que foi digitado: tamanho mínimo, maiúscula, número, símbolo
     e limite de 128 caracteres. Cada item acende ou apaga uma linha do medidor. */
  /*
    Objeto que a barra lê. Não vai para a API. Cada chave liga um item [data-rule]
    no HTML do medidor. nivel() conta quantas estão true (0 a 5) e ROTULOS
    traduz isso em "Fraca", "Média", "Boa" ou "Forte".

    {
      length: pelo menos 8 caracteres,
      upper: tem maiúscula,
      number: tem dígito,
      symbol: tem algo que não é letra nem número,
      max: não passou de 128
    }

    O servidor repete a mesma regra no cadastro. Esta função só orienta a tela.
  */
  function regras(value) {
    const texto = String(value ?? "");
    return {
      length: texto.length >= 8,
      upper: /[A-ZÀ-Ý]/.test(texto),
      number: /\d/.test(texto),
      symbol: /[^A-Za-zÀ-ÿ\d]/.test(texto),
      max: texto.length <= 128,
    };
  }

  /* Conta quantas regras já foram atendidas para escolher a faixa da barra. */
  function nivel(atuais) {
    return Object.values(atuais).filter(Boolean).length;
  }

  /* Pinta a barra de força, troca o rótulo e marca as regras cumpridas
     ao lado do campo de senha. */
  function atualizar(input) {
    const meter = input.closest("form")?.querySelector("[data-meter]") || document.querySelector("[data-meter]");
    if (!meter) return;
    const atuais = regras(input.value);
    const forca = nivel(atuais);
    meter.dataset.level = String(forca);
    const rotulo = meter.querySelector("[data-meter-label]");
    if (rotulo) {
      rotulo.dataset.level = String(forca);
      rotulo.textContent = ROTULOS[forca] || ROTULOS.at(-1);
    }
    meter.querySelectorAll("[data-rule]").forEach((regra) => {
      regra.classList.toggle("is-valid", Boolean(atuais[regra.dataset.rule]));
    });
  }

  /* Liga o medidor em cada campo marcado na página e atualiza a barra a cada tecla. */
  function iniciar() {
    document.querySelectorAll("[data-password-meter]").forEach((input) => {
      if (input.dataset.senhaMedidor === "true") return;
      input.dataset.senhaMedidor = "true";
      const update = () => atualizar(input);
      input.addEventListener("input", update);
      update();
    });
  }

  window.AgroSenha = { regras, nivel, rotulos: ROTULOS, iniciar };
})();
