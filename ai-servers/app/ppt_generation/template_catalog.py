from __future__ import annotations

import json
from copy import deepcopy
from pathlib import Path
from typing import Any, Dict, List, Tuple


_TEMPLATE_ROOT = Path(__file__).resolve().parent / "assets" / "templates"
_CATALOG = {
    "general": ("简约通用", "清晰留白，适合课程复习与知识讲解"),
    "dynamic": ("活力校园", "明快配色与卡片结构，适合课堂展示"),
    "editorial": ("编辑风格", "深色大气排版，适合杂志风格演示"),
    "executive": ("深色专注", "高对比深色风格，适合重点总结"),
    "modern": ("现代几何", "现代网格与几何装饰，适合概念呈现"),
    "momentum": ("动势表达", "强调节奏和视觉层级，适合故事化讲解"),
    "standard": ("标准教学", "稳健规范的教学版式，适合正式课件"),
    "swift": ("轻快简报", "紧凑轻量，适合快速复习和提纲展示"),
}


class EmbeddedTemplateCatalog:
    def __init__(self, root: Path = _TEMPLATE_ROOT) -> None:
        self.root = root.resolve()

    def list_templates(self) -> List[Dict[str, Any]]:
        templates: List[Dict[str, Any]] = []
        for template_id, (name, description) in _CATALOG.items():
            directory = self._directory(template_id)
            if not directory.is_dir():
                continue
            templates.append({
                "id": template_id,
                "name": name,
                "description": description,
                "layout_count": self._layout_count(directory / "template.json"),
                "is_default": template_id == "general",
            })
        return templates

    def thumbnail(self, template_id: str) -> Tuple[bytes, str]:
        path = (self._directory(template_id) / "static" / "thumbnail.png").resolve()
        if not path.is_relative_to(self.root) or not path.is_file():
            raise FileNotFoundError("PPT 模板缩略图不存在")
        return path.read_bytes(), "image/png"

    def contains(self, template_id: str) -> bool:
        return template_id in _CATALOG and self._directory(template_id).is_dir()

    def load(self, template_id: str) -> Dict[str, Any]:
        path = self._directory(template_id) / "template.json"
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as exc:
            raise ValueError(f"PPT 模板 {template_id} 无法读取") from exc
        if not isinstance(payload, dict) or not isinstance(payload.get("layouts"), list):
            raise ValueError(f"PPT 模板 {template_id} 格式无效")
        return deepcopy(payload)

    def get_layout(self, template_id: str, layout_id: str) -> Dict[str, Any]:
        """Return the original Presenton layout JSON without flattening it.

        The content model receives this exact UI tree and must return it back
        with only user-visible values changed. Keeping the full tree here avoids
        rebuilding nested groups in the application renderer.
        """
        payload = self.load(template_id)
        wanted = str(layout_id or "").strip()
        for layout in payload.get("layouts") or []:
            if isinstance(layout, dict) and str(layout.get("id") or "") == wanted:
                return deepcopy(layout)
        raise KeyError(f"Presenton layout not found: {layout_id}")

    def layout_summaries(self, template_id: str) -> List[Dict[str, Any]]:
        payload = self.load(template_id)
        summaries: List[Dict[str, Any]] = []
        for layout in payload.get("layouts") or []:
            if not isinstance(layout, dict) or not str(layout.get("id") or "").strip():
                continue
            kinds: set[str] = set()
            names: List[str] = []
            texts: List[str] = []
            self._collect_slots(layout.get("components") or [], kinds, names)
            self._collect_preview_texts(layout.get("components") or [], texts)
            summaries.append({
                "id": str(layout["id"]),
                "description": str(layout.get("description") or ""),
                "elementTypes": sorted(kinds),
                "slots": names[:40],
                "previewTexts": texts[:8],
                # Presenton generates slide content against the component
                # schema, not against coordinates. Keep the useful contract
                # fields in the prompt while avoiding the full template JSON.
                "componentSchema": self._component_schema(layout.get("components") or []),
            })
        return summaries

    def _directory(self, template_id: str) -> Path:
        if template_id not in _CATALOG:
            raise FileNotFoundError("PPT 模板不存在")
        path = (self.root / template_id).resolve()
        if not path.is_relative_to(self.root):
            raise FileNotFoundError("PPT 模板不存在")
        return path

    @staticmethod
    def _layout_count(path: Path) -> int:
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return 0
        layouts = payload.get("layouts") if isinstance(payload, dict) else None
        return len(layouts) if isinstance(layouts, list) else 0

    @classmethod
    def _collect_slots(cls, values: Any, kinds: set[str], names: List[str]) -> None:
        if isinstance(values, list):
            for value in values:
                cls._collect_slots(value, kinds, names)
            return
        if not isinstance(values, dict):
            return
        element_type = str(values.get("type") or "").strip()
        if element_type:
            kinds.add(element_type)
        name = str(values.get("name") or "").strip()
        if name and name not in names:
            names.append(name)
        for key in ("components", "elements", "children"):
            cls._collect_slots(values.get(key), kinds, names)
        cls._collect_slots(values.get("child"), kinds, names)

    @classmethod
    def _component_schema(cls, values: Any) -> List[Dict[str, Any]]:
        result: List[Dict[str, Any]] = []

        def visit(value: Any) -> None:
            if isinstance(value, list):
                for item in value:
                    visit(item)
                return
            if not isinstance(value, dict):
                return
            name = str(value.get("name") or "").strip()
            element_type = str(value.get("type") or "").strip()
            if name and element_type:
                item: Dict[str, Any] = {"name": name, "type": element_type}
                for key in ("max_length", "min_length", "max_children", "min_children"):
                    if value.get(key) is not None:
                        item[key] = value[key]
                if element_type == "image":
                    item["replaceable"] = "replaceable_template_image" in str(value.get("data") or "")
                result.append(item)
            for key in ("components", "elements", "children"):
                visit(value.get(key))
            visit(value.get("child"))

        visit(values)
        return result[:80]

    @classmethod
    def _collect_preview_texts(cls, values: Any, texts: List[str]) -> None:
        if isinstance(values, list):
            for value in values:
                cls._collect_preview_texts(value, texts)
            return
        if not isinstance(values, dict):
            return
        if str(values.get("type") or "").strip() == "text":
            data = values.get("data")
            if isinstance(data, dict):
                text = str(data.get("text") or "").strip()
                if text and text not in texts:
                    texts.append(text)
            elif isinstance(data, str):
                text = data.strip()
                if text and "replaceable_template_image" not in text and text not in texts:
                    texts.append(text)
            runs = values.get("runs")
            if isinstance(runs, list):
                for run in runs:
                    if not isinstance(run, dict):
                        continue
                    text = str(run.get("text") or "").strip()
                    if text and text not in texts:
                        texts.append(text)
        for key in ("components", "elements", "children"):
            cls._collect_preview_texts(values.get(key), texts)
        cls._collect_preview_texts(values.get("child"), texts)
