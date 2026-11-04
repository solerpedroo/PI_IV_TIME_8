/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */
/**
 * E2E: formulários de auth chamam a rota correta (login não cai em forgot-password).
 */
const assert = require("node:assert/strict");
const path = require("node:path");
const { spawn } = require("node:child_process");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const { launchTestBrowser } = require("./browser-launch.cjs");

const base = "http://127.0.0.1:8080";
const cpSeparator = process.platform === "win32" ? ";" : ":";
const classpath = `bin${cpSeparator}lib/*`;
const emptySmtp = {
  SMTP_HOST: "",
  SMTP_USERNAME: "",
  SMTP_PASSWORD: "",
  SMTP_FROM: "",
  SMTP_PORT: "",
};

function waitAuthPost(page, pathname) {
  return page.waitForRequest(
    (req) => req.method() === "POST" && req.url().replace(base, "").startsWith(pathname),
    { timeout: 15000 },
  );
}

(async () => {
  try {
    await fetch(base + "/health");
    console.error("A porta 8080 já está em uso.");
    process.exit(1);
  } catch {
    // livre
  }

  const algo = spawn("java", ["-cp", "bin", "Servidor", "3000"], {
    cwd: path.join(__dirname, "../backend/serversocket"),
  });
  const server = spawn("java", ["-cp", classpath, "Servidor", "8080", "--memoria"], {
    cwd: path.join(__dirname, "../backend/api"),
    env: {
      ...process.env,
      ...emptySmtp,
      SERVIDOR_ALGORITMOS_HOST: "127.0.0.1",
      SERVIDOR_ALGORITMOS_PORT: "3000",
    },
  });
  let log = "";
  const appendLog = (chunk) => { log += chunk.toString(); };
  algo.stdout.on("data", appendLog);
  algo.stderr.on("data", appendLog);
  server.stdout.on("data", appendLog);
  server.stderr.on("data", appendLog);

  let browser;
  try {
    for (let i = 0; ; i++) {
      try {
        if ((await fetch(base + "/health")).ok) break;
      } catch {}
      if (i === 60 || server.exitCode !== null) throw new Error("Servidor não iniciou:\n" + log);
      await new Promise((r) => setTimeout(r, 100));
    }

    const api = async (endpoint, body) => {
      const res = await fetch(base + endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      return { status: res.status, data: await res.json().catch(() => ({})) };
    };

    const email = "login-form-e2e@agro.test";
    const password = "Senha@123";
    assert.equal(
      (await api("/api/auth/register", {
        name: "Login E2E",
        email,
        phone: "(19) 98888-3333",
        password,
        confirm: password,
        farm: "Fazenda Login",
        area: "10 ha",
        terms: true,
      })).status,
      201,
    );
    for (let i = 0; i < 50 && !/token: ([\w-]+)/.test(log); i++) await new Promise((r) => setTimeout(r, 100));
    const token = log.match(/token: ([\w-]+)/)?.[1];
    assert.ok(token, "token de confirmação no console");
    assert.equal((await api("/api/auth/confirm-email", { token })).status, 200);

    browser = await launchTestBrowser(chromium);
    const page = await browser.newPage();

    const loginReq = waitAuthPost(page, "/api/auth/login");
    await page.goto(base + "/login.html");
    await page.locator("#email").fill(email);
    await page.locator("#password").fill(password);
    await page.locator('form[data-auth-route="/api/auth/login"] [type="submit"]').click();
    const loginCall = await loginReq;
    assert.equal(new URL(loginCall.url()).pathname, "/api/auth/login");
    await page.waitForURL("**/dashboard.html", { timeout: 15000 });
    assert.ok(await page.evaluate(() => Boolean(sessionStorage.getItem("agro.sessionToken"))));

    const forgotReq = waitAuthPost(page, "/api/auth/forgot-password");
    await page.goto(base + "/recuperar-senha.html");
    await page.locator("#email").fill(email);
    await page.locator('form[data-auth-route="/api/auth/forgot-password"] [type="submit"]').click();
    const forgotCall = await forgotReq;
    assert.equal(new URL(forgotCall.url()).pathname, "/api/auth/forgot-password");
    await page.waitForURL("**/redefinir-senha.html", { timeout: 15000 });

    console.log("OK login.html → POST /api/auth/login; recuperar-senha → POST /api/auth/forgot-password");
  } finally {
    if (browser) await browser.close();
    server.kill("SIGTERM");
    algo.kill("SIGTERM");
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
