/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */
/**
 * Polyfill mínimo de AbortSignal.timeout para ambientes sem suporte (ex.: Safari antes da versão 16.4).
 * Deve ser carregado antes de qualquer script que use fetch com timeout.
 */
(() => {
  "use strict";
  if (typeof AbortSignal === "undefined" || typeof AbortSignal.timeout === "function") return;
  /** @param {number} ms */
  AbortSignal.timeout = function timeout(ms) {
    const controller = new AbortController();
    const id = setTimeout(() => controller.abort(), ms);
    // Evita timers órfãos se o sinal for combinado ou descartado de outra forma.
    if (typeof controller.signal.addEventListener === "function") {
      controller.signal.addEventListener("abort", () => clearTimeout(id), { once: true });
    }
    return controller.signal;
  };
})();
