#!/usr/bin/env python3
"""Generate, structurally audit, render, and report exam-paper visual fixtures."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "AppBackend"
REFERENCE = Path("/Users/zzs/Desktop/zzs/github/wordpapergenerate/动态试卷.docx")
REFERENCE_SHA256 = "449510b906bc119345388d379cb339ea8139981f0b2ee7f4865abb6fe7f8ad72"
SKILL = Path("/Users/zzs/.codex/plugins/cache/openai-primary-runtime/documents/26.709.11516/skills/documents")
RENDERER = SKILL / "render_docx.py"
PYTHON = Path("/Users/zzs/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3")
OVERRIDE_BIN = Path("/Users/zzs/.cache/codex-runtimes/codex-primary-runtime/dependencies/bin/override")
TOKEN = re.compile(rb"%[^%]+%")
MUTABLE = {"word/document.xml", "word/header1.xml", "word/header2.xml", "word/settings.xml"}
REQUIRED = {
    "word/document.xml", "word/header1.xml", "word/header2.xml", "word/footer1.xml",
    "word/footer2.xml", "word/styles.xml", "word/numbering.xml", "word/settings.xml",
    "word/_rels/document.xml.rels",
}


def run(command: list[str], cwd: Path | None = None, env: dict[str, str] | None = None) -> None:
    print("+", " ".join(command), flush=True)
    subprocess.run(command, cwd=cwd, env=env, check=True)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def fixture_name(path: Path) -> str:
    return path.name.removesuffix(".docx")


def structural_audit(path: Path, source_template: Path) -> dict[str, object]:
    result: dict[str, object] = {"file": path.name, "sha256": sha256(path), "errors": []}
    with zipfile.ZipFile(source_template) as source, zipfile.ZipFile(path) as final:
        source_names, final_names = set(source.namelist()), set(final.namelist())
        result["entries"] = len(final_names)
        missing = sorted(REQUIRED - final_names)
        if missing:
            result["errors"].append(f"missing entries: {missing}")
        for name in sorted(REQUIRED & final_names):
            if name.endswith((".xml", ".rels")):
                try:
                    ET.fromstring(final.read(name))
                except ET.ParseError as error:
                    result["errors"].append(f"malformed {name}: {error}")
        unresolved = sorted({match.decode("utf-8", "replace") for name in final_names & MUTABLE
                             for match in TOKEN.findall(final.read(name))})
        if unresolved:
            result["errors"].append(f"unresolved tokens: {unresolved}")
        preserved_drift = []
        for name in sorted(source_names - MUTABLE):
            if name not in final_names or hashlib.sha256(source.read(name)).digest() != hashlib.sha256(final.read(name)).digest():
                preserved_drift.append(name)
        if preserved_drift:
            result["errors"].append(f"preserve-only drift: {preserved_drift}")
        document = final.read("word/document.xml").decode("utf-8")
        rels = final.read("word/_rels/document.xml.rels").decode("utf-8")
        settings = final.read("word/settings.xml").decode("utf-8")
        for relationship in ("rId8", "rId9", "rId10", "rId11"):
            if relationship not in rels:
                result["errors"].append(f"missing relationship {relationship}")
        if "w:evenAndOddHeaders" not in settings:
            result["errors"].append("missing evenAndOddHeaders")
        for marker in ("w:pgSz", "w:pgMar", "w:docGrid"):
            if marker not in document:
                result["errors"].append(f"missing page setting {marker}")
        result["binding_references"] = sum(document.count(f'r:id="{item}"') for item in ("rId8", "rId9", "rId10", "rId11"))
        result["score_tables"] = document.count("题号")
        result["grader_tables"] = document.count("阅卷人")
        result["answer_section"] = "答案解析" in document
    return result


def render(path: Path, output: Path, environment: dict[str, str]) -> dict[str, object]:
    destination = output / fixture_name(path)
    destination.mkdir(parents=True)
    run([str(PYTHON), str(RENDERER), str(path), "--output_dir", str(destination), "--emit_pdf"], env=environment)
    pages = sorted(destination.glob("page-*.png"))
    if not pages:
        raise RuntimeError(f"renderer produced no pages for {path.name}")
    pdf = destination / f"{path.stem}.pdf"
    if not pdf.exists() or pdf.read_bytes()[:4] != b"%PDF":
        raise RuntimeError(f"renderer produced invalid PDF for {path.name}")
    return {"pages": len(pages), "pngs": [str(item) for item in pages], "pdf": str(pdf)}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--skip-render", action="store_true")
    args = parser.parse_args()
    qa = (args.output_dir or Path(tempfile.mkdtemp(prefix="exam-paper-visual-qa-"))).resolve()
    fixtures, renders = qa / "fixtures", qa / "renders"
    if fixtures.exists():
        shutil.rmtree(fixtures)
    if renders.exists():
        shutil.rmtree(renders)
    fixtures.mkdir(parents=True, exist_ok=True)
    renders.mkdir(parents=True, exist_ok=True)
    if not REFERENCE.exists() or sha256(REFERENCE) != REFERENCE_SHA256:
        raise RuntimeError("authoritative reference missing or SHA-256 changed")
    source_template = BACKEND / "src/main/resources/exam-paper-template/static/document.docx"
    run(["/opt/homebrew/bin/mvn", "-q", "-Dtest=SourcePaperVisualFixtureTest",
         f"-Dexam.visual.output={fixtures}", "test"], cwd=BACKEND)

    reports: list[dict[str, object]] = []
    environment = os.environ.copy()
    environment["PATH"] = f"{OVERRIDE_BIN}:{environment.get('PATH', '')}"
    environment["TMPDIR"] = "/private/tmp"
    reference_report: dict[str, object] = {
        "file": REFERENCE.name, "sha256": sha256(REFERENCE), "errors": []
    }
    if not args.skip_render:
        reference_report.update(render(REFERENCE, renders, environment))
    for docx in sorted(fixtures.glob("*.docx")):
        audit = structural_audit(docx, source_template) if docx.name.startswith("template-") else {
            "file": docx.name, "sha256": sha256(docx), "errors": [], "mode": "SIMPLE"
        }
        if not args.skip_render:
            audit.update(render(docx, renders, environment))
        reports.append(audit)
    errors = [f"{report['file']}: {error}" for report in reports for error in report["errors"]]
    payload = {
        "qa_directory": str(qa), "reference": str(REFERENCE), "reference_sha256": REFERENCE_SHA256,
        "reference_render": reference_report, "fixtures": reports, "errors": errors,
        "verdict": "PASS" if not errors else "FAIL",
    }
    (qa / "verdict.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = ["# Exam Paper Visual QA", "", f"- Verdict: **{payload['verdict']}**",
             f"- Reference: `{REFERENCE}`", f"- QA directory: `{qa}`", "", "## Fixtures", ""]
    for report in reports:
        lines.append(f"- `{report['file']}`: {report.get('pages', 'not rendered')} pages; errors={len(report['errors'])}")
    lines += ["", "## Automated structural findings", ""]
    lines += [f"- {error}" for error in errors] or ["- None."]
    lines += ["", "## Manual visual inspection", "",
              "Open every `renders/<fixture>/page-*.png` at 100% and record the human verdict here.", ""]
    (qa / "verdict.md").write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
