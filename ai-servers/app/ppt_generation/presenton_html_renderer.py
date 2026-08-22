from __future__ import annotations

import copy
import json
import logging
import os
import shutil
import subprocess
import uuid
from pathlib import Path
from typing import Any, Mapping, Tuple
from urllib.parse import unquote, urlparse

from app.rag.document_conversion import generated_exporter
from app.ppt_generation.pptx_export_qa import validate_exported_pptx

logger = logging.getLogger(__name__)

_ROOT = Path(__file__).resolve().parents[2]
_RUNTIME = _ROOT / "presenton_runtime"
_SCRIPT = _RUNTIME / "src" / "render.mjs"
_TEMPLATES = Path(__file__).resolve().parent / "assets" / "templates"
_DOCKER_RUNTIME_ROOT = "/app/runtime"
_DOCKER_HOST_ROOT = "/app/host"


def _use_docker_pptx_export(pptx_only: bool) -> bool:
    """Use the Linux official exporter only for downloadable PPTX tasks on Windows."""
    if not pptx_only or os.name != "nt":
        return False
    backend = str(os.getenv("PPTX_EXPORT_BACKEND") or "docker").strip().lower()
    return backend in {"auto", "docker", "linux-docker"}


def _map_host_path_to_container(value: str, host_root: Path, runtime_root: Path) -> str:
    """Map absolute Windows asset paths into the read-only Docker project mount."""
    raw = str(value or "")
    host_text = str(host_root.resolve()).replace("\\", "/").rstrip("/")
    runtime_text = str(runtime_root.resolve()).replace("\\", "/").rstrip("/")
    normalized = raw.replace("\\", "/")
    for prefix, target in (
        (f"file:///{host_text}/", f"file://{_DOCKER_HOST_ROOT}/"),
        (f"file:///{runtime_text}/", f"file://{_DOCKER_RUNTIME_ROOT}/"),
        (f"{host_text}/", f"{_DOCKER_HOST_ROOT}/"),
        (f"{runtime_text}/", f"{_DOCKER_RUNTIME_ROOT}/"),
    ):
        if normalized.startswith(prefix):
            return target + normalized[len(prefix):]
    return raw


def _dockerize_payload(payload: Mapping[str, Any], template_id: str, runtime_root: Path) -> dict[str, Any]:
    host_root = _ROOT

    def transform(value: Any) -> Any:
        if isinstance(value, Mapping):
            return {key: transform(item) for key, item in value.items()}
        if isinstance(value, list):
            return [transform(item) for item in value]
        if isinstance(value, str):
            return _map_host_path_to_container(value, host_root, runtime_root)
        return value

    mapped = copy.deepcopy(transform(payload))
    # The dedicated exporter image contains the template library at /app/templates.
    mapped["templateRoot"] = f"/app/templates/{template_id}"
    mapped["outputRoot"] = _DOCKER_RUNTIME_ROOT
    return mapped


def _renderer_result(stdout: str) -> dict[str, Any]:
    lines = [line.strip() for line in str(stdout or "").splitlines() if line.strip()]
    if not lines:
        raise RuntimeError("PPTX 导出器没有返回结果")
    try:
        result = json.loads(lines[-1])
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"PPTX 导出器返回内容无效：{lines[-1][:500]}") from exc
    if not isinstance(result, dict):
        raise RuntimeError("PPTX 导出器返回结果不是对象")
    return result


def _run_docker_renderer(input_path: Path, runtime_root: Path) -> dict[str, Any]:
    image = str(os.getenv("PPTX_EXPORT_DOCKER_IMAGE") or "agent-a3-pptx-exporter:latest").strip()
    if not image:
        raise RuntimeError("未配置 PPTX Docker 导出器镜像")
    inspect = subprocess.run(
        ["docker", "image", "inspect", image],
        capture_output=True,
        text=True,
        timeout=20,
    )
    if inspect.returncode != 0:
        inspect_detail = str(inspect.stderr or inspect.stdout or "").strip()
        if any(marker in inspect_detail.lower() for marker in ("permission denied", "access is denied", "cannot connect", "docker engine")):
            raise RuntimeError(
                "Docker Desktop 当前不可用或当前用户没有 Docker 引擎权限；"
                "请启动 Docker Desktop 并确认当前 Windows 用户可访问 Docker Engine。"
            )
        raise RuntimeError(
            "PPTX Docker 导出器镜像不存在："
            f"{image}。请先在 ai-servers 目录执行 "
            "docker build -t agent-a3-pptx-exporter:latest -f Dockerfile.pptx-exporter ."
        )
    host_mount = f"type=bind,source={_ROOT.resolve()},target={_DOCKER_HOST_ROOT},readonly"
    runtime_mount = f"type=bind,source={runtime_root.resolve()},target={_DOCKER_RUNTIME_ROOT}"
    command = [
        "docker", "run", "--rm", "--init",
        "--mount", host_mount,
        "--mount", runtime_mount,
        "--env", "PRESENTON_ENABLE_PPTX=true",
        "--env", "PRESENTON_EXPORT_ROOT=/app/presenton_runtime/presentation-export",
        "--env", "BUILT_PYTHON_MODULE_PATH=/app/presenton_runtime/presentation-export/py/convert-linux-current",
        "--env", "CHROMIUM_PATH=/usr/bin/chromium",
        "--env", "PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium",
        "--env", "TEMP_DIRECTORY=/app/runtime/temp",
        "--env", "APP_DATA_DIRECTORY=/app/runtime/app-data",
        image,
        f"{_DOCKER_RUNTIME_ROOT}/{input_path.name}",
    ]
    try:
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            timeout=int(os.getenv("PPT_PRESENTON_RENDER_TIMEOUT_SECONDS", "300")),
        )
    except (OSError, subprocess.SubprocessError) as exc:
        raise RuntimeError(f"PPTX Docker 导出器无法启动：{exc}") from exc
    if completed.returncode != 0:
        detail = str(completed.stderr or completed.stdout or "").strip().splitlines()[-20:]
        raise RuntimeError(
            "PPTX Docker 官方导出失败："
            + (" | ".join(detail)[:2000] or f"exit={completed.returncode}")
        )
    return _renderer_result(completed.stdout)


def _host_path_from_renderer(value: Any, runtime_root: Path) -> Path:
    raw = str(value or "")
    parsed = urlparse(raw)
    if parsed.scheme == "file":
        raw = unquote(parsed.path)
    prefix = f"{_DOCKER_RUNTIME_ROOT}/"
    if raw.startswith(prefix):
        return runtime_root / raw[len(prefix):]
    return Path(raw)


def _detect_chromium_path() -> str | None:
    """Auto-detect a Playwright-installed Chromium executable on this machine."""
    if os.getenv("CHROMIUM_PATH") or os.getenv("PUPPETEER_EXECUTABLE_PATH"):
        return None
    playwright_root = Path(os.getenv("LOCALAPPDATA", "")) / "ms-playwright"
    try:
        chromium_dirs = sorted(
            [d for d in playwright_root.iterdir() if d.is_dir() and d.name.startswith("chromium-")],
            key=lambda d: d.name,
            reverse=True,
        ) if playwright_root.is_dir() else []
    except OSError:
        # Windows 安全策略可能禁止读取默认 Playwright 缓存目录；继续尝试
        # 系统浏览器，不能因为探测缓存失败就让整个 PPT 任务失败。
        chromium_dirs = []
    for d in chromium_dirs:
        # Playwright 旧版装到 chrome-win，新版(1.5x+)装到 chrome-win64，两者都探测
        for sub in ("chrome-win64", "chrome-win"):
            chrome_exe = d / sub / "chrome.exe"
            if chrome_exe.is_file():
                return str(chrome_exe)
    # Windows 开发机/自托管节点通常已有 Chrome，但没有 Playwright 缓存。
    # 仅使用明确的系统安装位置，不扫描用户目录，避免权限和性能问题。
    windows_candidates = [
        Path(os.getenv("PROGRAMFILES", "")) / "Google/Chrome/Application/chrome.exe",
        Path(os.getenv("PROGRAMFILES(X86)", "")) / "Google/Chrome/Application/chrome.exe",
        Path(os.getenv("LOCALAPPDATA", "")) / "Google/Chrome/Application/chrome.exe",
    ]
    for chrome_exe in windows_candidates:
        try:
            if chrome_exe.is_file():
                return str(chrome_exe)
        except OSError:
            continue
    return None


def render_presenton_html(
    slides: list[Mapping[str, Any]],
    title: str,
    settings: Mapping[str, Any],
) -> Tuple[dict[str, Any] | None, Path | None, list[dict[str, Any]], dict[str, Any] | None]:
    """Render complete Presenton UI JSON with the original JSON-to-HTML path.

    The Python layer only stages files and invokes the vendored Node runtime;
    it does not choose layouts, inject text, or rebuild component trees. This
    intentionally has no python-pptx fallback.
    """
    template_id = str(settings.get("templateId") or "general").strip().lower()
    template_path = (_TEMPLATES / template_id / "template.json").resolve()
    if not template_path.is_relative_to(_TEMPLATES.resolve()) or not template_path.is_file():
        raise ValueError(f"Presenton template not found: {template_id}")
    if not _SCRIPT.is_file():
        raise RuntimeError("Presenton HTML runtime is not built; run npm run build in presenton_runtime")

    task_id = f"presenton_{uuid.uuid4().hex}"
    preview_only = bool(settings.get("previewOnly"))
    pptx_only = bool(settings.get("pptxOnly"))
    use_docker = _use_docker_pptx_export(pptx_only)
    runtime_base = Path(os.getenv("AI_EXPORT_ROOT", str(_ROOT / "data" / "ai-exports"))) / "presenton-runtime"
    # Docker writes the official exporter's temp/data files into the bind
    # mount. Keep each task isolated so concurrent exports cannot share or
    # remove one another's files.
    runtime_root = runtime_base / task_id if use_docker else runtime_base
    runtime_root.mkdir(parents=True, exist_ok=True)
    if use_docker:
        (runtime_root / "temp").mkdir(parents=True, exist_ok=True)
        (runtime_root / "app-data" / "exports").mkdir(parents=True, exist_ok=True)
    input_path = runtime_root / f"{task_id}.json"
    payload = {
        "taskId": task_id,
        "title": title,
        "templateRoot": (
            f"/app/templates/{template_id}"
            if use_docker
            else str((_TEMPLATES / template_id).resolve())
        ),
        "template": json.loads(template_path.read_text(encoding="utf-8")),
        "slides": slides,
        "outputRoot": _DOCKER_RUNTIME_ROOT if use_docker else str(runtime_root.resolve()),
        "pngOnly": preview_only,
        "pptxOnly": pptx_only,
        # HTML-to-PPTX conversion is editable but not pixel-stable for the
        # template's nested spans, SVG decorations and browser-only CSS.
        # Fidelity mode makes the already-rendered slide image the PPTX slide
        # artwork, preventing a second layout engine from dropping content.
        "pptxExportMode": str(os.getenv("PPTX_EXPORT_RENDER_MODE") or "hybrid").strip().lower(),
    }
    if use_docker:
        payload = _dockerize_payload(payload, template_id, runtime_root)
    input_path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    try:
        if use_docker:
            logger.info("Presenton PPTX export using Linux Docker image")
            result = _run_docker_renderer(input_path, runtime_root)
        else:
            render_env = os.environ.copy()
            # The official exporter requires an explicit scratch directory on
            # desktop runs; keep it inside the task runtime root for cleanup.
            render_env.setdefault("TEMP_DIRECTORY", str(runtime_root.resolve()))
            render_env.setdefault("APP_DATA_DIRECTORY", str(runtime_root.resolve()))
            chromium_path = _detect_chromium_path()
            if chromium_path:
                # 显式覆盖而非 setdefault：外部残留的空串 CHROMIUM_PATH 会让
                # render.mjs 拿到空 executablePath，无法稳定定位 Chromium
                render_env["CHROMIUM_PATH"] = chromium_path
                # presentation-export uses Puppeteer and reads its own variable;
                # pass the same known-good Windows browser path to both runtimes.
                render_env["PUPPETEER_EXECUTABLE_PATH"] = chromium_path
                logger.info("Presenton render using Chromium: %s", chromium_path)
            completed = subprocess.run(
                ["node", str(_RUNTIME / "src" / "render.mjs"), str(input_path)],
                cwd=str(_RUNTIME),
                check=True,
                capture_output=True,
                text=True,
                env=render_env,
                timeout=int(os.getenv("PPT_PRESENTON_RENDER_TIMEOUT_SECONDS", "300")),
            )
            result = _renderer_result(completed.stdout)
    except subprocess.CalledProcessError as exc:
        # render.mjs 顶层 catch 会把结构化错误打到 stderr；拼进异常信息，
        # 排障不再只有 "returned non-zero exit status 1"
        detail = str(exc.stderr or "").strip().splitlines()[-400:]
        raise RuntimeError(
            f"Presenton Chromium render failed (exit={exc.returncode}): "
            f"{' | '.join(detail) or exc}"
        ) from exc
    except (OSError, subprocess.SubprocessError, ValueError) as exc:
        raise RuntimeError(f"Presenton Chromium render failed: {exc}") from exc
    finally:
        input_path.unlink(missing_ok=True)

    try:
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
        if not preview_only and result.get("pptxPath"):
            source = _host_path_from_renderer(result["pptxPath"], runtime_root).resolve()
            if source.is_file():
                quality = validate_exported_pptx(source)
                if not quality["passed"]:
                    raise RuntimeError(
                        "PPTX 导出质量校验失败："
                        + json.dumps(quality["errors"][:8], ensure_ascii=False)
                    )
                target = generated_exporter._new_export_path(generated_exporter._slugify(title), "pptx")
                shutil.copyfile(source, target)
                pptx_attachment = generated_exporter._attachment_for_file(
                    target, "ai_ppt_generation_tool", "PPTX", display_stem=title
                )
                pptx_attachment.update({"type": "pptx", "templateId": template_id})
        if not preview_only and pptx_only and pptx_attachment is None:
            raise RuntimeError(
                "当前 PPTX Docker 官方导出器没有返回可用文件；"
                "请确认 Docker Desktop 已启动且导出器镜像已构建。"
            )
        return None, None, previews, pptx_attachment
    finally:
        # The generated artifacts have already been copied into the protected
        # export store; remove renderer scratch files to avoid unbounded growth.
        if use_docker:
            shutil.rmtree(runtime_root, ignore_errors=True)
        else:
            for scratch_path in runtime_root.glob(f"{task_id}*"):
                try:
                    scratch_path.unlink(missing_ok=True)
                except OSError:
                    pass


__all__ = ["render_presenton_html"]
