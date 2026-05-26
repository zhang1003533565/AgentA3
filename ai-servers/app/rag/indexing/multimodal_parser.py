import csv
import json
import re
from pathlib import Path
from typing import Dict


class MultimodalParser:
    def parse(self, path: str) -> Dict[str, str]:
        source = Path(path)
        suffix = source.suffix.lower()
        if suffix == ".pdf":
            return {"path": path, "modality": "pdf", "text": self._parse_pdf(source)}
        if suffix in {".png", ".jpg", ".jpeg", ".webp", ".gif"}:
            return {"path": path, "modality": "image", "text": self._parse_image_metadata(source)}

        text = source.read_text(encoding="utf-8", errors="ignore") if source.exists() else ""
        if suffix in {".csv", ".tsv"}:
            delimiter = "\t" if suffix == ".tsv" else ","
            return {"path": path, "modality": "table", "text": self._parse_delimited(text, delimiter)}
        if suffix == ".json":
            return {"path": path, "modality": "structured_json", "text": self._parse_json(text)}
        if suffix in {".html", ".htm"}:
            return {"path": path, "modality": "html", "text": self._strip_html(text)}
        if suffix in {".md", ".markdown"}:
            image_count = len(re.findall(r"!\[[^\]]*\]\([^)]+\)", text))
            table_count = sum(1 for line in text.splitlines() if "|" in line)
            prefix = f"Markdown 多模态摘要：图片 {image_count} 个，表格行 {table_count} 行。\n"
            return {"path": path, "modality": "markdown", "text": prefix + text}
        return {"path": path, "modality": "text", "text": text}

    def _parse_delimited(self, text: str, delimiter: str) -> str:
        rows = list(csv.reader(text.splitlines(), delimiter=delimiter))
        if not rows:
            return ""
        preview = rows[:20]
        return "\n".join(" | ".join(cell.strip() for cell in row) for row in preview)

    def _parse_json(self, text: str) -> str:
        try:
            payload = json.loads(text)
        except Exception:
            return text
        return json.dumps(payload, ensure_ascii=False, indent=2)[:4000]

    def _strip_html(self, text: str) -> str:
        without_scripts = re.sub(r"<(script|style)[\s\S]*?</\1>", "", text, flags=re.IGNORECASE)
        without_tags = re.sub(r"<[^>]+>", " ", without_scripts)
        return re.sub(r"\s+", " ", without_tags).strip()

    def _parse_pdf(self, source: Path) -> str:
        if not source.exists():
            return ""
        try:
            from pypdf import PdfReader
        except Exception:
            return f"PDF 文件：{source.name}。未安装 pypdf，暂未抽取正文。"
        reader = PdfReader(str(source))
        pages = []
        for index, page in enumerate(reader.pages[:20], start=1):
            try:
                pages.append(f"[page {index}]\n{page.extract_text() or ''}")
            except Exception:
                pages.append(f"[page {index}]\n")
        return "\n\n".join(pages).strip()

    def _parse_image_metadata(self, source: Path) -> str:
        if not source.exists():
            return ""
        stat = source.stat()
        return (
            f"图片文件：{source.name}\n"
            f"大小：{stat.st_size} bytes\n"
            "当前未接入视觉模型/OCR，已保留图片元数据用于后续多模态索引。"
        )
