/* Miguel — RA: 25003110 */
/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

/* Persistência operacional compartilhada. Java continua no servidor; somente
 * JavaScript no navegador consegue acessar o localStorage deste dispositivo.
 * Guardamos dados, nunca HTML, senhas ou tokens. As telas recriam sua interface
 * pelos construtores existentes e calculam novamente os indicadores. */
(() => {
  "use strict";
  const defaults = window.AgroOperationalDefaults;
  if (!defaults) throw new Error("Carregue scripts/data/operational-defaults.js antes de agro-local.js");
  const clone = (value) => JSON.parse(JSON.stringify(value));
  const text = (value) => typeof value === "string" && value.trim().length > 0;
  const string = (value) => typeof value === "string";
  const number = (value) => typeof value === "number" && Number.isFinite(value) && value >= 0;
  const positive = (value) => number(value) && value > 0;
  // Não aceitamos datas impossíveis ou movimentações sem instante válido.
  const isoDay = (value) => string(value) && /^\d{4}-\d{2}-\d{2}$/.test(value)
    && Number.isFinite(Date.parse(value + "T12:00:00Z"))
    && new Date(value + "T12:00:00Z").toISOString().slice(0, 10) === value;
  const isoInstant = (value) => string(value) && Number.isFinite(Date.parse(value))
    && new Date(value).toISOString() === value;
  const oneOf = (...values) => (value) => values.includes(value);
  const shape = (fields) => (value) => value && !Array.isArray(value) && typeof value === "object"
    && Object.keys(value).length === Object.keys(fields).length
    && Object.entries(fields).every(([key, valid]) => Object.hasOwn(value, key) && valid(value[key]));
  const list = (valid) => (value) => Array.isArray(value) && value.every(valid);
  const records = (fields) => (value) => list(shape({ id: text, ...fields }))(value)
    && new Set(value.map((item) => item.id)).size === value.length;
  const timeline = list(shape({time: text, title: text, desc: string}));
  const schemas = {
    plots: records({plot: text, crop: text, hectares: positive, status: oneOf("Ativo", "Irrigação pendente", "Em preparo")}),
    // Aceita o cache anterior sem os campos de planejamento e o formato atual,
    // para que uma atualização não descarte atividades já gravadas no navegador.
    activities: (value) => records({activity: text, tipo: text, talhao: text, date: text,
      status: oneOf("Pendente", "Agendada", "Em andamento", "Concluída", "Cancelada")})(value)
      || records({activity: text, tipo: text, talhao: text, date: text, horario: string,
        responsavel: string, prioridade: string,
        status: oneOf("Pendente", "Agendada", "Em andamento", "Concluída", "Cancelada")})(value)
      || records({activity: text, tipo: text, talhao: text, date: text, horario: string,
        responsavel: string, prioridade: string, safraId: string, safra: string, cultura: string,
        status: oneOf("Pendente", "Agendada", "Em andamento", "Concluída", "Cancelada")})(value),
    stock: records({product: text, category: text, qty: number, unit: text, min: positive,
      movements: list(shape({id: text, type: oneOf("entrada", "saida"), qty: positive, date: isoInstant, note: string}))}),
    occurrences: records({occ: text, tipo: text, talhao: text, date: text,
      severity: oneOf("Alta", "Média", "Baixa"), status: oneOf("Aberta", "Em análise", "Resolvida"), timeline}),
    costs: shape({budget: positive, period: oneOf("Jan — Ago 2026", "Safra 2025/26"),
      entries: (value) => list(shape({id: positive, description: text,
        category: oneOf("Insumos", "Mão de Obra", "Maquinário", "Combustível", "Outros"),
        plot: string, date: isoDay, value: positive}))(value)
        && new Set(value.map((entry) => entry.id)).size === value.length}),
    notifications: list(text),
    settings: shape({profile: shape({name: text, email: text, phone: string, role: string}),
      // Aceita o registro anterior sem localização apenas para leitura; no próximo
      // salvamento a tela exige o novo campo e grava o formato completo.
      property: (value) => shape({farm: text, area: positive, location: text, city: text, harvest: oneOf("2025/26", "2026/27", "2024/25")})(value)
        || shape({farm: text, area: positive, city: text, harvest: oneOf("2025/26", "2026/27", "2024/25")})(value),
      preferences: shape(Object.fromEntries(["stock", "weather", "activities", "occurrences", "reports"].map((key) => [key, (value) => typeof value === "boolean"])))})
  };
  const blocked = new Set();
  const known = new Map();
  function sessionScope() {
    const user = JSON.parse(sessionStorage.getItem("agro.sessionUser") || "null");
    if (!text(user?.id) || !text(user?.propriedadeId)) throw new Error("Sessão sem propriedade");
    return encodeURIComponent(user.id) + "." + encodeURIComponent(user.propriedadeId) + ".";
  }
  let scope;
  try { scope = sessionScope(); } catch { scope = null; }
  function storageKey(key) {
    // Uma aba aberta no contexto antigo nunca grava na propriedade recém-selecionada.
    if (!scope || sessionScope() !== scope) throw new Error("Recarregue a sessão");
    return "agro.operational.v1." + scope + key;
  }
  const labels = {plots: "os talhões", activities: "as atividades", stock: "o estoque",
    occurrences: "as ocorrências", costs: "os custos", settings: "as configurações", notifications: "as notificações"};

  // Um aviso visível permanece na página: não depende da duração do toast.
  function report(message) {
    const show = () => {
      let banner = document.querySelector("[data-storage-error]");
      if (!banner) {
        banner = document.createElement("p");
        banner.dataset.storageError = "";
        banner.setAttribute("role", "alert");
        banner.style.cssText = "padding:12px;background:#fff1d6;color:#702d00;position:relative;z-index:10";
        (document.querySelector("main") || document.body).prepend(banner);
      }
      banner.textContent = message;
      window.AgroToast?.show({type: "error", title: "Dados locais não salvos", description: message});
    };
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", show, {once: true});
    else show();
  }

  function read(key, fallback = defaults[key], empty = []) {
    try {
      const raw = window.localStorage.getItem(storageKey(key));
      const envelope = raw === null ? null : JSON.parse(raw);
      if (envelope && (envelope.version !== 1 || !schemas[key]?.(envelope.data))) throw new Error("invalid");
      if (raw !== null && !envelope) throw new Error("invalid");
      known.set(key, raw);
      return clone(envelope ? envelope.data : fallback);
    } catch (error) {
      blocked.add(key);
      report("Não foi possível recuperar " + labels[key] + ". O armazenamento está inválido ou indisponível. Nenhuma recuperação foi confirmada; os dados existentes não serão sobrescritos. Corrija o armazenamento e recarregue a página.");
      return clone(empty);
    }
  }

  function save(key, data) {
    try {
      if (blocked.has(key) || !schemas[key]?.(data)) throw new Error("invalid");
      // Impede que uma aba desatualizada apague silenciosamente alterações de outra.
      const current = window.localStorage.getItem(storageKey(key));
      if (known.has(key) && current !== known.get(key)) throw new Error("conflict");
      const raw = JSON.stringify({version: 1, data});
      window.localStorage.setItem(storageKey(key), raw);
      known.set(key, raw);
      return true;
    } catch (error) {
      report("Não foi possível salvar " + labels[key] + ". A alteração foi desfeita. Verifique o espaço ou a permissão do navegador e recarregue a página antes de tentar novamente.");
      return false;
    }
  }

  // Cada tela informa como extrair os registros e como remontar sua interface.
  // Se a gravação falhar, voltamos ao último estado confirmado antes do toast.
  function connect(key, capture, restore, fallback = defaults[key], empty = []) {
    let confirmed = read(key, fallback, empty);
    restore(clone(confirmed));
    return () => {
      const data = capture();
      if (!save(key, data)) { restore(clone(confirmed)); return false; }
      confirmed = clone(data);
      return true;
    };
  }
  const id = () => {
    if (window.crypto?.randomUUID) return window.crypto.randomUUID();
    const bytes = new Uint8Array(16);
    window.crypto.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
    return hex.slice(0, 8) + "-" + hex.slice(8, 12) + "-" + hex.slice(12, 16) + "-" + hex.slice(16, 20) + "-" + hex.slice(20);
  };
  const escape = (value) => String(value).replace(/[&<>"']/g, (char) => ({"&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;"}[char]));
  window.AgroLocal = {read, save, connect, id, escape, initial: (key) => clone(defaults[key])};
})();
