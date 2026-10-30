/* Miguel — RA: 25003110 */
// Regras comuns de apresentação e CSV para os relatórios consultados.
window.AgroRelatorio = (() => {
  const escape = value => String(value ?? "").replace(/[&<>"']/g, char => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"})[char]);
  function context() {
    try { const user = JSON.parse(sessionStorage.getItem("agro.sessionUser") || "null");
      return JSON.stringify([sessionStorage.getItem("agro.sessionToken"), user?.id, user?.propriedadeId]);
    } catch { return ""; }
  }
  function csv(rows) {
    return "\uFEFF" + rows.map(row => row.map(value => {
      let text = String(value ?? "");
      if (/^[\s\uFEFF]*[=+\-@]/.test(text) || /^[\t\r\n]/.test(text)) text = "'" + text;
      return '"' + text.replaceAll('"', '""') + '"';
    }).join(";")).join("\r\n");
  }
  function download(rows, name) {
    const url = URL.createObjectURL(new Blob([csv(rows)], {type:"text/csv;charset=utf-8"}));
    const link = document.createElement("a"); link.href=url; link.download=name; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  return {escape, context, csv, download};
})();
