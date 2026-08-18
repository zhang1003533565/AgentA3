import fs from "node:fs/promises";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { pathToFileURL } from "node:url";
import { chromium } from "@playwright/test";
import { templateV2UiToHtml } from "../dist/template-v2-json-to-html.mjs";

const input = JSON.parse(await fs.readFile(process.argv[2], "utf8"));
const templateRoot = path.resolve(input.templateRoot);
const outputRoot = path.resolve(input.outputRoot);
await fs.mkdir(outputRoot, { recursive: true });

// The model returns a complete Presenton UI JSON tree. This function only
// resolves local asset URLs; it never injects text, chooses layouts, or rebuilds
// component children. That work belongs to Presenton's original renderer.
function resolveUiAssets(value, spec) {
  if (Array.isArray(value)) return value.map((item) => resolveUiAssets(item, spec));
  if (!value || typeof value !== "object") return value;
  const node = structuredClone(value);
  if (String(node.type || "") === "image" && typeof node.data === "string") {
    if (node.data.includes("replaceable_template_image")) {
      const imagePath = String(spec.imagePath || "");
      node.data = imagePath ? pathToFileURL(imagePath).href : "";
    } else {
      const raw = node.data.replace(/^\//, "").replace(/^static\//, "").replace(/^images\//, "");
      const candidate = path.resolve(templateRoot, "static", raw);
      if (candidate.startsWith(path.resolve(templateRoot, "static")) && awaitExists.has(candidate)) {
        node.data = pathToFileURL(candidate).href;
      } else {
        node.data = "";
      }
    }
  }
  for (const key of ["elements", "components", "children"]) {
    if (Array.isArray(node[key])) node[key] = node[key].map((item) => resolveUiAssets(item, spec));
  }
  if (node.child) node.child = resolveUiAssets(node.child, spec);
  return node;
}

function resolveFontAssets(value) {
  if (Array.isArray(value)) return value.map(resolveFontAssets);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, raw]) => [key, resolveFontAssets(raw)]));
  }
  if (typeof value !== "string") return value;
  const raw = value.replace(/^\//, "").replace(/^static\//, "");
  const candidate = path.resolve(templateRoot, "static", raw);
  if (candidate.startsWith(path.resolve(templateRoot, "static")) && awaitExists.has(candidate)) {
    return pathToFileURL(candidate).href;
  }
  return value;
}

const awaitExists = new Set();
for (const spec of input.slides || []) {
  const fileList = await fs.readdir(path.resolve(templateRoot, "static"), { recursive: true }).catch(() => []);
  for (const rel of fileList) awaitExists.add(path.resolve(templateRoot, "static", rel));
  break;
}

const pages = [];
let originalHead = "";
for (const spec of input.slides || []) {
  const ui = resolveUiAssets(spec.ui, spec);
  if (!ui || (!Array.isArray(ui.components) && !Array.isArray(ui.elements))) {
    throw new Error(`Presenton UI JSON missing for slide ${spec.index}`);
  }
  // Use Presenton's complete document renderer.  We only extract its head and
  // body so several independently rendered pages can share one export HTML
  // document; the component tree, CSS, font tags, SVG handling and chart
  // scripts remain the original Presenton output.
  const originalHtml = templateV2UiToHtml(ui, {
    width: 1280,
    height: 720,
    fonts: resolveFontAssets(input.template.fonts || {}),
  });
  if (!originalHtml) throw new Error(`Presenton UI rendered empty for slide ${spec.index}`);
  const headMatch = originalHtml.match(/<head>([\s\S]*?)<\/head>/i);
  const bodyMatch = originalHtml.match(/<body>([\s\S]*?)<\/body>/i);
  if (!bodyMatch) throw new Error(`Presenton HTML body missing for slide ${spec.index}`);
  if (!originalHead && headMatch) originalHead = headMatch[1];
  pages.push(`<section class="slide" data-slide-index="${spec.index}" style="width:1280px;height:720px">${bodyMatch[1]}</section>`);
}

const html = `<!doctype html><html><head><meta charset="utf-8">${originalHead}<style>html,body{margin:0;padding:0;background:#fff}.deck{width:1280px}.slide{page-break-after:always;overflow:hidden;position:relative}.slide:last-child{page-break-after:auto}</style></head><body><main class="deck">${pages.join("")}</main></body></html>`;
const htmlPath = path.join(outputRoot, `${input.taskId}.html`);
await fs.writeFile(htmlPath, html, "utf8");

const browser = await chromium.launch({
  headless: true,
  executablePath: process.env.PUPPETEER_EXECUTABLE_PATH || process.env.CHROMIUM_PATH || undefined,
});
const page = await browser.newPage({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1 });
await page.goto(pathToFileURL(htmlPath).href, { waitUntil: "load" });
await page.waitForTimeout(700);
const pdfPath = path.join(outputRoot, `${input.taskId}.pdf`);
await page.pdf({ path: pdfPath, width: "1280px", height: "720px", printBackground: true, margin: { top: 0, right: 0, bottom: 0, left: 0 } });
const slideCount = pages.length;
for (let index = 1; index <= slideCount; index += 1) {
  const target = path.join(outputRoot, `${input.taskId}-${index}.png`);
  await page.locator(`[data-slide-index="${index}"]`).screenshot({ path: target });
}
await browser.close();

let pptxPath = null;
const exportRoot = process.env.PRESENTON_EXPORT_ROOT || path.resolve("../presenton_runtime/presentation-export");
const exportEntrypoint = path.join(exportRoot, "index.js");
const exportConverter = process.env.BUILT_PYTHON_MODULE_PATH || path.join(exportRoot, "py", "convert-linux-current");
if (process.env.PRESENTON_ENABLE_PPTX === "true" && await fs.stat(exportEntrypoint).then(() => true).catch(() => false)) {
  const exportTask = path.join(outputRoot, `${input.taskId}.export.json`);
  const exportResponse = path.join(outputRoot, `${input.taskId}.export.response.json`);
  await fs.writeFile(exportTask, JSON.stringify({
    type: "export",
    url: pathToFileURL(htmlPath).href,
    format: "pptx",
    title: input.title || "presentation",
  }), "utf8");
  const child = spawnSync(process.execPath, [exportEntrypoint, exportTask], {
    cwd: exportRoot,
    env: { ...process.env, BUILT_PYTHON_MODULE_PATH: exportConverter },
    encoding: "utf8",
    timeout: Number(process.env.PRESENTON_EXPORT_TIMEOUT_MS || 300000),
  });
  if (child.status !== 0) throw new Error(`Presenton PPTX export failed: ${child.stderr || child.stdout || child.status}`);
  const response = JSON.parse(await fs.readFile(exportResponse, "utf8"));
  pptxPath = response.path || null;
}
console.log(JSON.stringify({ htmlPath, pdfPath, slideCount, pptxPath }));
