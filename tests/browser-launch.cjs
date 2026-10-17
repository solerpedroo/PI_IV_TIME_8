/* Miguel — RA: 25003110 */
/** Playwright Chromium por padrão; BROWSER_PATH opcional (ex.: Edge no Windows). */
async function launchTestBrowser(chromium) {
  const options = { headless: true };
  if (process.env.BROWSER_PATH) options.executablePath = process.env.BROWSER_PATH;
  return chromium.launch(options);
}

module.exports = { launchTestBrowser };
