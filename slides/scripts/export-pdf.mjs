/**
 * Exporta o deck AgroGestão para PDF em alta resolução (2× retina).
 * Uso: node scripts/export-pdf.mjs
 * Requer: npm install playwright pdf-lib && npx playwright install chromium
 */
import { createServer } from "node:http";
import { readFile, writeFile } from "node:fs/promises";
import { join, extname, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";
import { PDFDocument } from "pdf-lib";

const __dirname = dirname(fileURLToPath(import.meta.url));
const SLIDES_ROOT = join(__dirname, "..");
const OUTPUT = join(SLIDES_ROOT, "AgroGestao-Pitch.pdf");

const VIEWPORT = { width: 1920, height: 1080 };
const SCALE = 2;
const ANIM_MS = 1000;

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".png": "image/png",
  ".svg": "image/svg+xml",
  ".woff2": "font/woff2",
};

function startStaticServer(root) {
  return new Promise((resolve) => {
    const server = createServer(async (req, res) => {
      try {
        const url = new URL(req.url || "/", "http://127.0.0.1");
        let filePath = join(root, decodeURIComponent(url.pathname));
        if (filePath.endsWith("/")) filePath += "index.html";
        const data = await readFile(filePath);
        res.writeHead(200, {
          "Content-Type": MIME[extname(filePath).toLowerCase()] || "application/octet-stream",
          "Cache-Control": "no-store",
        });
        res.end(data);
      } catch {
        res.writeHead(404);
        res.end("Not found");
      }
    });
    server.listen(0, "127.0.0.1", () => resolve(server));
  });
}

async function waitForAssets(page) {
  await page.evaluate(() => document.fonts.ready);
  await page.waitForFunction(() =>
    [...document.images].every((img) => img.complete && img.naturalWidth > 0)
  );
}

async function main() {
  const server = await startStaticServer(SLIDES_ROOT);
  const port = server.address().port;
  const url = `http://127.0.0.1:${port}/index.html`;

  console.log("Servindo slides em", url);

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: VIEWPORT,
    deviceScaleFactor: SCALE,
    colorScheme: "light",
  });
  const page = await context.newPage();

  await page.goto(url, { waitUntil: "networkidle", timeout: 120_000 });
  await waitForAssets(page);

  await page.addStyleTag({
    content: "#hint,#slideCounter,#progressOverlay{display:none!important}",
  });

  const slideCount = await page.evaluate(
    () => document.querySelectorAll("#stage .slide").length
  );

  console.log(`Capturando ${slideCount} slides em ${VIEWPORT.width * SCALE}×${VIEWPORT.height * SCALE}px…`);

  const pngBuffers = [];

  for (let i = 0; i < slideCount; i += 1) {
    await page.evaluate((index) => {
      window.location.hash = "#" + (index + 1);
    }, i);
    await page.waitForTimeout(ANIM_MS);
    await waitForAssets(page);

    const shot = await page.screenshot({
      type: "png",
      fullPage: false,
      animations: "disabled",
    });
    pngBuffers.push(shot);
    console.log(`  slide ${String(i + 1).padStart(2, "0")}/${slideCount}`);
  }

  await browser.close();
  server.close();

  const pdfDoc = await PDFDocument.create();
  const pageW = VIEWPORT.width;
  const pageH = VIEWPORT.height;

  for (const png of pngBuffers) {
    const image = await pdfDoc.embedPng(png);
    const pdfPage = pdfDoc.addPage([pageW, pageH]);
    pdfPage.drawImage(image, {
      x: 0,
      y: 0,
      width: pageW,
      height: pageH,
    });
  }

  const pdfBytes = await pdfDoc.save();
  await writeFile(OUTPUT, pdfBytes);

  const sizeMb = (pdfBytes.length / (1024 * 1024)).toFixed(1);
  console.log(`\nPDF gerado: ${OUTPUT} (${sizeMb} MB, ${slideCount} páginas, escala ${SCALE}×)`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
