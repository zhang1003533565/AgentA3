"""PPT 视觉 QA 渲染脚本（开发工具）。

渲染同一版式的三种状态供视觉 QA 对比：
1. pristine — 模板原样（占位文本，视觉基线）
2. bad      — 旧行为：超长 AI 内容直接合并（未修复）
3. fixed    — 新行为：超长内容经 ContentFitter/RepairEngine 修复后

输出 PNG 预览路径（AI_EXPORT_ROOT 下），交给视觉模型做第二层检测
（结构/错位/溢出/拥挤/留白/失衡）。程序几何检测见 layout_validator。

用法: python scripts/ppt_visual_qa_render.py [--layout title_intro] [--template general]
"""

from __future__ import annotations

import argparse
import copy
import os
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.ppt_generation.presenton_html_renderer import render_presenton_html  # noqa: E402
from app.ppt_generation.repair_engine import RepairEngine  # noqa: E402
from app.ppt_generation.service import _merge_content_into_layout  # noqa: E402
from app.ppt_generation.template_catalog import EmbeddedTemplateCatalog  # noqa: E402
from app.ppt_generation.template_model import parse_slide_layout  # noqa: E402

LONG_TITLE = "这是一段超长的标题内容用来测试溢出检测与自动压缩机制是否能够正常工作并且保持版式稳定"
LONG_BODY = (
    "自动完成重复任务降低人工处理成本提升整体效率，这是企业数字化转型的核心价值所在，"
    "通过引入智能化工具，团队可以把精力集中在更有创造性的工作上，"
    "同时降低出错率并提升响应速度，让业务持续获得竞争优势。" * 2
)


def _merge(layout, content):
    return _merge_content_into_layout(layout, content)


def _set_text(tree, name, text):
    def walk(node):
        if isinstance(node, list):
            for item in node:
                walk(item)
            return
        if isinstance(node, dict):
            if node.get("name") == name:
                node["text"] = text
                node["runs"] = [{"text": text, "font": dict(node.get("font") or {})}]
            for key in ("elements", "components", "children"):
                if key in node:
                    walk(node[key])
            if "child" in node:
                walk(node["child"])

    walk(tree)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--template", default="general")
    parser.add_argument("--layout", default="title_intro")
    args = parser.parse_args()

    catalog = EmbeddedTemplateCatalog()
    layout = catalog.get_layout(args.template, args.layout)
    model = parse_slide_layout(layout)

    # 1) 模板原样
    pristine = {"index": 1, "ui": {"components": copy.deepcopy(layout["components"])}}
    # 2) 旧行为：超长内容直接合并（无修复）
    bad_merged = _merge(layout, {
        "headline_text": LONG_TITLE,
        "body_copy": LONG_BODY,
        "attribution_name": "视觉 QA 对比测试",
        "attribution_detail": "展示内容溢出与修复效果",
    })
    bad = {"index": 2, "ui": bad_merged}
    # 3) 新行为：合并后走修复闭环
    fixed_ui = _merge(layout, {
        "headline_text": LONG_TITLE,
        "body_copy": LONG_BODY,
        "attribution_name": "视觉 QA 对比测试",
        "attribution_detail": "展示内容溢出与修复效果",
    })
    outcome = RepairEngine().repair(fixed_ui, model, llm_rewrite=None)
    fixed = {"index": 3, "ui": outcome.ui}

    export_root = Path(os.getenv("AI_EXPORT_ROOT") or (Path(tempfile.gettempdir()) / "ppt-visual-qa"))
    export_root.mkdir(parents=True, exist_ok=True)
    os.environ["AI_EXPORT_ROOT"] = str(export_root)
    os.environ["PPT_PRESENTON_RENDER_TIMEOUT_SECONDS"] = "600"

    print(f"rendering 3 slides ({args.template}/{args.layout}) -> {export_root}")
    pdf_attachment, _, previews, pptx_attachment = render_presenton_html(
        [pristine, bad, fixed],
        "visual-qa-comparison",
        {"templateId": args.template},
    )
    print(f"pdf: {pdf_attachment}")
    print(f"pptx: {bool(pptx_attachment)}")
    for preview in previews:
        print(f"preview slide {preview.get('slideIndex')}: {preview.get('filePath')}")


if __name__ == "__main__":
    main()
