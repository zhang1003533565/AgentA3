"""Deterministic forensic audit for a generated PPTX.

This tool intentionally audits the serialized PPTX package instead of the
in-memory layout model.  It does not modify the input file and does not call
an LLM.  Rendering is optional and delegated to the separate PowerShell
Office renderer when requested.

Usage:
    uv run python scripts/ppt_audit/audit_pptx.py input.pptx --out audit-dir
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
import zipfile
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass, field
from pathlib import Path
from subprocess import CompletedProcess, run
from typing import Any, Iterable
from xml.etree import ElementTree as ET


NS = {
    "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
    "p": "http://schemas.openxmlformats.org/presentationml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "rel": "http://schemas.openxmlformats.org/package/2006/relationships",
}
EMU_PER_INCH = 914400
EMU_PER_POINT = 12700
ELLIPSIS_RE = re.compile(r"(?:…|\.\.\.)")
SLIDE_RE = re.compile(r"^ppt/slides/slide(\d+)\.xml$")
PLACEHOLDER_MARKERS = (
    "Metric", "Last Year", "This Year", "Growth", "Revenue", "Customers",
    "Conversion Rate", "Retention", "Our Team", "Timeline", "Recommendations",
    "Business Model", "www.yourwebsite.com", "John Doe", "Juliana Silva",
)


@dataclass
class Finding:
    code: str
    severity: str
    message: str
    slide: int | None = None
    shape: str | None = None
    details: dict[str, Any] = field(default_factory=dict)


@dataclass
class TextRecord:
    slide: int
    shape_id: str
    name: str
    text: str
    x: int
    y: int
    width: int
    height: int
    font_sizes_pt: list[float]
    font_faces: list[str]
    autofit: str
    paragraphs: int
    runs: int


@dataclass
class ShapeRecord:
    slide: int
    shape_id: str
    name: str
    kind: str
    x: int
    y: int
    width: int
    height: int
    text: str = ""


def _slide_number(name: str) -> int:
    match = SLIDE_RE.match(name)
    return int(match.group(1)) if match else 0


def _text(node: ET.Element | None) -> str:
    if node is None:
        return ""
    return "".join(item.text or "" for item in node.findall(".//a:t", NS)).strip()


def _attr_int(node: ET.Element | None, name: str, default: int = 0) -> int:
    try:
        return int((node.get(name) if node is not None else None) or default)
    except (TypeError, ValueError):
        return default


def _box(root: ET.Element) -> tuple[int, int, int, int]:
    xfrm = root.find(".//p:spPr/a:xfrm", NS)
    if xfrm is None:
        xfrm = root.find(".//p:txBody/../a:xfrm", NS)
    off = xfrm.find("a:off", NS) if xfrm is not None else None
    ext = xfrm.find("a:ext", NS) if xfrm is not None else None
    return (
        _attr_int(off, "x"), _attr_int(off, "y"),
        _attr_int(ext, "cx"), _attr_int(ext, "cy"),
    )


def _shape_name(root: ET.Element) -> tuple[str, str]:
    nv = root.find(".//p:nvSpPr/p:cNvPr", NS)
    if nv is None:
        nv = root.find(".//p:nvPicPr/p:cNvPr", NS)
    return (nv.get("id", "") if nv is not None else "", nv.get("name", "") if nv is not None else "")


def _font_sizes(root: ET.Element) -> list[float]:
    values: list[float] = []
    for node in root.findall(".//a:rPr", NS) + root.findall(".//a:defRPr", NS):
        if node.get("sz"):
            try:
                values.append(round(int(node.get("sz")) / 100, 2))
            except ValueError:
                pass
    return sorted(set(values))


def _font_faces(root: ET.Element) -> list[str]:
    values: list[str] = []
    for node in root.findall(".//a:rPr", NS) + root.findall(".//a:defRPr", NS):
        for child in list(node):
            if child.tag in {f"{{{NS['a']}}}latin", f"{{{NS['a']}}}ea", f"{{{NS['a']}}}cs"} and child.get("typeface"):
                values.append(child.get("typeface") or "")
    return sorted(set(values))


def _autofit(root: ET.Element) -> str:
    body = root.find(".//p:txBody/a:bodyPr", NS)
    if body is None:
        return "unspecified"
    for child in list(body):
        if child.tag.startswith(f"{{{NS['a']}}}"):
            return child.tag.rsplit("}", 1)[-1]
    return "unspecified"


def _slide_size(archive: zipfile.ZipFile) -> dict[str, Any]:
    root = ET.fromstring(archive.read("ppt/presentation.xml"))
    node = root.find("p:sldSz", NS)
    cx = _attr_int(node, "cx")
    cy = _attr_int(node, "cy")
    ratio = round(cx / cy, 6) if cy else 0
    return {
        "cxEmu": cx,
        "cyEmu": cy,
        "widthInches": round(cx / EMU_PER_INCH, 6) if cx else 0,
        "heightInches": round(cy / EMU_PER_INCH, 6) if cy else 0,
        "ratio": ratio,
        "type": node.get("type", "") if node is not None else "",
        "isSixteenByNine": math.isclose(ratio, 16 / 9, rel_tol=0, abs_tol=0.002),
    }


def _theme_fonts(archive: zipfile.ZipFile) -> list[str]:
    fonts: set[str] = set()
    for name in archive.namelist():
        if not name.startswith("ppt/theme/") or not name.endswith(".xml"):
            continue
        try:
            root = ET.fromstring(archive.read(name))
        except ET.ParseError:
            continue
        for node in root.findall(".//a:latin", NS) + root.findall(".//a:ea", NS) + root.findall(".//a:cs", NS):
            if node.get("typeface"):
                fonts.add(node.get("typeface") or "")
    return sorted(fonts)


def _rels_targets(archive: zipfile.ZipFile, slide_name: str) -> set[str]:
    rels_name = f"ppt/slides/_rels/{Path(slide_name).name}.rels"
    if rels_name not in archive.namelist():
        return set()
    root = ET.fromstring(archive.read(rels_name))
    return {
        str(rel.get("Target", "")).replace("..", "ppt", 1).replace("\\", "/")
        for rel in root.findall("rel:Relationship", NS)
        if "media/" in str(rel.get("Target", ""))
    }


def _outside(box: tuple[int, int, int, int], slide: dict[str, Any]) -> bool:
    x, y, width, height = box
    return x < 0 or y < 0 or x + width > slide["cxEmu"] or y + height > slide["cyEmu"]


def _records_for_slide(root: ET.Element, slide_number: int) -> tuple[list[TextRecord], list[ShapeRecord]]:
    texts: list[TextRecord] = []
    shapes: list[ShapeRecord] = []
    for shape in root.findall(".//p:sp", NS) + root.findall(".//p:pic", NS):
        shape_id, name = _shape_name(shape)
        box = _box(shape)
        text = _text(shape)
        kind = "picture" if shape.tag.endswith("pic") else "shape"
        shapes.append(ShapeRecord(slide_number, shape_id, name, kind, *box, text))
        if text:
            texts.append(TextRecord(
                slide=slide_number,
                shape_id=shape_id,
                name=name,
                text=text,
                x=box[0], y=box[1], width=box[2], height=box[3],
                font_sizes_pt=_font_sizes(shape),
                font_faces=_font_faces(shape),
                autofit=_autofit(shape),
                paragraphs=len(shape.findall(".//a:p", NS)),
                runs=len(shape.findall(".//a:r", NS)),
            ))
    return texts, shapes


def _overlap(a: ShapeRecord, b: ShapeRecord) -> float:
    left = max(a.x, b.x)
    top = max(a.y, b.y)
    right = min(a.x + a.width, b.x + b.width)
    bottom = min(a.y + a.height, b.y + b.height)
    area = max(0, right - left) * max(0, bottom - top)
    smaller = min(a.width * a.height, b.width * b.height)
    return area / smaller if smaller else 0


def audit_pptx(path: Path, out_dir: Path | None = None) -> dict[str, Any]:
    """Audit one PPTX and return a JSON-serializable report."""
    path = path.resolve()
    findings: list[Finding] = []
    texts: list[TextRecord] = []
    shapes: list[ShapeRecord] = []
    slides: list[dict[str, Any]] = []
    package: dict[str, Any] = {
        "path": str(path),
        "sizeBytes": path.stat().st_size if path.exists() else 0,
        "sha256": "",
    }
    if not path.is_file():
        findings.append(Finding("INPUT_MISSING", "error", f"PPTX 不存在：{path}"))
        return _report(package, {}, slides, texts, shapes, findings, out_dir)

    try:
        archive = zipfile.ZipFile(path)
    except (OSError, zipfile.BadZipFile) as exc:
        findings.append(Finding("INVALID_ZIP", "error", f"PPTX 不是可读取的 ZIP 包：{exc}"))
        return _report(package, {}, slides, texts, shapes, findings, out_dir)

    with archive:
        names = set(archive.namelist())
        package["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
        package["partCounts"] = {
            "slides": sum(1 for name in names if name.startswith("ppt/slides/slide") and name.endswith(".xml")),
            "slideLayouts": sum(1 for name in names if name.startswith("ppt/slideLayouts/")),
            "slideMasters": sum(1 for name in names if name.startswith("ppt/slideMasters/")),
            "themes": sum(1 for name in names if name.startswith("ppt/theme/")),
            "media": sum(1 for name in names if name.startswith("ppt/media/")),
        }
        package["themeFonts"] = _theme_fonts(archive)
        required = {"[Content_Types].xml", "ppt/presentation.xml"}
        missing = sorted(required - names)
        if missing:
            findings.append(Finding("PACKAGE_MISSING_PART", "error", "缺少 PPTX 必要部件", details={"parts": missing}))
        try:
            slide = _slide_size(archive)
        except (KeyError, ET.ParseError) as exc:
            findings.append(Finding("PRESENTATION_XML_INVALID", "error", f"presentation.xml 无法解析：{exc}"))
            slide = {}
        package["slideSize"] = slide
        if slide.get("isSixteenByNine") and slide.get("type") == "screen4x3":
            findings.append(Finding(
                "SLIDE_SIZE_METADATA_MISMATCH", "warning",
                "实际页面尺寸为 16:9，但 presentation.xml 的 type 标记为 screen4x3",
                details=slide,
            ))
        slide_names = sorted((name for name in names if SLIDE_RE.match(name)), key=_slide_number)
        package["slideCount"] = len(slide_names)
        package["mediaCount"] = len([name for name in names if name.startswith("ppt/media/")])
        if not slide_names:
            findings.append(Finding("NO_SLIDES", "error", "PPTX 没有可读取的幻灯片"))
        for slide_name in slide_names:
            number = _slide_number(slide_name)
            try:
                root = ET.fromstring(archive.read(slide_name))
            except ET.ParseError as exc:
                findings.append(Finding("SLIDE_XML_INVALID", "error", f"幻灯片 XML 无法解析：{exc}", number))
                continue
            slide_texts, slide_shapes = _records_for_slide(root, number)
            texts.extend(slide_texts)
            shapes.extend(slide_shapes)
            media_targets = sorted(_rels_targets(archive, slide_name))
            missing_media = [target for target in media_targets if target not in names]
            if missing_media:
                findings.append(Finding("MISSING_MEDIA_TARGET", "error", "幻灯片引用了不存在的媒体文件", number, details={"targets": missing_media}))
            slides.append({
                "slide": number,
                "xmlPart": slide_name,
                "textCount": len(slide_texts),
                "shapeCount": len(slide_shapes),
                "pictureCount": sum(1 for item in slide_shapes if item.kind == "picture"),
                "mediaTargets": media_targets,
            })
            if not slide:
                continue
            for item in slide_shapes:
                if item.width <= 0 or item.height <= 0:
                    findings.append(Finding("ZERO_SIZE_SHAPE", "warning", "元素宽高为 0", number, item.shape_id, asdict(item)))
                if _outside((item.x, item.y, item.width, item.height), slide):
                    findings.append(Finding("OUT_OF_BOUNDS", "error", "元素超出幻灯片画布", number, item.shape_id, asdict(item)))
            for item in slide_texts:
                if ELLIPSIS_RE.search(item.text):
                    findings.append(Finding("TEXT_TRUNCATED_MARKER", "error", "文本内容已经包含省略号，疑似在写入 PPTX 前被截断", number, item.shape_id, {"text": item.text[:240]}))
                marker = next((value for value in PLACEHOLDER_MARKERS if value.casefold() in item.text.casefold()), None)
                if marker:
                    findings.append(Finding("TEMPLATE_PLACEHOLDER_LEAK", "error", "发现模板示例/占位文本", number, item.shape_id, {"marker": marker, "text": item.text[:240]}))
                if len(item.text) >= 20 and item.width and item.height:
                    chars_per_line = max(1, item.width / (max(item.font_sizes_pt or [12]) * EMU_PER_POINT * 0.95))
                    estimated_lines = math.ceil(len(item.text) / chars_per_line)
                    if estimated_lines > 4 and item.height < estimated_lines * max(item.font_sizes_pt or [12]) * EMU_PER_POINT * 1.35:
                        findings.append(Finding("TEXT_BOX_CAPACITY_RISK", "warning", "文本长度与文本框容量存在明显风险", number, item.shape_id, {"textChars": len(item.text), "estimatedLines": estimated_lines, "boxEmu": [item.width, item.height], "fontSizesPt": item.font_sizes_pt}))
                if item.autofit == "unspecified" and len(item.text) >= 30:
                    findings.append(Finding("AUTOFIT_UNSPECIFIED", "warning", "较长文本未声明 AutoFit 行为，渲染器可能采用不同默认值", number, item.shape_id, {"textChars": len(item.text)}))
            slide_texts_by_value = defaultdict(list)
            for item in slide_texts:
                slide_texts_by_value[item.text].append(item.shape_id)
            for value, ids in slide_texts_by_value.items():
                if len(value) >= 8 and len(ids) > 1:
                    findings.append(Finding("DUPLICATE_TEXT_SAME_SLIDE", "warning", "同一页存在重复文本", number, details={"text": value[:240], "shapeIds": ids}))
            for index, first in enumerate(slide_shapes):
                if not first.text:
                    continue
                for second in slide_shapes[index + 1:]:
                    if not second.text:
                        continue
                    ratio = _overlap(first, second)
                    if ratio >= 0.35:
                        findings.append(Finding("TEXT_SHAPE_OVERLAP", "warning", "两个带文字元素高度重叠", number, first.shape_id, {"otherShape": second.shape_id, "overlapRatio": round(ratio, 3)}))

    counts = Counter(item.text for item in texts if len(item.text) >= 12)
    for value, count in counts.items():
        if count >= 3:
            findings.append(Finding("DUPLICATE_TEXT_ACROSS_SLIDES", "warning", "同一长文本跨多页重复", details={"count": count, "text": value[:240]}))
    return _report(package, slide, slides, texts, shapes, findings, out_dir)


def _report(package: dict[str, Any], slide: dict[str, Any], slides: list[dict[str, Any]], texts: list[TextRecord], shapes: list[ShapeRecord], findings: list[Finding], out_dir: Path | None) -> dict[str, Any]:
    errors = sum(1 for item in findings if item.severity == "error")
    warnings = sum(1 for item in findings if item.severity == "warning")
    result = {
        "schemaVersion": 1,
        "passed": errors == 0,
        "summary": {"errors": errors, "warnings": warnings, "findingCount": len(findings)},
        "package": package,
        "slideSize": slide,
        "slides": slides,
        "texts": [asdict(item) for item in texts],
        "shapes": [asdict(item) for item in shapes],
        "findings": [asdict(item) for item in findings],
    }
    if out_dir:
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / "audit.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        (out_dir / "audit.md").write_text(_markdown(result), encoding="utf-8")
    return result


def _markdown(result: dict[str, Any]) -> str:
    package = result.get("package") or {}
    summary = result.get("summary") or {}
    lines = [
        "# PPTX Forensic Audit",
        "",
        f"- 文件：`{package.get('path', '')}`",
        f"- 页数：{package.get('slideCount', 0)}",
        f"- 结果：{'PASS' if result.get('passed') else 'FAIL'}",
        f"- 错误：{summary.get('errors', 0)}；警告：{summary.get('warnings', 0)}",
        "",
        "## Slide size",
        "",
        f"`{json.dumps(result.get('slideSize') or {}, ensure_ascii=False)}`",
        "",
        "## Findings",
        "",
    ]
    findings = result.get("findings") or []
    if not findings:
        lines.append("没有发现确定性问题。")
    else:
        for item in findings:
            slide_label = f"（第{item['slide']}页）" if item.get("slide") else ""
            lines.append(
                f"- **{item['severity'].upper()} `{item['code']}`**"
                f"{slide_label}：{item['message']}"
            )
    lines.extend(["", "## Render", "", "渲染由独立的 Office/LibreOffice 适配器执行；本报告不把缺少渲染器当作 PPT 内容通过。", ""])
    return "\n".join(lines)


def _run_office_renderer(pptx: Path, out_dir: Path) -> CompletedProcess[str]:
    script = Path(__file__).with_name("render_office.ps1")
    return run([
        "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script),
        "-Pptx", str(pptx), "-OutputDir", str(out_dir),
    ], capture_output=True, text=True, check=False)


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Audit a serialized PPTX without modifying it")
    parser.add_argument("pptx", type=Path)
    parser.add_argument("--out", type=Path, default=None, help="output directory for audit.json/audit.md")
    parser.add_argument("--render-office", action="store_true", help="also render slides through installed Microsoft PowerPoint")
    args = parser.parse_args(list(argv) if argv is not None else None)
    out_dir = args.out.resolve() if args.out else None
    result = audit_pptx(args.pptx, out_dir)
    if args.render_office and out_dir:
        render_dir = out_dir / "office-render"
        process = _run_office_renderer(args.pptx.resolve(), render_dir)
        render_status = {
            "requested": True,
            "exitCode": process.returncode,
            "available": process.returncode == 0,
            "stdout": process.stdout[-4000:],
            "stderr": process.stderr[-4000:],
        }
        (out_dir / "render.json").write_text(json.dumps(render_status, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(render_status, ensure_ascii=False))
    print(json.dumps(result["summary"], ensure_ascii=False))
    return 0 if result["passed"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
