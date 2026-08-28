"""Audit the structure contract of every embedded PPT template.

This audit is intentionally independent of the LLM and of PPTX export. It
checks whether a layout exposes enough stable structure for content filling:
named/repeated text slots, dynamic layout containers, and vector candidates
that may act as connectors. It does not modify templates or production code.

Usage:
    uv run python scripts/ppt_audit/audit_template_structure.py
    uv run python scripts/ppt_audit/audit_template_structure.py --out audit-dir
"""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from pathlib import Path
from typing import Any, Iterable

try:
    from app.ppt_generation.template_model import parse_slide_layout
except ModuleNotFoundError:  # pragma: no cover - supports direct script execution
    import sys

    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
    from app.ppt_generation.template_model import parse_slide_layout


VECTOR_TYPES = {"vector", "line", "divider"}
TARGET_KEYS = (
    "target",
    "targetSlot",
    "target_slot",
    "connectsTo",
    "connects_to",
    "sourceSlot",
    "source_slot",
    "anchor",
    "bindTo",
    "bind_to",
)
TEXT_TYPES = {"text", "text-list"}


def _default_template_root() -> Path:
    return Path(__file__).resolve().parents[2] / "app" / "ppt_generation" / "assets" / "templates"


def _walk(value: Any, *, component: str, path: str) -> Iterable[dict[str, Any]]:
    if isinstance(value, list):
        for index, item in enumerate(value):
            yield from _walk(item, component=component, path=f"{path}[{index}]")
        return
    if not isinstance(value, dict):
        return

    node = dict(value)
    node["_path"] = path
    node["_component"] = component
    yield node

    next_component = component or str(value.get("id") or "")
    for key in ("components", "elements", "children"):
        if key in value:
            yield from _walk(value.get(key), component=next_component, path=f"{path}.{key}")
    if "child" in value:
        yield from _walk(value.get("child"), component=component, path=f"{path}.child")


def _has_target_binding(node: dict[str, Any]) -> bool:
    return any(node.get(key) not in (None, "", [], {}) for key in TARGET_KEYS)


def _is_connector_candidate(node: dict[str, Any]) -> bool:
    node_type = str(node.get("type") or "").strip().lower()
    if node_type in {"line", "divider"}:
        return True
    if node_type != "vector":
        return False
    points = node.get("points")
    return isinstance(points, list) and len(points) == 2 and not bool(node.get("closed"))


def _text_role_hint(name: str) -> str:
    key = name.lower().replace("-", "_")
    if re.search(r"(^|_)(section|card|callout|feature|item|step|timeline|milestone)_(heading|title|label|name)($|_)", key):
        return "repeated_heading_candidate"
    if re.search(r"(^|_)(section|card|callout|feature|item|step|timeline|milestone)_(body|description|detail|text|copy)($|_)", key):
        return "repeated_body_candidate"
    if re.search(r"(^|_)(title|headline|heading|subject)($|_)", key):
        return "title_candidate"
    if re.search(r"(^|_)(body|description|detail|copy|text)($|_)", key):
        return "body_candidate"
    return "unclassified_text"


def _audit_layout(template_id: str, layout: dict[str, Any]) -> dict[str, Any]:
    nodes = list(_walk(layout.get("components") or [], component="", path="components"))
    model = parse_slide_layout(layout)
    inferred_by_index = {relation.connector_index: relation for relation in model.connector_targets}
    text_nodes = [node for node in nodes if str(node.get("type") or "") in TEXT_TYPES]
    connector_nodes = [node for node in nodes if _is_connector_candidate(node)]
    named_texts = Counter(str(node.get("name") or "") for node in text_nodes if str(node.get("name") or ""))
    repeated_slots = [
        {"name": name, "count": count}
        for name, count in sorted(named_texts.items())
        if count > 1
    ]
    heading_names = {
        str(node.get("name") or "")
        for node in text_nodes
        if _text_role_hint(str(node.get("name") or "")) == "repeated_heading_candidate"
    }
    body_names = {
        str(node.get("name") or "")
        for node in text_nodes
        if _text_role_hint(str(node.get("name") or "")) == "repeated_body_candidate"
    }
    dynamic_nodes = [
        node
        for node in nodes
        if str(node.get("type") or "").strip().lower() in {"flex", "group"}
    ]
    connector_bindings = []
    for index, node in enumerate(connector_nodes):
        relation = inferred_by_index.get(index)
        explicit = _has_target_binding(node)
        connector_bindings.append(
            {
                "name": str(node.get("name") or ""),
                "path": node["_path"],
                "component": node["_component"],
                "bound": explicit or relation is not None,
                "bindingType": "explicit" if explicit else "inferred" if relation else "unresolved",
                "inferredTarget": (
                    {
                        "name": relation.target_name,
                        "occurrence": relation.target_index,
                        "semanticRole": relation.target_semantic_role,
                        "distance": relation.distance,
                    }
                    if relation is not None
                    else None
                ),
            }
        )
    findings: list[dict[str, Any]] = []
    for connector in connector_bindings:
        if connector["bindingType"] == "unresolved":
            findings.append(
                {
                    "code": "UNRESOLVED_CONNECTOR_CANDIDATE",
                    "severity": "warning",
                    "message": "线状 vector 没有显式或高置信度推断的文字目标，无法保证填充后仍指向正确内容。",
                    "details": connector,
                }
            )
    if repeated_slots:
        findings.append(
            {
                "code": "REPEATED_TEXT_SLOTS",
                "severity": "info",
                "message": "布局包含重复文字槽位，必须保留 occurrence 和父级关系。",
                "details": {"slots": repeated_slots},
            }
        )
    if dynamic_nodes:
        findings.append(
            {
                "code": "DYNAMIC_LAYOUT_CONTAINER",
                "severity": "info",
                "message": "布局包含 flex/group，静态 x/y 不能单独代表最终位置。",
                "details": {
                    "count": len(dynamic_nodes),
                    "types": sorted(Counter(str(node.get("type") or "") for node in dynamic_nodes)),
                },
            }
        )
    if connector_nodes and heading_names and body_names:
        findings.append(
            {
                "code": "CONNECTOR_WITH_HEADING_BODY_PAIR",
                "severity": "warning",
                "message": "连接线同时面对重复标题和正文槽位，必须声明实际目标，不能依赖名称猜测。",
                "details": {
                    "headingSlots": sorted(heading_names),
                    "bodySlots": sorted(body_names),
                },
            }
        )

    return {
        "templateId": template_id,
        "layoutId": str(layout.get("id") or ""),
        "description": str(layout.get("description") or ""),
        "nodeCounts": {
            "texts": len(text_nodes),
            "connectorCandidates": len(connector_nodes),
            "dynamicContainers": len(dynamic_nodes),
        },
        "textSlots": [
            {
                "name": str(node.get("name") or ""),
                "occurrence": index,
                "component": str(node.get("_component") or ""),
                "roleHint": _text_role_hint(str(node.get("name") or "")),
            }
            for index, node in enumerate(text_nodes)
        ],
        "connectorCandidates": connector_bindings,
        "inferredConnectorCount": sum(
            1 for connector in connector_bindings if connector["bindingType"] == "inferred"
        ),
        "repeatedSlots": repeated_slots,
        "findings": findings,
    }


def audit_templates(root: Path) -> dict[str, Any]:
    templates: list[dict[str, Any]] = []
    layouts: list[dict[str, Any]] = []
    for template_dir in sorted(path for path in root.iterdir() if path.is_dir()):
        template_file = template_dir / "template.json"
        if not template_file.is_file():
            continue
        payload = json.loads(template_file.read_text(encoding="utf-8"))
        template_layouts = [
            _audit_layout(template_dir.name, layout)
            for layout in payload.get("layouts") or []
            if isinstance(layout, dict)
        ]
        layouts.extend(template_layouts)
        templates.append(
            {
                "templateId": template_dir.name,
                "layoutCount": len(template_layouts),
                "connectorLayouts": sum(1 for item in template_layouts if item["nodeCounts"]["connectorCandidates"]),
                "repeatedSlotLayouts": sum(1 for item in template_layouts if item["repeatedSlots"]),
                "dynamicLayouts": sum(1 for item in template_layouts if item["nodeCounts"]["dynamicContainers"]),
                "warningCount": sum(
                    1
                    for item in template_layouts
                    for finding in item["findings"]
                    if finding["severity"] == "warning"
                ),
            }
        )
    findings = [
        {
            "templateId": item["templateId"],
            "layoutId": item["layoutId"],
            **finding,
        }
        for item in layouts
        for finding in item["findings"]
    ]
    return {
        "schemaVersion": 1,
        "root": str(root.resolve()),
        "summary": {
            "templates": len(templates),
            "layouts": len(layouts),
            "connectorLayouts": sum(1 for item in layouts if item["nodeCounts"]["connectorCandidates"]),
            "connectorCandidates": sum(item["nodeCounts"]["connectorCandidates"] for item in layouts),
            "explicitConnectorBindings": sum(
                sum(1 for connector in item["connectorCandidates"] if connector["bindingType"] == "explicit")
                for item in layouts
            ),
            "inferredConnectorBindings": sum(item["inferredConnectorCount"] for item in layouts),
            "unresolvedConnectorCandidates": sum(
                1 for finding in findings if finding["code"] == "UNRESOLVED_CONNECTOR_CANDIDATE"
            ),
            "repeatedSlotLayouts": sum(1 for item in layouts if item["repeatedSlots"]),
            "dynamicLayouts": sum(1 for item in layouts if item["nodeCounts"]["dynamicContainers"]),
            "warningFindings": sum(1 for finding in findings if finding["severity"] == "warning"),
        },
        "templates": templates,
        "layouts": layouts,
        "findings": findings,
    }


def _markdown(report: dict[str, Any]) -> str:
    summary = report["summary"]
    lines = [
        "# PPT Template Structure Audit",
        "",
        f"- templates: {summary['templates']}",
        f"- layouts: {summary['layouts']}",
        f"- connector candidates: {summary['connectorCandidates']}",
        f"- explicit connector bindings: {summary['explicitConnectorBindings']}",
        f"- inferred connector bindings: {summary['inferredConnectorBindings']}",
        f"- unresolved connector candidates: {summary['unresolvedConnectorCandidates']}",
        f"- repeated-slot layouts: {summary['repeatedSlotLayouts']}",
        f"- dynamic layouts: {summary['dynamicLayouts']}",
        f"- warning findings: {summary['warningFindings']}",
        "",
        "## Templates",
        "",
        "| Template | Layouts | Connector layouts | Repeated slots | Dynamic layouts | Warnings |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for item in report["templates"]:
        lines.append(
            "| {templateId} | {layoutCount} | {connectorLayouts} | {repeatedSlotLayouts} | {dynamicLayouts} | {warningCount} |".format(**item)
        )
    lines.extend(["", "## Findings", ""])
    for finding in report["findings"]:
        lines.append(
            f"- `{finding['severity']}` `{finding['code']}` "
            f"`{finding['templateId']}/{finding['layoutId']}`: {finding['message']}"
        )
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=_default_template_root())
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()
    report = audit_templates(args.root)
    if args.out:
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "template-structure.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        (args.out / "template-structure.md").write_text(_markdown(report), encoding="utf-8")
        print(json.dumps({"out": str(args.out.resolve()), "summary": report["summary"]}, ensure_ascii=False))
    else:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
