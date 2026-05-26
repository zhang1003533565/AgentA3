import csv
import json
import re
from pathlib import Path
from typing import Dict


class MultimodalParser:
    def parse(self, path: str) -> Dict[str, str]:
        source = Path(path)
        suffix = source.suffix.lower()
        text = source.read_text(encoding="utf-8", errors="ignore") if source.exists() else ""
        if suffix == ".csv":
            return {"path": path, "modality": "table", "text": self._parse_csv(text)}
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

    def _parse_csv(self, text: str) -> str:
        rows = list(csv.reader(text.splitlines()))
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
