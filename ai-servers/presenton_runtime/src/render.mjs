import fs from "node:fs/promises";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { pathToFileURL } from "node:url";
import { chromium } from "@playwright/test";
import { templateV2UiToHtmlFragment } from "../dist/template-v2-json-to-html.mjs";

const input = JSON.parse(await fs.readFile(process.argv[2], "utf8"));
const templateRoot = path.resolve(input.templateRoot);
const outputRoot = path.resolve(input.outputRoot);
await fs.mkdir(outputRoot, { recursive: true });

function flatten(value) {
  if (Array.isArray(value)) return value.flatMap(flatten);
  if (value && typeof value === "object") {
    return Object.values(value).flatMap(flatten);
  }
  return [];
}

function hydrate(value, spec, state) {
  if (Array.isArray(value)) return value.map((item) => hydrate(item, spec, state));
  if (!value || typeof value !== "object") return value;
  const node = structuredClone(value);
  const type = String(node.type || "");
  const name = String(node.name || "").trim();
  const slots = spec.layoutContent && typeof spec.layoutContent === "object" ? spec.layoutContent : {};
  if (type === "text" && name) {
    const explicit = Object.entries(slots).find(([key]) => key.toLowerCase() === name.toLowerCase())?.[1];
    const sample = Array.isArray(node.runs) ? node.runs.map((run) => String(run?.text || "")).join("") : "";
    let text = explicit !== undefined ? explicit : "";
    if (Array.isArray(text)) text = text.join("\n");
    if (!String(text).trim()) {
      const lower = name.toLowerCase();
      if (/(headline|heading|title)/.test(lower) && !/(item|card|feature|metric|label)/.test(lower)) text = spec.title;
      else if (/(page|folio|pagination)/.test(lower)) text = String(spec.index).padStart(2, "0");
      else if (/(body|paragraph|description|copy|caption|supporting|detail|summary|item_body|feature_description)/.test(lower)) text = state.next() || sample;
      else if (/(label|item_title|feature_title|metric_title|quote)/.test(lower)) text = state.next() || sample;
      else text = state.next() || sample;
    }
    node.runs = [{ text: String(text || ""), font: node.font }];
    node.text = String(text || "");
  }
  if (type === "chart") {
    const chartData = Object.entries(slots).find(([key]) => key.toLowerCase() === "chart_data")?.[1];
    if (chartData && typeof chartData === "object" && !Array.isArray(chartData)) {
      if (Array.isArray(chartData.categories)) node.categories = chartData.categories;
      if (Array.isArray(chartData.series)) node.series = chartData.series;
      if (Array.isArray(chartData.data)) node.data = chartData.data;
      if (chartData.chartType || chartData.chart_type) node.chartType = chartData.chartType || chartData.chart_type;
    }
  }
  if (type === "table") {
    const tableData = Object.entries(slots).find(([key]) => key.toLowerCase() === "table_data")?.[1];
    if (tableData && typeof tableData === "object" && !Array.isArray(tableData)) {
      if (Array.isArray(tableData.columns)) node.columns = tableData.columns;
      if (Array.isArray(tableData.rows)) node.rows = tableData.rows;
    }
  }
  if (type === "image" && typeof node.data === "string") {
    if (node.data.includes("replaceable_template_image")) {
      const imagePath = String(spec.imagePath || "");
      if (imagePath) node.data = pathToFileURL(imagePath).href;
      else node.data = "";
    } else {
      const raw = node.data.replace(/^\//, "").replace(/^static\//, "").replace(/^images\//, "");
      const candidate = path.resolve(templateRoot, "static", raw.replace(/^images\//, ""));
      if (candidate.startsWith(path.resolve(templateRoot, "static")) && (awaitExists.has(candidate))) node.data = pathToFileURL(candidate).href;
    }
  }
  for (const key of ["elements", "children"]) if (Array.isArray(node[key])) node[key] = node[key].map((item) => hydrate(item, spec, state));
  if (node.child) node.child = hydrate(node.child, spec, state);
  return node;
}

const awaitExists = new Set();
for (const spec of input.slides || []) {
  const fileList = await fs.readdir(path.resolve(templateRoot, "static"), { recursive: true }).catch(() => []);
  for (const rel of fileList) awaitExists.add(path.resolve(templateRoot, "static", rel));
  break;
}

const pages = [];
for (const spec of input.slides || []) {
  const layout = (input.template.layouts || []).find((item) => String(item.id) === String(spec.templateLayoutId || spec.layout)) || input.template.layouts[0];
  const values = [...(spec.content || []), spec.objective].filter(Boolean).map(String);
  let cursor = 0;
  const state = { next: () => values[cursor++] || "" };
  const ui = { components: hydrate(layout.components || [], spec, state), background: "#FFFFFF" };
  const fragment = templateV2UiToHtmlFragment(ui, { width: 1280, height: 720, fonts: input.template.fonts || {} });
  pages.push(`<section class="slide" data-slide-index="${spec.index}" style="width:1280px;height:720px">${fragment || ""}</section>`);
}

const html = `<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;padding:0;background:#fff}.deck{width:1280px}.slide{page-break-after:always;overflow:hidden;position:relative}.slide:last-child{page-break-after:auto}</style></head><body><main class="deck">${pages.join("")}</main></body></html>`;
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
