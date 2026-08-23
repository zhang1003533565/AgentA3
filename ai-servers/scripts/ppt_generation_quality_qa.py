"""Render a before/after comparison for the PPT fallback-quality path."""

from __future__ import annotations

import copy
import os
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.ppt_generation.presenton_html_renderer import render_presenton_html  # noqa: E402
from app.ppt_generation.service import (  # noqa: E402
    _fill_layout_with_slide_text,
    _node_text_capacity,
    _set_text_node_content,
    _text_font_size,
)
from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog  # noqa: E402
from app.rag.document_conversion import generated_exporter  # noqa: E402


POINTS = [
    "问题一：现状存在明显瓶颈",
    "问题二：用户反馈集中在效率不足",
    "问题三：流程缺少统一标准",
]


def _walk(value):
    if isinstance(value, list):
        for item in value:
            yield from _walk(item)
    elif isinstance(value, dict):
        yield value
        for key in ("elements", "components", "children"):
            if key in value:
                yield from _walk(value[key])
        if "child" in value:
            yield from _walk(value["child"])


def _legacy_fill(layout):
    """Approximate the previous fallback: distribute points by box area only."""
    result = copy.deepcopy(layout)
    title = "现状与问题"
    text_nodes = [node for node in _walk(result) if node.get("type") == "text"]

    def decorative(node):
        text = str(node.get("text") or ((node.get("runs") or [{}])[0].get("text") or "")).strip()
        return bool(text.isdigit() or (text.isupper() and len(text) <= 12) or _text_font_size(node) >= 100)

    fillable = [node for node in text_nodes if not decorative(node)]
    title_node = next((node for node in fillable if "heading" in str(node.get("name") or "").lower()), fillable[0])
    _set_text_node_content(title_node, title)
    body_nodes = [node for node in fillable if node is not title_node and _node_text_capacity(node) >= 8]
    body_nodes.sort(key=lambda node: float((node.get("size") or {}).get("width") or 0) * float((node.get("size") or {}).get("height") or 0), reverse=True)
    for node, point in zip(body_nodes, POINTS):
        _set_text_node_content(node, f"• {point}")
    return result


def _main() -> None:
    output_root = Path(os.environ.get("PPT_QA_OUTPUT_ROOT") or (Path.cwd() / "data" / "ppt-quality-qa"))
    output_root.mkdir(parents=True, exist_ok=True)
    os.environ["AI_EXPORT_ROOT"] = str(output_root)
    os.environ["PPT_PRESENTON_RENDER_TIMEOUT_SECONDS"] = "600"

    catalog = EmbeddedTemplateCatalog()
    layout = catalog.get_layout("general", "title_description_bullet_points")
    outline = {"index": 1, "title": "现状与问题", "content": POINTS}
    old_ui = _legacy_fill(layout)
    new_ui = _fill_layout_with_slide_text(layout, outline, outline)
    _, _, previews, _ = render_presenton_html(
        [
            {"index": 1, "ui": old_ui},
            {"index": 2, "ui": new_ui},
        ],
        "ppt-generation-quality",
        {"templateId": "general", "previewOnly": True},
    )
    export_root = generated_exporter._current_export_root()
    paths = [export_root / str(item.get("storageKey") or "") for item in previews]
    if len(paths) != 2 or not all(path.is_file() for path in paths):
        raise RuntimeError(f"rendered previews missing: {paths}")
    images = [Image.open(path).convert("RGB") for path in paths]
    width = min(image.width for image in images)
    images = [image.resize((width, int(image.height * width / image.width))) for image in images]
    label_h = 56
    canvas = Image.new("RGB", (width * 2 + 24, images[0].height + label_h + 24), "#eef1f5")
    draw = ImageDraw.Draw(canvas)
    font_path = next(
        (candidate for candidate in (r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\simhei.ttf") if Path(candidate).is_file()),
        None,
    )
    label_font = ImageFont.truetype(font_path, 22) if font_path else ImageFont.load_default()
    for index, image in enumerate(images):
        x = 8 + index * (width + 8)
        canvas.paste(image, (x, label_h + 8))
        draw.text((x + 12, 16), "优化前（按面积顺序填充）" if index == 0 else "优化后（卡片标题/正文配对）", fill="#17202a", font=label_font)
    comparison = output_root / "ppt-generation-quality-before-after.png"
    canvas.save(comparison)
    print(comparison)
    for path in paths:
        print(path)


if __name__ == "__main__":
    _main()
