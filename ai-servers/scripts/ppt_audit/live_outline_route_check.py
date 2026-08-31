"""Run a real, outline-only PPT generation check through the internal API route.

This is intentionally not a mock and intentionally does not start a server:
FastAPI TestClient exercises the same route, request validation, task queue and
polling path in-process while the model provider uses the project .env values.
It stops after the outline task and never calls slide generation or export.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path
from typing import Any


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


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser()
    parser.add_argument("--topic", default="计算机专业就业指导")
    parser.add_argument("--source-content", default="")
    parser.add_argument("--source-file", default="")
    parser.add_argument("--page-count", type=int, default=5)
    parser.add_argument("--timeout", type=int, default=420)
    parser.add_argument("--report", default="")
    parser.add_argument("--include-raw", action="store_true")
    parser.add_argument(
        "--with-content",
        action="store_true",
        help="在大纲成功后继续生成页面 UI 内容，但不渲染或导出 PPT",
    )
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[3]
    _load_dotenv(repo_root / ".env")
    server_root = repo_root / "ai-servers"
    if str(server_root) not in sys.path:
        sys.path.insert(0, str(server_root))

    source_content = args.source_content
    source_name = args.topic
    if args.source_file:
        from app.ppt_generation.source_parser import extract_source_text

        source_path = Path(args.source_file).resolve()
        source_content = extract_source_text(source_path)
        source_name = source_path.name

    from fastapi.testclient import TestClient

    from app.main import app
    from app.ppt_generation import service as ppt_service

    raw_responses: list[str] = []
    original_run_specialist_agent = ppt_service.run_specialist_agent

    def capture_model_response(*call_args: Any, **call_kwargs: Any) -> str:
        answer = original_run_specialist_agent(*call_args, **call_kwargs)
        raw_responses.append(str(answer or ""))
        return answer

    # Capture only model text for diagnosis; credentials are never part of a
    # model response and are not included in the report.
    ppt_service.run_specialist_agent = capture_model_response

    headers = {
        "Authorization": "Bearer codex-live-outline-qa",
        "X-User-Id": "codex-live-outline-qa",
        "X-AI-Internal-Token": os.getenv("AI_INTERNAL_TOKEN", "dev-internal-token-change-me-32chars"),
        "X-AI-Provider": os.getenv("LLM_PROVIDER", ""),
        "X-AI-Base-Url": os.getenv("LLM_BASE_URL", ""),
        "X-AI-Api-Key": os.getenv("LLM_API_KEY", ""),
        "X-AI-Model": os.getenv("LLM_MODEL", ""),
    }
    request_body = {
        "sourceName": source_name,
        "sourceContent": source_content or args.topic,
        "topic": args.topic,
        "outlineMode": "ai_outline",
        "pageCount": args.page_count,
        "audience": "本科生",
        "tone": "专业严谨",
    }

    started = time.perf_counter()
    with TestClient(app) as client:
        response = client.post(
            "/internal/rag/ppt-generation/outlines/tasks",
            headers=headers,
            json=request_body,
        )
        if response.status_code != 200:
            print(json.dumps({"stage": "enqueue", "status": response.status_code, "body": response.json()}, ensure_ascii=False, indent=2))
            return 2
        task_id = str(response.json().get("taskId") or "")
        if not task_id:
            print(json.dumps({"stage": "enqueue", "error": "missing taskId"}, ensure_ascii=False, indent=2))
            return 3

        final: dict[str, Any] = {}
        while time.perf_counter() - started < args.timeout:
            task_response = client.get(
                f"/internal/rag/ppt-generation/tasks/{task_id}",
                headers=headers,
            )
            if task_response.status_code != 200:
                print(json.dumps({"stage": "poll", "status": task_response.status_code, "body": task_response.json()}, ensure_ascii=False, indent=2))
                return 4
            final = task_response.json()
            if final.get("status") in {"completed", "failed", "timed_out", "cancelled"}:
                break
            time.sleep(1)

        content_final: dict[str, Any] = {}
        content_task_id = ""
        if args.with_content and final.get("status") == "completed":
            outline = final.get("outline") or {}
            content_response = client.post(
                "/internal/rag/ppt-generation/slides/tasks",
                headers=headers,
                json={
                    "outline": outline,
                    "sourceContent": source_content or args.topic,
                    "settings": {
                        "pptStyle": "general",
                        "includeVisuals": False,
                        "imageMode": "placeholder",
                    },
                    "sharedPrompt": "",
                },
            )
            if content_response.status_code != 200:
                content_final = {
                    "status": "enqueue_failed",
                    "error": content_response.json(),
                }
            else:
                content_task_id = str(content_response.json().get("taskId") or "")
                while time.perf_counter() - started < args.timeout:
                    task_response = client.get(
                        f"/internal/rag/ppt-generation/tasks/{content_task_id}",
                        headers=headers,
                    )
                    if task_response.status_code != 200:
                        content_final = {
                            "status": "poll_failed",
                            "error": task_response.json(),
                        }
                        break
                    content_final = task_response.json()
                    if content_final.get("status") in {"completed", "failed", "timed_out", "cancelled"}:
                        break
                    time.sleep(1)

    outline = final.get("outline") or {}
    report = {
        "testEnvironment": "real .env model + FastAPI route + in-process task queue; no server port started",
        "provider": os.getenv("LLM_PROVIDER", ""),
        "model": os.getenv("LLM_MODEL", ""),
        "fallbackModels": [item.strip() for item in os.getenv("LLM_MODEL_FALLBACKS", "").split(",") if item.strip()],
        "input": request_body,
        "elapsedSeconds": round(time.perf_counter() - started, 2),
        "taskId": task_id,
        "status": final.get("status"),
        "stage": final.get("stage"),
        "progress": final.get("progress"),
        "generationMode": final.get("generationMode") or outline.get("generationMode"),
        "warnings": final.get("warnings") or outline.get("warnings") or [],
        "items": outline.get("items") or final.get("items") or [],
        "outlineMarkdown": outline.get("outlineMarkdown") or final.get("outlineMarkdown") or "",
        "rawModelResponses": raw_responses if args.include_raw else [],
        "error": final.get("error"),
        "contentTaskId": content_task_id,
        "contentStatus": content_final.get("status"),
        "contentStage": content_final.get("stage"),
        "contentCompletedSlides": content_final.get("completedSlides"),
        "contentError": content_final.get("error"),
        "contentWarnings": content_final.get("warnings") or [],
    }
    if args.report:
        Path(args.report).resolve().write_text(
            json.dumps(report, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["status"] == "completed" else 5


if __name__ == "__main__":
    raise SystemExit(main())
