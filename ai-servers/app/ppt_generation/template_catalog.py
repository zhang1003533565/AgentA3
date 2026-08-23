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
        for template_id in self._template_ids():
            name, description = _CATALOG.get(
                template_id,
                (template_id.replace("_", " ").replace("-", " ").title(), "项目内发现的 PPT 模板"),
            )
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
        try:
            directory = self._directory(template_id)
        except FileNotFoundError:
            return False
        return directory.is_dir() and (directory / "template.json").is_file()

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
        # Keep the structure selector aligned with the same semantic model
        # used by validation/filling.  The old summary exposed only names and
        # element types, so a metric slot looked like an ordinary text slot to
        # the selector and card count was left to guesswork.
        from app.ppt_generation.template_model import parse_slide_layout, semantic_content_contract

        for layout in payload.get("layouts") or []:
            if not isinstance(layout, dict) or not str(layout.get("id") or "").strip():
                continue
            kinds: set[str] = set()
            names: List[str] = []
            texts: List[str] = []
            self._collect_slots(layout.get("components") or [], kinds, names)
            self._collect_preview_texts(layout.get("components") or [], texts)
            semantic_slots: List[Dict[str, Any]] = []
            try:
                model = parse_slide_layout(layout)
                for name, elements in model.elements.items():
                    for occurrence, element in enumerate(elements):
                        if element.element_type not in {"text", "text-list"} or not element.mutable_text:
                            continue
                        semantic_slots.append({
                            "name": name,
                            "occurrence": occurrence,
                            "semanticRole": element.semantic_role,
                            "contentContract": semantic_content_contract(
                                element.semantic_role,
                                element.constraint,
                            ),
                            "capacity": {
                                "recommendedChars": int(element.constraint.recommended_chars),
                                "hardMaxChars": int(element.constraint.hard_max_chars),
                                "maxLines": int(element.constraint.max_lines),
                                "charsPerLine": int(element.constraint.chars_per_line),
                            } if element.constraint else {},
                        })
            except Exception:
                # A malformed optional semantic summary must not make an
                # otherwise readable template disappear from the catalog.
                semantic_slots = []
            summaries.append({
                "id": str(layout["id"]),
                "description": str(layout.get("description") or ""),
                "elementTypes": sorted(kinds),
                "slots": names[:40],
                "previewTexts": texts[:8],
                "semanticSlots": semantic_slots[:80],
                "requiresNumericData": any(
                    slot.get("semanticRole") in {"metric_value", "card_value"}
                    for slot in semantic_slots
                ),
                # Presenton generates slide content against the component
                # schema, not against coordinates. Keep the useful contract
                # fields in the prompt while avoiding the full template JSON.
                "componentSchema": self.component_schema(layout.get("components") or []),
            })
        return summaries

    def _directory(self, template_id: str) -> Path:
        candidate = str(template_id or "").strip()
        path = (self.root / candidate).resolve()
        if not candidate or not path.is_relative_to(self.root) or not path.is_dir() or not (path / "template.json").is_file():
            raise FileNotFoundError("PPT 模板不存在")
        return path

    def _template_ids(self) -> List[str]:
        """Return catalogued templates plus any valid template.json directories.

        The bundled catalog keeps names/descriptions stable for the shipped
        templates, while directory discovery prevents a newly added project
        template from silently remaining unavailable in the app.
        """
        discovered: List[str] = []
        try:
            discovered = sorted(
                path.name
                for path in self.root.iterdir()
                if path.is_dir() and (path / "template.json").is_file()
            )
        except OSError:
            discovered = []
        return list(dict.fromkeys([*(_CATALOG.keys()), *discovered]))

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
    def component_schema(cls, values: Any) -> List[Dict[str, Any]]:
        result: List[Dict[str, Any]] = []
        try:
            from app.ppt_generation.template_model import parse_slide_layout, semantic_content_contract

            schema_model = parse_slide_layout({"id": "component-schema", "components": deepcopy(values)})
        except Exception:
            schema_model = None

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
                occurrence = sum(1 for existing in result if existing.get("name") == name)
                item: Dict[str, Any] = {"name": name, "type": element_type, "occurrence": occurrence}
                for key in ("max_length", "min_length", "max_children", "min_children"):
                    if value.get(key) is not None:
                        item[key] = value[key]
                if schema_model is not None:
                    element = schema_model.element(name, occurrence)
                    if element is not None and element.constraint is not None:
                        constraint = element.constraint
                        # The raw template max_length often describes an
                        # English/ideal-font box and is larger than the real
                        # CJK geometry. Expose the effective contract so the
                        # model cannot plan copy the browser will wrap.
                        item["max_length"] = int(constraint.hard_max_chars)
                        if "min_length" in item:
                            item["min_length"] = min(int(item["min_length"] or 0), int(constraint.hard_max_chars))
                        item["role"] = element.role
                        item["semanticRole"] = element.semantic_role
                        item["contentContract"] = semantic_content_contract(
                            element.semantic_role,
                            constraint,
                        )
                        item["capacity"] = {
                            "recommendedChars": int(constraint.recommended_chars),
                            "hardMaxChars": int(constraint.hard_max_chars),
                            "maxLines": int(constraint.max_lines),
                            "charsPerLine": int(constraint.chars_per_line),
                        }
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
