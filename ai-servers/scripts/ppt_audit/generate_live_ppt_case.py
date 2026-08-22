"""Run one real end-to-end AI PPT generation case for repeatable QA.

This script deliberately uses the configured project LLM settings, then calls
the same service methods used by the API: outline -> slides -> export task.
It never prints the API key. The output directory contains only diagnostics and
the newly generated artifact for the selected run.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path
from typing import Any, Mapping


DEFAULT_SOURCE = """栈与队列是常见的线性数据结构。栈遵循后进先出（LIFO），插入和删除通常都在栈顶进行，常用于函数调用、递归和撤销操作。队列遵循先进先出（FIFO），元素从队尾进入、从队首移出，适用于任务调度、打印服务和消息处理。

顺序队列可以用数组和队首、队尾指针实现；如果出队后搬移全部元素，出队可能退化为 O(n)。循环队列通过取模复用数组空间，能够让入队和出队保持 O(1)。广度优先搜索（BFS）也利用队列按层级遍历图或树。

选择栈还是队列，应根据问题是“后进先出”还是“先来先到”，同时结合复杂度、内存和并发约束。"""


def _load_dotenv(path: Path) -> None:
    if not path.is_file():
        return
    for raw in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


def _write_json(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def _config_from_env():
    from app.model_providers.runtime_config import build_llm_runtime_config

    return build_llm_runtime_config(
        provider=os.getenv("LLM_PROVIDER", ""),
        base_url=os.getenv("LLM_BASE_URL", ""),
        api_key=os.getenv("LLM_API_KEY", ""),
        model=os.getenv("LLM_MODEL", ""),
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True, help="run output directory")
    parser.add_argument("--source", default=DEFAULT_SOURCE)
    parser.add_argument("--source-name", default="栈与队列 LIFO/FIFO 机制及应用")
    parser.add_argument("--template-id", default="general")
    parser.add_argument("--page-count", type=int, default=5)
    parser.add_argument("--include-visuals", action="store_true")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[3]
    output = Path(args.out).resolve()
    output.mkdir(parents=True, exist_ok=True)
    _load_dotenv(repo_root / ".env")
    server_root = repo_root / "ai-servers"
    if str(server_root) not in sys.path:
        sys.path.insert(0, str(server_root))

    # These are bounded test-run defaults. They do not change production
    # configuration and make the run reproducible across local invocations.
    os.environ.setdefault("PPT_TEMPLATE_PREVIEW_WARMUP", "0")
    os.environ.setdefault("PPTX_EXPORT_BACKEND", "docker")
    os.environ.setdefault("PPTX_EXPORT_RENDER_MODE", "fidelity")
    os.environ.setdefault("PPT_QA_REPORT_DIR", str(output / "qa"))
    os.environ.setdefault("PPT_STRUCTURE_MAX_OUTPUT_TOKENS", "4000")
    os.environ.setdefault("PPT_CONTENT_MAX_OUTPUT_TOKENS", "8000")
    os.environ.setdefault("PPT_CONTENT_BATCH_SIZE", "2")
    os.environ.setdefault("PPT_CONTENT_MAX_WORKERS", "1")
    os.environ.setdefault("PPT_TASK_TIMEOUT_SECONDS", "1800")

    # Import after environment setup because service constants are read at
    # import time.
    from app.ppt_generation.service import ppt_generation_service
    from app.rag.document_conversion import generated_exporter

    config = _config_from_env()
    user_id = "codex-live-qa"
    outline_request = {
        "sourceName": args.source_name,
        "sourceContent": args.source,
        "outlineMode": "ai_outline",
        "pageCount": args.page_count,
        "topic": "",
        "audience": "本科生",
        "tone": "专业严谨",
    }
    print("OUTLINE_START", flush=True)
    outline = ppt_generation_service.generate_outline(outline_request, config, user_id)
    _write_json(output / "outline.json", outline)
    print("OUTLINE_OK", len(outline.get("items") or []), flush=True)

    settings = {
        "templateId": args.template_id,
        "imageMode": "ai" if args.include_visuals else "placeholder",
        "includeVisuals": bool(args.include_visuals),
        "pptxOnly": True,
    }
    print("SLIDES_START", flush=True)
    slides_result = ppt_generation_service.generate_slides(
        {
            "sourceName": args.source_name,
            "sourceContent": args.source,
            "outline": outline,
            "settings": settings,
            "sharedPrompt": "完整生成课堂讲解型多页PPT，保留大纲逻辑并展开关键原理、复杂度和应用。",
            "audience": "本科生",
            "tone": "专业严谨",
        },
        config,
        user_id,
    )
    _write_json(output / "slides.json", slides_result)
    slides = slides_result.get("slides") or []
    print(
        "SLIDES_OK",
        len(slides),
        "warnings",
        len(slides_result.get("warnings") or []),
        "quality",
        (slides_result.get("contentQuality") or {}).get("status", ""),
        flush=True,
    )

    task_request = {
        "sourceName": args.source_name,
        "outline": outline,
        "slides": slides,
        "sharedPrompt": "完整生成课堂讲解型多页PPT，保留大纲逻辑并展开关键原理、复杂度和应用。",
        "settings": settings,
        "contentQuality": slides_result.get("contentQuality") or {},
        "exportFormats": ["pptx"],
    }
    print("TASK_START", flush=True)
    queued = ppt_generation_service.create_task(user_id, task_request, config)
    task_id = str(queued.get("taskId") or "")
    print("TASK_ID", task_id, flush=True)
    deadline = time.time() + 30 * 60
    task: Mapping[str, Any] = queued
    while time.time() < deadline:
        task = ppt_generation_service.get_task(user_id, task_id)
        print(
            "STATUS",
            task.get("status"),
            task.get("stage"),
            task.get("progress"),
            flush=True,
        )
        if str(task.get("status") or "") in {"completed", "failed", "cancelled", "timed_out"}:
            break
        time.sleep(5)
    _write_json(output / "task-final.json", task)
    if str(task.get("status") or "") != "completed":
        print("TASK_FAILED", json.dumps(task.get("error"), ensure_ascii=False), flush=True)
        return 2

    attachments = task.get("attachments") or []
    pptx = next((item for item in attachments if str(item.get("type") or "") == "pptx"), None)
    if not isinstance(pptx, Mapping):
        print("PPTX_MISSING", flush=True)
        return 3
    exported = generated_exporter.open_generated_export(
        str(pptx.get("storageKey") or ""),
        str(pptx.get("internalCapability") or ""),
    )
    try:
        payload = exported.stream.read()
    finally:
        exported.stream.close()
    final_path = output / "live-ai-generated.pptx"
    final_path.write_bytes(payload)
    print("ARTIFACT", str(final_path), "SLIDES", len(slides), "BYTES", len(payload), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
