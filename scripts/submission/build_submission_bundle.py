#!/usr/bin/env python3
"""Build a deterministic, evidence-aware AgentA3 submission archive."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import sys
from typing import Iterable, NamedTuple
import zipfile


DEFAULT_OUTPUT = Path("artifacts/submission/AgentA3-提交包.zip")
FINAL_PPTX = Path("artifacts/submission/AgentA3-中国软件杯演示.pptx")
FINAL_DOCX = Path("output/docx/AgentA3-需求设计与开发说明书.docx")
FINAL_PDF = Path("output/pdf/AgentA3-需求设计与开发说明书.pdf")
VIDEO_SCRIPT = Path("docs/submission/7-minute-demo-script.md")
REQUIRED_ARTIFACTS = (FINAL_PPTX, FINAL_DOCX, FINAL_PDF, VIDEO_SCRIPT)


class SubmissionStatuses(NamedTuple):
    knowledge: str
    factual: str
    load: str
    video: str


def should_include(path: Path) -> bool:
    raw = path.as_posix()
    while raw.startswith("./"):
        raw = raw[2:]
    logical = PurePosixPath(raw)
    parts = logical.parts
    if not parts or logical.is_absolute() or ".." in parts:
        return False

    excluded_parts = {
        ".git",
        ".idea",
        ".vscode",
        ".pytest_cache",
        ".venv",
        "venv",
        "node_modules",
        "target",
        "dist",
        "unpackage",
        "__pycache__",
    }
    if any(part in excluded_parts for part in parts):
        return False
    if logical.name in {".env", ".DS_Store", "Thumbs.db"}:
        return False
    if logical.suffix in {".pyc", ".pyo"}:
        return False
    if logical.name.endswith(".inspect.ndjson"):
        return False
    if len(parts) >= 3 and parts[:2] == ("artifacts", "submission"):
        if logical.suffix == ".zip" or logical.name.endswith(".zip.sha256"):
            return False
        if parts[2] == "AgentA3-中国软件杯演示":
            return False
    if len(parts) >= 3 and parts[:3] == ("docs", "project-document", "output"):
        return False
    return True


def _read_json(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return {}
    return value if isinstance(value, dict) else {}


def read_submission_statuses(root: Path) -> SubmissionStatuses:
    knowledge = _read_json(root / "artifacts/knowledge-base/python-course/manifest.json")
    factual = _read_json(root / "artifacts/verification/python-course-factual.json")
    load = _read_json(root / "artifacts/verification/python-course-load.json")
    videos = [
        path
        for path in (root / "artifacts/submission").glob("*.mp4")
        if path.is_file() and path.stat().st_size > 0
    ]
    factual_status = str(factual.get("status") or "missing")
    if factual_status == "completed" and factual.get("passed") is True:
        factual_status = "completed_passed"
    load_status = str(load.get("status") or "missing")
    if load_status == "completed" and load.get("passed") is True:
        load_status = "completed_passed"
    return SubmissionStatuses(
        knowledge=str(knowledge.get("status") or "missing"),
        factual=factual_status,
        load=load_status,
        video="present" if videos else "missing",
    )


def final_gate_failures(statuses: SubmissionStatuses) -> list[str]:
    failures: list[str] = []
    if statuses.knowledge != "ready":
        failures.append("Python 课程知识包尚未 ready")
    if statuses.factual != "completed_passed":
        failures.append("30 题事实评测尚未 completed 且 passed")
    if statuses.load != "completed_passed":
        failures.append("5×50 压测尚未 completed 且 passed")
    if statuses.video != "present":
        failures.append("7 分钟演示视频尚未提供")
    return failures


def bundle_gate_failures(
    statuses: SubmissionStatuses,
    *,
    allow_pending_evidence: bool,
) -> list[str]:
    return [] if allow_pending_evidence else final_gate_failures(statuses)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_manifest(
    *,
    root: Path,
    files: Iterable[Path],
    revision: str,
    branch: str,
    generated_at: str,
    statuses: SubmissionStatuses,
    rehearsal: bool,
) -> dict:
    entries = []
    for logical in sorted(set(files), key=lambda item: item.as_posix()):
        absolute = root / logical
        entries.append(
            {
                "path": logical.as_posix(),
                "bytes": absolute.stat().st_size,
                "sha256": _sha256(absolute),
            }
        )
    return {
        "schemaVersion": 1,
        "project": "AgentA3",
        "competition": "第十五届中国软件杯 A3",
        "git": {"revision": revision, "branch": branch},
        "generatedAt": generated_at,
        "rehearsal": rehearsal,
        "statuses": statuses._asdict(),
        "files": entries,
    }


def _git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), *args],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return result.stdout.strip()


def _tracked_files(root: Path) -> list[Path]:
    result = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return [Path(value.decode("utf-8")) for value in result.stdout.split(b"\0") if value]


def collect_files(root: Path) -> list[Path]:
    candidates = set(_tracked_files(root))
    candidates.update(path for path in REQUIRED_ARTIFACTS if (root / path).is_file())
    candidates.update(
        path.relative_to(root)
        for path in (root / "artifacts/submission").glob("*.mp4")
        if path.is_file()
    )

    selected: list[Path] = []
    for logical in sorted(candidates, key=lambda value: value.as_posix()):
        absolute = root / logical
        if not should_include(logical) or not absolute.is_file():
            continue
        if absolute.is_symlink():
            raise ValueError(f"提交包拒绝符号链接：{logical.as_posix()}")
        selected.append(logical)
    return selected


def _required_artifact_failures(root: Path) -> list[str]:
    return [
        f"缺少提交物：{path.as_posix()}"
        for path in REQUIRED_ARTIFACTS
        if not (root / path).is_file() or (root / path).stat().st_size <= 0
    ]


def _zip_timestamp(epoch: int) -> tuple[int, int, int, int, int, int]:
    import datetime as dt

    moment = dt.datetime.fromtimestamp(max(epoch, 315532800), tz=dt.timezone.utc)
    return (moment.year, moment.month, moment.day, moment.hour, moment.minute, moment.second)


def write_bundle(
    *,
    root: Path,
    output: Path,
    files: list[Path],
    manifest: dict,
    epoch: int,
) -> tuple[Path, Path]:
    absolute_output = output if output.is_absolute() else root / output
    absolute_output.parent.mkdir(parents=True, exist_ok=True)
    temporary = absolute_output.with_suffix(absolute_output.suffix + ".tmp")
    timestamp = _zip_timestamp(epoch)

    with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for logical in files:
            info = zipfile.ZipInfo(logical.as_posix(), timestamp)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, (root / logical).read_bytes())
        manifest_info = zipfile.ZipInfo("SUBMISSION-MANIFEST.json", timestamp)
        manifest_info.compress_type = zipfile.ZIP_DEFLATED
        manifest_info.external_attr = 0o100644 << 16
        archive.writestr(
            manifest_info,
            (json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8"),
        )
    temporary.replace(absolute_output)
    checksum_path = absolute_output.with_suffix(absolute_output.suffix + ".sha256")
    checksum_path.write_text(
        f"{_sha256(absolute_output)}  {absolute_output.name}\n",
        encoding="utf-8",
    )
    return absolute_output, checksum_path


def _parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--check-only", action="store_true")
    parser.add_argument(
        "--allow-pending-evidence",
        action="store_true",
        help="构建排练包；允许知识包、在线评测、压测或视频仍待完成。",
    )
    parser.add_argument(
        "--allow-dirty",
        action="store_true",
        help="仅用于排练；最终提交包必须来自干净工作树。",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv or sys.argv[1:])
    root = Path(__file__).resolve().parents[2]
    statuses = read_submission_statuses(root)
    failures = _required_artifact_failures(root)
    failures.extend(
        bundle_gate_failures(statuses, allow_pending_evidence=args.allow_pending_evidence)
    )

    dirty = _git(root, "status", "--porcelain=v1", "--untracked-files=all")
    if dirty and not args.allow_dirty:
        failures.append("Git 工作树不干净；最终提交包必须对应唯一提交")
    if failures:
        print(json.dumps({"status": "blocked", "failures": failures, "evidence": statuses._asdict()}, ensure_ascii=False, indent=2))
        return 2

    revision = _git(root, "rev-parse", "HEAD")
    branch = _git(root, "branch", "--show-current") or "DETACHED"
    epoch_text = os.getenv("SOURCE_DATE_EPOCH") or _git(root, "show", "-s", "--format=%ct", "HEAD")
    epoch = int(epoch_text)
    import datetime as dt

    generated_at = dt.datetime.fromtimestamp(epoch, tz=dt.timezone.utc).isoformat().replace("+00:00", "Z")
    files = collect_files(root)
    rehearsal = bool(args.allow_dirty or args.allow_pending_evidence)
    manifest = build_manifest(
        root=root,
        files=files,
        revision=revision,
        branch=branch,
        generated_at=generated_at,
        statuses=statuses,
        rehearsal=rehearsal,
    )

    if args.check_only:
        print(json.dumps({"status": "ready", "fileCount": len(files), "rehearsal": rehearsal, "evidence": statuses._asdict()}, ensure_ascii=False, indent=2))
        return 0

    output, checksum = write_bundle(
        root=root,
        output=args.output,
        files=files,
        manifest=manifest,
        epoch=epoch,
    )
    print(
        json.dumps(
            {
                "status": "created",
                "output": output.relative_to(root).as_posix() if output.is_relative_to(root) else str(output),
                "checksum": checksum.relative_to(root).as_posix() if checksum.is_relative_to(root) else str(checksum),
                "fileCount": len(files),
                "rehearsal": rehearsal,
                "evidence": statuses._asdict(),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
