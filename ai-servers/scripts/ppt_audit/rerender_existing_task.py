"""Re-export an existing completed PPT task through a selected PPTX mode.

This diagnostic keeps the AI output and template payload fixed so that
fidelity, editable, and hybrid exports can be compared without regenerating
content. It copies the resulting attachments into the requested output folder
and writes a small manifest for later audit scripts.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any, Mapping


def _copy_attachment(attachment: Mapping[str, Any], destination: Path) -> None:
    from app.rag.document_conversion import generated_exporter

    exported = generated_exporter.open_generated_export(
        str(attachment.get("storageKey") or ""),
        str(attachment.get("internalCapability") or ""),
    )
    try:
        destination.write_bytes(exported.stream.read())
    finally:
        exported.stream.close()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--task", required=True, help="completed task-final.json")
    parser.add_argument("--out", required=True, help="output directory")
    parser.add_argument(
        "--mode",
        choices=("fidelity", "editable", "hybrid"),
        required=True,
        help="PPTX export mode to test",
    )
    parser.add_argument(
        "--backend",
        choices=("docker", "local"),
        default=os.getenv("PPTX_EXPORT_BACKEND") or "docker",
        help="PPTX exporter backend (default: docker)",
    )
    parser.add_argument(
        "--cjk-font",
        default=os.getenv("PPTX_CJK_FONT") or "Microsoft YaHei",
        help="explicit CJK font for native PPTX text (default: Microsoft YaHei)",
    )
    args = parser.parse_args()

    task_path = Path(args.task).resolve()
    output = Path(args.out).resolve()
    output.mkdir(parents=True, exist_ok=True)
    task = json.loads(task_path.read_text(encoding="utf-8"))
    slides = task.get("slides") or []
    if not slides:
        raise ValueError(f"task has no slides: {task_path}")

    repo_root = Path(__file__).resolve().parents[3]
    server_root = repo_root / "ai-servers"
    if str(server_root) not in sys.path:
        sys.path.insert(0, str(server_root))
    os.environ["AI_EXPORT_ROOT"] = str(output / "export-store")
    os.environ["PPTX_EXPORT_BACKEND"] = args.backend
    os.environ["PPTX_EXPORT_RENDER_MODE"] = args.mode
    os.environ["PPTX_CJK_FONT"] = args.cjk_font
    os.environ["PRESENTON_ENABLE_PPTX"] = "true"
    os.environ.setdefault("PPT_TEMPLATE_PREVIEW_WARMUP", "0")
    os.environ["PPT_PRESENTON_RENDER_TIMEOUT_SECONDS"] = "600"
    os.environ["PPT_QA_REPORT_DIR"] = str(output / "qa")

    from app.ppt_generation.presenton_html_renderer import render_presenton_html

    settings = dict(task.get("settings") or {})
    settings["pptxOnly"] = True
    title = str(task.get("sourceName") or task.get("title") or "presentation")
    _, _, previews, pptx = render_presenton_html(slides, title, settings)
    if not isinstance(pptx, Mapping):
        raise RuntimeError("renderer did not return a PPTX attachment")

    pptx_path = output / f"{args.mode}.pptx"
    _copy_attachment(pptx, pptx_path)
    preview_paths = []
    for preview in previews:
        if not isinstance(preview, Mapping):
            continue
        index = int(preview.get("slideIndex") or len(preview_paths) + 1)
        preview_path = output / f"preview-{index}.png"
        _copy_attachment(preview, preview_path)
        preview_paths.append(str(preview_path))

    manifest = {
        "mode": args.mode,
        "task": str(task_path),
        "slideCount": len(slides),
        "pptx": str(pptx_path),
        "previews": preview_paths,
        "sourceTaskId": task.get("taskId"),
        "contentQuality": task.get("contentQuality"),
    }
    (output / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(manifest, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
