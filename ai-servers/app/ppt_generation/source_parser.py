from __future__ import annotations

import re
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path
from xml.etree import ElementTree

import fitz
from docx import Document
from pptx import Presentation


class PptSourceParseError(RuntimeError):
    pass


def extract_source_text(path: Path, max_characters: int = 200_000) -> str:
    extension = path.suffix.lower()
    if extension == ".txt":
        text = _decode_text(path.read_bytes())
    elif extension == ".pdf":
        text = _extract_pdf(path)
    elif extension == ".docx":
        text = "\n".join(p.text for p in Document(path).paragraphs if p.text.strip())
    elif extension == ".pptx":
        text = _extract_pptx(path)
    elif extension == ".xlsx":
        text = _extract_xlsx(path)
    elif extension in {".doc", ".ppt", ".xls"}:
        text = _extract_legacy_office(path)
    else:
        raise PptSourceParseError("不支持的资料格式")
    normalized = re.sub(r"\n{3,}", "\n\n", str(text or "")).strip()
    if not normalized:
        raise PptSourceParseError("未从资料中解析到可用文本")
    return normalized[:max_characters]


def _decode_text(content: bytes) -> str:
    for encoding in ("utf-8-sig", "utf-8", "gb18030"):
        try:
            return content.decode(encoding)
        except UnicodeDecodeError:
            continue
    return content.decode("utf-8", errors="replace")


def _extract_pdf(path: Path) -> str:
    document = fitz.open(path)
    try:
        return "\n\n".join(page.get_text("text") for page in document)
    finally:
        document.close()


def _extract_pptx(path: Path) -> str:
    rows = []
    for index, slide in enumerate(Presentation(path).slides, start=1):
        values = []
        for shape in slide.shapes:
            if getattr(shape, "has_text_frame", False):
                value = str(shape.text or "").strip()
                if value:
                    values.append(value)
        if values:
            rows.append(f"第 {index} 页\n" + "\n".join(values))
    return "\n\n".join(rows)


def _extract_xlsx(path: Path) -> str:
    namespace = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
    with zipfile.ZipFile(path) as archive:
        shared = []
        if "xl/sharedStrings.xml" in archive.namelist():
            root = ElementTree.fromstring(archive.read("xl/sharedStrings.xml"))
            for item in root.findall("m:si", namespace):
                shared.append("".join(node.text or "" for node in item.findall(".//m:t", namespace)))
        rows = []
        sheets = sorted(name for name in archive.namelist() if name.startswith("xl/worksheets/sheet") and name.endswith(".xml"))
        for sheet_index, name in enumerate(sheets, start=1):
            root = ElementTree.fromstring(archive.read(name))
            rows.append(f"工作表 {sheet_index}")
            for row in root.findall(".//m:row", namespace):
                values = []
                for cell in row.findall("m:c", namespace):
                    value = cell.find("m:v", namespace)
                    raw = value.text if value is not None else ""
                    if cell.get("t") == "s" and raw.isdigit() and int(raw) < len(shared):
                        raw = shared[int(raw)]
                    if raw:
                        values.append(raw)
                if values:
                    rows.append(" | ".join(values))
        return "\n".join(rows)


def _extract_legacy_office(path: Path) -> str:
    soffice = shutil.which("soffice") or shutil.which("libreoffice")
    if not soffice:
        raise PptSourceParseError("旧版 Office 文件解析需要安装 LibreOffice")
    target_extension = "pptx" if path.suffix.lower() == ".ppt" else "xlsx" if path.suffix.lower() == ".xls" else "docx"
    with tempfile.TemporaryDirectory(prefix="a3-ppt-source-") as directory:
        subprocess.run(
            [soffice, "--headless", "--convert-to", target_extension, "--outdir", directory, str(path)],
            check=True,
            capture_output=True,
            timeout=120,
        )
        converted = Path(directory) / f"{path.stem}.{target_extension}"
        if not converted.is_file():
            raise PptSourceParseError("LibreOffice 未生成可解析文件")
        return extract_source_text(converted)

