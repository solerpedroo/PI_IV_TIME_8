/* Miguel — RA: 25003110 */
/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */
// Uma propriedade nova começa vazia. Dados operacionais vêm da API.
window.AgroOperationalDefaults = {
  plots: [], activities: [], stock: [], occurrences: [], notifications: [],
  costs: { budget: 0, period: "todo", entries: [] },
  settings: {
    profile: { name: "", email: "", phone: "", role: "" },
    property: { farm: "", area: 0, city: "", harvest: "" },
    preferences: { stock: true, weather: true, activities: true, occurrences: true, reports: false }
  }
};
