import fs from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import { spawnSync } from "node:child_process";
import { pathToFileURL } from "node:url";
import { chromium } from "@playwright/test";
import { templateV2UiToHtml } from "../dist/template-v2-json-to-html.mjs";

// 顶层错误出口：把失败原因以 JSON 打到 stderr 并以非零码退出。
// 之前未捕获异常只有裸栈，Python 层又吞掉 stderr，导致排障只能看到
// "returned non-zero exit status 1"。
const main = async () => {

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
  pages.push(`<div class="slide" data-slide-index="${spec.index}" style="width:1280px;height:720px">${bodyMatch[1]}</div>`);
}

// Keep the wrapper shape expected by Presenton's official PPTX exporter:
// #presentation-slides-wrapper > div > div > div > div > .slide.
const html = `<!doctype html><html><head><meta charset="utf-8">${originalHead}<style>html,body{margin:0;padding:0;background:#fff}#presentation-slides-wrapper{width:1280px}.slide{page-break-after:always;overflow:hidden;position:relative}.slide:last-child{page-break-after:auto}</style></head><body><main id="presentation-slides-wrapper"><div><div><div><div>${pages.join("")}</div></div></div></div></main></body></html>`;
const htmlPath = path.join(outputRoot, `${input.taskId}.html`);
await fs.writeFile(htmlPath, html, "utf8");

// Chromium 启动：--disable-gpu 避免多实例并发时 NVIDIA DXCache/GPU 上下文
// 冲突导致 Chrome 崩溃（PPT 任务线程池有 2 并发，同时起两个 Chrome）；
// 指定路径启动失败时降级用 Playwright 默认 Chromium 再试一次。
const userDataDir = await fs.mkdtemp(path.join(os.tmpdir(), "presenton-chromium-"));
const LAUNCH_ARGS = [
  "--disable-gpu",
  "--disable-dev-shm-usage",
  "--no-sandbox",
  "--no-first-run",
  "--no-default-browser-check",
];
const launchBrowser = async () => {
  const explicit = process.env.PUPPETEER_EXECUTABLE_PATH || process.env.CHROMIUM_PATH || undefined;
  if (explicit) {
    try {
      return await chromium.launchPersistentContext(userDataDir, {
        headless: true,
        executablePath: explicit,
        viewport: { width: 1280, height: 720 },
        deviceScaleFactor: 1,
        args: LAUNCH_ARGS,
      });
    } catch (exc) {
      console.error(JSON.stringify({ level: "warn", message: `explicit chromium launch failed (${exc.message}); retrying with bundled chromium` }));
    }
  }
  return chromium.launchPersistentContext(userDataDir, {
    headless: true,
    viewport: { width: 1280, height: 720 },
    deviceScaleFactor: 1,
    args: LAUNCH_ARGS,
  });
};
let browser;
try {
  browser = await launchBrowser();
} catch (exc) {
  await fs.rm(userDataDir, { recursive: true, force: true }).catch(() => {});
  throw exc;
}
let slideCount = 0;
try {
const page = await browser.newPage();
await page.goto(pathToFileURL(htmlPath).href, { waitUntil: "load" });
await page.waitForTimeout(700);
// pngOnly is used by preview rendering. The product only exposes editable PPTX
// files, so this runtime only creates the artifacts required by this flow.
const pngOnly = input.pngOnly === true;
const pptxOnly = input.pptxOnly === true;
slideCount = pages.length;
for (let index = 1; index <= slideCount; index += 1) {
  const target = path.join(outputRoot, `${input.taskId}-${index}.png`);
  try {
    await page.locator(`[data-slide-index="${index}"]`).screenshot({ path: target, timeout: 60_000 });
  } catch (exc) {
    throw new Error(`slide ${index} screenshot failed: ${exc.message}`);
  }
}
} finally {
  // 渲染中途抛错也必须关掉 Chrome，否则进程泄漏累积后拖垮后续任务
  await browser.close().catch(() => {});
  await fs.rm(userDataDir, { recursive: true, force: true }).catch(() => {});
}

let pptxPath = null;
const exportRoot = process.env.PRESENTON_EXPORT_ROOT || path.resolve("../presenton_runtime/presentation-export");
// The runtime package is ESM, but the export bundle exposes a CommonJS
// entrypoint. Prefer the generated .cjs shim on Windows/Node ESM projects.
const exportEntrypoint = await fs.stat(path.join(exportRoot, "index.cjs")).then(() => path.join(exportRoot, "index.cjs")).catch(() => path.join(exportRoot, "index.js"));
const exportConverter = process.env.BUILT_PYTHON_MODULE_PATH || path.join(exportRoot, "py", "convert-linux-current");
if (!input.pngOnly && process.env.PRESENTON_ENABLE_PPTX === "true" && await fs.stat(exportEntrypoint).then(() => true).catch(() => false)) {
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
  // presenton-export v0.4.x returns a file:// URL, while older builds used
  // `path`. Keep both forms so the host-side adapter can map the artifact back
  // from the container runtime mount.
  pptxPath = response.path || response.url || null;
}
console.log(JSON.stringify({ htmlPath, slideCount, pptxPath }));
};

try {
  await main();
} catch (exc) {
  // 结构化错误到 stderr：Python 层会把它拼进异常信息，排障不再盲猜
  console.error(JSON.stringify({ level: "error", stage: "render", message: exc.message, stack: String(exc.stack || "").split("\n").slice(0, 6) }));
  process.exit(1);
}
