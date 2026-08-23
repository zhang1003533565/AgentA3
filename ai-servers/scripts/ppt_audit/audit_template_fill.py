"""Run a deterministic content-fill/geometry regression over every template layout.

This is intentionally model-only: it does not call the LLM, modify template
JSON, or export a PPTX. It catches the class of regressions where a semantic
slot is cleared, a repeated group loses an occurrence, or an inferred
connector target becomes empty after content filling.

Usage (from ``ai-servers``):
    uv run python scripts/ppt_audit/audit_template_fill.py --out audit-dir
"""

from __future__ import annotations

import argparse
import copy
import json
from collections import Counter
from pathlib import Path
from typing import Any

try:
    from app.ppt_generation.layout_validator import validate_slide
    from app.ppt_generation.service import _fill_layout_with_slide_text
    from app.ppt_generation.template_model import parse_slide_layout
except ModuleNotFoundError:  # pragma: no cover - supports direct script execution
    import sys

    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
    from app.ppt_generation.layout_validator import validate_slide
    from app.ppt_generation.service import _fill_layout_with_slide_text
    from app.ppt_generation.template_model import parse_slide_layout


def _default_template_root() -> Path:
    return Path(__file__).resolve().parents[2] / "app" / "ppt_generation" / "assets" / "templates"


def audit_template_fills(root: Path) -> dict[str, Any]:
    rows: list[dict[str, Any]] = []
    issue_counts: Counter[str] = Counter()
    parse_errors = 0

    for template_dir in sorted(path for path in root.iterdir() if path.is_dir()):
        template_file = template_dir / "template.json"
        if not template_file.is_file():
            continue
        payload = json.loads(template_file.read_text(encoding="utf-8"))
        for layout in payload.get("layouts") or []:
            if not isinstance(layout, dict):
                continue
            layout_id = str(layout.get("id") or "")
            try:
                model = parse_slide_layout(layout)
                ui = _fill_layout_with_slide_text(
                    layout,
                    {
                        "title": "T",
                        "content": [
                            "A",
                            "B",
                            "C",
                            "D",
                            "E",
                            "F",
                        ],
                    },
                    {},
                )
                result = validate_slide({"components": copy.deepcopy(ui.get("components") or [])}, model)
                errors = [issue.error_type for issue in result.issues if issue.severity == "error"]
                warnings = [issue.error_type for issue in result.issues if issue.severity == "warning"]
                structural_errors = [code for code in errors if code != "TEXT_OVERFLOW"]
                for code in errors:
                    issue_counts[code] += 1
                rows.append(
                    {
                        "templateId": template_dir.name,
                        "layoutId": layout_id,
                        "connectorTargets": len(model.connector_targets),
                        "effectiveBoxes": len(model.effective_boxes),
                        "errors": errors,
                        "structuralErrors": structural_errors,
                        "contentFitErrors": errors.count("TEXT_OVERFLOW"),
                        "warnings": warnings,
                        "passed": not structural_errors,
                    }
                )
            except Exception as exc:  # pragma: no cover - defensive audit boundary
                parse_errors += 1
                issue_counts["AUDIT_EXCEPTION"] += 1
                rows.append(
                    {
                        "templateId": template_dir.name,
                        "layoutId": layout_id,
                        "connectorTargets": 0,
                        "effectiveBoxes": 0,
                        "errors": ["AUDIT_EXCEPTION"],
                        "structuralErrors": ["AUDIT_EXCEPTION"],
                        "contentFitErrors": 0,
                        "warnings": [],
                        "passed": False,
                        "detail": f"{type(exc).__name__}: {exc}",
                    }
                )

    return {
        "schemaVersion": 1,
        "root": str(root.resolve()),
        "summary": {
            "layouts": len(rows),
            "passed": sum(1 for row in rows if row["passed"]),
            "failed": sum(1 for row in rows if not row["passed"]),
            "parseErrors": parse_errors,
            "contentFitIssueCount": sum(row["contentFitErrors"] for row in rows),
            "contentFitIssueLayouts": sum(1 for row in rows if row["contentFitErrors"]),
            "issueCounts": dict(sorted(issue_counts.items())),
        },
        "layouts": rows,
    }


def _markdown(report: dict[str, Any]) -> str:
    summary = report["summary"]
    lines = [
        "# PPT Template Fill Audit",
        "",
        f"- layouts: {summary['layouts']}",
        f"- passed: {summary['passed']}",
        f"- structural failed: {summary['failed']}",
        f"- parse errors: {summary['parseErrors']}",
        f"- content-fit issue count (before repair engine): {summary['contentFitIssueCount']}",
        f"- content-fit issue layouts: {summary['contentFitIssueLayouts']}",
        f"- issue counts: {summary['issueCounts']}",
        "",
        "| Template | Layout | Connector targets | Structural errors | Fit errors | Warnings | Passed |",
        "| --- | --- | ---: | --- | ---: | --- | --- |",
    ]
    for row in report["layouts"]:
        lines.append(
            "| {templateId} | {layoutId} | {connectorTargets} | {structuralErrors} | {contentFitErrors} | {warnings} | {passed} |".format(
                **row
            )
        )
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=_default_template_root())
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()
    report = audit_template_fills(args.root)
    if args.out:
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "template-fill.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        (args.out / "template-fill.md").write_text(_markdown(report), encoding="utf-8")
        print(json.dumps({"out": str(args.out.resolve()), "summary": report["summary"]}, ensure_ascii=False))
    else:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["summary"]["parseErrors"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
