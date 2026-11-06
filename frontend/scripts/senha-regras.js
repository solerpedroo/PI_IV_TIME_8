/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */
/**
 * Uma só cópia das regras que o medidor mostra.
 * O aceite continua em algoritmos.Validacao.senha: 8 a 128, maiúscula, número e símbolo.
 */
(() => {
  "use strict";

  const ROTULOS = ["—", "Fraca", "Fraca", "Média", "Boa", "Forte"];

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

  function nivel(atuais) {
    return Object.values(atuais).filter(Boolean).length;
  }

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
