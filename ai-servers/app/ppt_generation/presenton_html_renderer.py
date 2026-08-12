from __future__ import annotations

import json
import os
import shutil
import subprocess
import uuid
from pathlib import Path
from typing import Any, Mapping, Tuple

from app.rag.document_conversion import generated_exporter


_ROOT = Path(__file__).resolve().parents[2]
_RUNTIME = _ROOT / "presenton_runtime"
_SCRIPT = _RUNTIME / "src" / "render.mjs"
_TEMPLATES = Path(__file__).resolve().parent / "assets" / "templates"


def render_presenton_html(
    slides: list[Mapping[str, Any]],
    title: str,
    settings: Mapping[str, Any],
) -> Tuple[dict[str, Any], Path, list[dict[str, Any]], dict[str, Any] | None]:
    """Render slides with Presenton's original JSON-to-HTML renderer.

    This intentionally has no python-pptx fallback.  The HTML/SVG/CSS output
    is rendered by Chromium, which is the same visual path used by Presenton.
    """
    template_id = str(settings.get("templateId") or "general").strip().lower()
    template_path = (_TEMPLATES / template_id / "template.json").resolve()
    if not template_path.is_relative_to(_TEMPLATES.resolve()) or not template_path.is_file():
        raise ValueError(f"Presenton template not found: {template_id}")
    if not _SCRIPT.is_file():
        raise RuntimeError("Presenton HTML runtime is not built; run npm run build in presenton_runtime")

    task_id = f"presenton_{uuid.uuid4().hex}"
    runtime_root = Path(os.getenv("AI_EXPORT_ROOT", str(_ROOT / "data" / "ai-exports"))) / "presenton-runtime"
    runtime_root.mkdir(parents=True, exist_ok=True)
    input_path = runtime_root / f"{task_id}.json"
    payload = {
        "taskId": task_id,
        "title": title,
        "templateRoot": str((_TEMPLATES / template_id).resolve()),
        "template": json.loads(template_path.read_text(encoding="utf-8")),
        "slides": slides,
        "outputRoot": str(runtime_root.resolve()),
    }
    input_path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    try:
        completed = subprocess.run(
            ["node", str(_RUNTIME / "src" / "render.mjs"), str(input_path)],
            cwd=str(_RUNTIME),
            check=True,
            capture_output=True,
            text=True,
            timeout=int(os.getenv("PPT_PRESENTON_RENDER_TIMEOUT_SECONDS", "300")),
        )
        result = json.loads(completed.stdout.strip().splitlines()[-1])
    except (OSError, subprocess.SubprocessError, ValueError) as exc:
        raise RuntimeError(f"Presenton Chromium render failed: {exc}") from exc
    finally:
        input_path.unlink(missing_ok=True)

    try:
        pdf_source = Path(result["pdfPath"])
        pdf_path = generated_exporter._new_export_path(generated_exporter._slugify(title), "pdf")
        shutil.copyfile(pdf_source, pdf_path)
        pdf_attachment = generated_exporter._attachment_for_file(
            pdf_path, "ai_ppt_generation_tool", "PDF", display_stem=title
        )
        pdf_attachment.update({"type": "pdf", "templateId": template_id})

        previews: list[dict[str, Any]] = []
        for index in range(1, int(result.get("slideCount") or 0) + 1):
            source = runtime_root / f"{task_id}-{index}.png"
            if not source.is_file():
                continue
            target = generated_exporter._new_export_path(f"{generated_exporter._slugify(title)}-{index}", "png")
            shutil.copyfile(source, target)
            item = generated_exporter._attachment_for_file(
                target, "ai_ppt_generation_tool", "PNG", display_stem=f"{title}-{index}"
            )
            item.update({"type": "preview", "slideIndex": index})
            previews.append(item)
        pptx_attachment = None
        if result.get("pptxPath"):
            source = Path(str(result["pptxPath"])).resolve()
            if source.is_file():
                target = generated_exporter._new_export_path(generated_exporter._slugify(title), "pptx")
                shutil.copyfile(source, target)
                pptx_attachment = generated_exporter._attachment_for_file(
                    target, "ai_ppt_generation_tool", "PPTX", display_stem=title
                )
                pptx_attachment.update({"type": "pptx", "templateId": template_id})
        return pdf_attachment, pdf_path, previews, pptx_attachment
    finally:
        # The generated artifacts have already been copied into the protected
        # export store; remove renderer scratch files to avoid unbounded growth.
        for scratch_path in runtime_root.glob(f"{task_id}*"):
            try:
                scratch_path.unlink(missing_ok=True)
            except OSError:
                pass


__all__ = ["render_presenton_html"]
