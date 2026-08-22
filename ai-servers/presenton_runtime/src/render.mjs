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

// The browser can render a Poppins/CJK fallback stack, but the PPTX exporter
// serializes the requested family as the Office run font. If the requested
// Latin family has no CJK glyphs, PowerPoint may choose a serif fallback and
// the editable export no longer matches the verified preview. Keep this
// normalization limited to native PPTX layers; fidelity screenshots and the
// preview HTML must retain the original template styling.
const PPTX_CJK_FONT = String(input.pptxCjkFont || process.env.PPTX_CJK_FONT || "Microsoft YaHei").trim();
const CJK_TEXT_RE = /[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]/;

function _setPptxCjkFont(node) {
  if (!node || typeof node !== "object") return;
  if (node.font && typeof node.font === "object") {
    if (typeof node.font.family === "string") node.font.family = PPTX_CJK_FONT;
    if (typeof node.font.fontFamily === "string") node.font.fontFamily = PPTX_CJK_FONT;
  }
  if (typeof node.fontFamily === "string") node.fontFamily = PPTX_CJK_FONT;
  if (node.style && typeof node.style === "object" && typeof node.style.fontFamily === "string") {
    node.style.fontFamily = PPTX_CJK_FONT;
  }
}

function normalizePptxCjkFonts(value) {
  if (Array.isArray(value)) return value.map(normalizePptxCjkFonts);
  if (!value || typeof value !== "object") return value;

  const node = structuredClone(value);
  const directText = typeof node.text === "string" ? node.text : "";
  if (CJK_TEXT_RE.test(directText)) _setPptxCjkFont(node);

  // Tables keep their text under rows/cells rather than elements/children.
  // Walk every nested record so the native export receives the same CJK font
  // rule regardless of the template component type.
  for (const [key, child] of Object.entries(node)) {
    if (key === "font" || key === "style") continue;
    if (Array.isArray(child)) {
      node[key] = child.map(normalizePptxCjkFonts);
    } else if (child && typeof child === "object") {
      node[key] = normalizePptxCjkFonts(child);
    }
  }
  return node;
}

const HYBRID_COMPLEX_TYPES = new Set(["svg", "chart", "infographic"]);

function isHybridComplexVisual(node) {
  if (!node || typeof node !== "object") return false;
  const type = String(node.type || "").toLowerCase();
  if (HYBRID_COMPLEX_TYPES.has(type)) return true;
  if (type !== "image") return false;
  return Boolean(
    node.isIcon ||
    node.is_icon ||
    node.clipPath ||
    node.clip_path ||
    node.clippath ||
    Number(node.crop_scale ?? node.cropScale ?? 1) > 1
  );
}

function selectHybridLayer(value, layer) {
  if (Array.isArray(value)) {
    return value
      .map((item) => selectHybridLayer(item, layer))
      .filter((item) => item != null);
  }
  if (!value || typeof value !== "object") return value;

  const node = structuredClone(value);
  if (isHybridComplexVisual(node)) {
    return layer === "graphics" ? node : null;
  }

  let hasSelectedDescendant = false;
  for (const key of ["elements", "components", "children"]) {
    if (!Array.isArray(node[key])) continue;
    const selected = selectHybridLayer(node[key], layer);
    node[key] = selected;
    hasSelectedDescendant ||= selected.length > 0;
  }
  if (node.child && typeof node.child === "object") {
    const selectedChild = selectHybridLayer(node.child, layer);
    if (selectedChild == null) delete node.child;
    else {
      node.child = selectedChild;
      hasSelectedDescendant = true;
    }
  }

  // The top-level UI record is structural and must retain its background.
  if (!node.type) return node;
  if (layer === "graphics" && !hasSelectedDescendant) return null;
  return node;
}

function hybridNativeUi(ui) {
  const selected = selectHybridLayer(ui, "native");
  if (!selected || typeof selected !== "object") return ui;
  // The graphics PNG owns the slide background. Keeping the native layer
  // transparent prevents its root from covering the rasterized decoration.
  selected.background = "transparent";
  return selected;
}

function hybridGraphicsUi(ui) {
  return selectHybridLayer(ui, "graphics");
}

function hasHybridGraphics(ui) {
  if (!ui || typeof ui !== "object") return false;
  for (const key of ["elements", "components", "children"]) {
    if (Array.isArray(ui[key]) && ui[key].length > 0) return true;
  }
  return Boolean(ui.child && typeof ui.child === "object");
}

function flattenInlineTextSpansForPptx(html) {
  // The browser preview renders nested text spans correctly. The bundled
  // Presenton PPTX extractor, however, reads innerHTML for inline-only text
  // nodes and promotes that string into a PowerPoint text box. If the spans
  // remain, the literal `<span style=...>` markup is written into the PPTX.
  // Export-only HTML can safely flatten these generated spans because user
  // text is HTML-escaped by template-v2-json-to-html.ts. The preview HTML is
  // left untouched, so its typography and layout path do not change.
  let normalized = String(html || "");
  for (let pass = 0; pass < 16; pass += 1) {
    const next = normalized.replace(/<span\b[^>]*>([^<>]*)<\/span>/gi, "$1");
    if (next === normalized) break;
    normalized = next;
  }
  return normalized;
}

function buildFidelityExportHtml(slideCount, outputRoot, taskId) {
  // The browser has already resolved fonts, CSS transforms, gradients, SVG
  // decorations and nested text spans. Re-running those through an
  // HTML-to-PPTX layout engine is the source of the export-only drift. A
  // high-resolution slide artwork keeps the downloaded PPTX visually identical
  // to the verified preview, while the editable HTML path remains available
  // through PPTX_EXPORT_RENDER_MODE=editable for callers that explicitly need
  // native PowerPoint text boxes.
  const pages = [];
  for (let index = 1; index <= slideCount; index += 1) {
    const imagePath = pathToFileURL(path.join(outputRoot, `${taskId}-${index}.png`)).href;
    pages.push(
      `<div class="slide" data-slide-index="${index}" style="width:1280px;height:720px;overflow:hidden;position:relative">` +
      `<img src="${imagePath}" width="1280" height="720" style="display:block;width:1280px;height:720px" />` +
      `</div>`
    );
  }
  return `<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;padding:0;background:#fff}#presentation-slides-wrapper{width:1280px}.slide{page-break-after:always}.slide:last-child{page-break-after:auto}</style></head><body><main id="presentation-slides-wrapper"><div><div><div><div>${pages.join("")}</div></div></div></div></main></body></html>`;
}

function buildEditableExportHtml(pages, originalHead) {
  const body = pages.map((page) =>
    `<div class="slide" data-slide-index="${page.index}" style="width:1280px;height:720px;overflow:hidden;position:relative">${page.editableBody}</div>`
  ).join("");
  return `<!doctype html><html><head><meta charset="utf-8">${originalHead}<style>html,body{margin:0;padding:0;background:#fff}#presentation-slides-wrapper{width:1280px}.slide{page-break-after:always}.slide:last-child{page-break-after:auto}</style></head><body><main id="presentation-slides-wrapper"><div><div><div><div>${body}</div></div></div></div></main></body></html>`;
}

function buildHybridExportHtml(pages, outputRoot, taskId) {
  const slides = pages.map((page, offset) => {
    const index = offset + 1;
    const graphicPath = page.hasGraphics
      ? pathToFileURL(path.join(outputRoot, `${taskId}-graphics-${index}.png`)).href
      : null;
    const graphics = graphicPath
      ? `<img class="hybrid-graphics" src="${graphicPath}" width="1280" height="720" style="position:absolute;inset:0;z-index:0;display:block;width:1280px;height:720px" />`
      : "";
    return `<div class="slide" data-slide-index="${page.index}" style="width:1280px;height:720px;overflow:hidden;position:relative">${graphics}<div class="hybrid-native" style="position:relative;z-index:1;width:1280px;height:720px">${page.nativeBody}</div></div>`;
  });
  return `<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;padding:0;background:#fff}#presentation-slides-wrapper{width:1280px}.slide{page-break-after:always}.slide:last-child{page-break-after:auto}.hybrid-native{pointer-events:none}</style></head><body><main id="presentation-slides-wrapper"><div><div><div><div>${slides.join("")}</div></div></div></div></main></body></html>`;
}

const awaitExists = new Set();
for (const spec of input.slides || []) {
  const fileList = await fs.readdir(path.resolve(templateRoot, "static"), { recursive: true }).catch(() => []);
  for (const rel of fileList) awaitExists.add(path.resolve(templateRoot, "static", rel));
  break;
}

const pptxOnly = input.pptxOnly === true;
const pptxExportMode = String(input.pptxExportMode || process.env.PPTX_EXPORT_RENDER_MODE || "editable").toLowerCase();
const fidelityExport = pptxOnly && ["fidelity", "raster", "image"].includes(pptxExportMode);
// Editable is the product default so downloaded PPTX files contain native
// text boxes and shapes. Fidelity remains an explicit visual-baseline mode.
const hybridExport = pptxOnly && pptxExportMode === "hybrid";
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
  let nativeBody = bodyMatch[1];
  let editableBody = bodyMatch[1];
  let graphicsBody = "";
  if (!fidelityExport) {
    const editableHtml = templateV2UiToHtml(normalizePptxCjkFonts(ui), {
      width: 1280,
      height: 720,
      fonts: resolveFontAssets(input.template.fonts || {}),
    });
    const editableMatch = editableHtml?.match(/<body>([\s\S]*?)<\/body>/i);
    editableBody = editableMatch ? editableMatch[1] : bodyMatch[1];
  }
  if (hybridExport) {
    const nativeHtml = templateV2UiToHtml(hybridNativeUi(normalizePptxCjkFonts(ui)), {
      width: 1280,
      height: 720,
      fonts: resolveFontAssets(input.template.fonts || {}),
    });
    const nativeMatch = nativeHtml?.match(/<body>([\s\S]*?)<\/body>/i);
    nativeBody = nativeMatch ? nativeMatch[1] : "";

    const graphicsUi = hybridGraphicsUi(ui);
    if (hasHybridGraphics(graphicsUi)) {
      const graphicsHtml = templateV2UiToHtml(graphicsUi, {
        width: 1280,
        height: 720,
        fonts: resolveFontAssets(input.template.fonts || {}),
      });
      const graphicsMatch = graphicsHtml?.match(/<body>([\s\S]*?)<\/body>/i);
      if (graphicsMatch) graphicsBody = graphicsMatch[1];
    }
  }
  pages.push({
    index: spec.index,
    preview: `<div class="slide" data-slide-index="${spec.index}" style="width:1280px;height:720px">${bodyMatch[1]}</div>`,
    nativeBody,
    editableBody,
    graphicsBody,
    hasGraphics: Boolean(graphicsBody),
  });
}

// Keep the wrapper shape expected by Presenton's official PPTX exporter:
// #presentation-slides-wrapper > div > div > div > div > .slide.
const html = `<!doctype html><html><head><meta charset="utf-8">${originalHead}<style>html,body{margin:0;padding:0;background:#fff}#presentation-slides-wrapper{width:1280px}.slide{page-break-after:always;overflow:hidden;position:relative}.slide:last-child{page-break-after:auto}</style></head><body><main id="presentation-slides-wrapper"><div><div><div><div>${pages.map((page) => page.preview).join("")}</div></div></div></div></main></body></html>`;
const htmlPath = path.join(outputRoot, `${input.taskId}.html`);
await fs.writeFile(htmlPath, html, "utf8");

const graphicsHtmlPath = hybridExport && pages.some((page) => page.hasGraphics)
  ? path.join(outputRoot, `${input.taskId}.graphics.html`)
  : null;
if (graphicsHtmlPath) {
  const graphicsPages = pages.map((page) =>
    `<div class="graphics-slide" data-slide-index="${page.index}" style="width:1280px;height:720px;overflow:hidden;position:relative">${page.graphicsBody}</div>`
  ).join("");
  const graphicsHtml = `<!doctype html><html><head><meta charset="utf-8">${originalHead}<style>html,body{margin:0;padding:0;background:#fff}#presentation-slides-wrapper{width:1280px}.graphics-slide{page-break-after:always}.graphics-slide:last-child{page-break-after:auto}</style></head><body><main id="presentation-slides-wrapper"><div><div><div><div>${graphicsPages}</div></div></div></div></main></body></html>`;
  await fs.writeFile(graphicsHtmlPath, graphicsHtml, "utf8");
}

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
        deviceScaleFactor: fidelityExport || hybridExport ? 2 : 1,
        args: LAUNCH_ARGS,
      });
    } catch (exc) {
      console.error(JSON.stringify({ level: "warn", message: `explicit chromium launch failed (${exc.message}); retrying with bundled chromium` }));
    }
  }
  return chromium.launchPersistentContext(userDataDir, {
    headless: true,
    viewport: { width: 1280, height: 720 },
    deviceScaleFactor: fidelityExport || hybridExport ? 2 : 1,
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
slideCount = pages.length;
for (let index = 1; index <= slideCount; index += 1) {
  const target = path.join(outputRoot, `${input.taskId}-${index}.png`);
  try {
    await page.locator(`[data-slide-index="${index}"]`).screenshot({ path: target, timeout: 60_000 });
  } catch (exc) {
    throw new Error(`slide ${index} screenshot failed: ${exc.message}`);
  }
}
if (graphicsHtmlPath) {
  const graphicsPage = await browser.newPage();
  try {
    await graphicsPage.goto(pathToFileURL(graphicsHtmlPath).href, { waitUntil: "load" });
    await graphicsPage.waitForTimeout(700);
    for (let index = 1; index <= slideCount; index += 1) {
      const target = path.join(outputRoot, `${input.taskId}-graphics-${index}.png`);
      const page = pages[index - 1];
      if (!page.hasGraphics) continue;
      await graphicsPage.locator(`[data-slide-index="${page.index}"]`).screenshot({ path: target, timeout: 60_000 });
    }
  } finally {
    await graphicsPage.close().catch(() => {});
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
  const exportHtmlPath = path.join(outputRoot, `${input.taskId}.export.html`);
  const exportHtml = fidelityExport
    ? buildFidelityExportHtml(slideCount, outputRoot, input.taskId)
    : hybridExport
      ? flattenInlineTextSpansForPptx(buildHybridExportHtml(pages, outputRoot, input.taskId))
    : flattenInlineTextSpansForPptx(buildEditableExportHtml(pages, originalHead));
  await fs.writeFile(exportHtmlPath, exportHtml, "utf8");
  const exportTask = path.join(outputRoot, `${input.taskId}.export.json`);
  const exportResponse = path.join(outputRoot, `${input.taskId}.export.response.json`);
  await fs.writeFile(exportTask, JSON.stringify({
    type: "export",
    url: pathToFileURL(exportHtmlPath).href,
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
