#!/usr/bin/env python3
"""Generate the AgentA3 editable project-document deliverable."""

from __future__ import annotations

import argparse
from pathlib import Path

if __package__:
    from .agent_a3_document.docx_builder import build_document
else:
    from agent_a3_document.docx_builder import build_document


def create_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Build the AgentA3 requirements, design, and development DOCX."
    )
    parser.add_argument(
        "--source",
        required=True,
        type=Path,
        help="Directory containing the eleven reviewed Markdown sources.",
    )
    parser.add_argument(
        "--evidence",
        required=True,
        type=Path,
        help="Reviewed engineering evidence-index Markdown file.",
    )
    parser.add_argument(
        "--assets",
        required=True,
        type=Path,
        help="Directory containing the generated PNG manifest.",
    )
    parser.add_argument(
        "--output",
        required=True,
        type=Path,
        help="Destination DOCX path.",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = create_parser()
    args = parser.parse_args(argv)
    try:
        output = build_document(
            args.source,
            args.evidence,
            args.assets,
            args.output,
        )
    except (OSError, ValueError) as exc:
        parser.error(str(exc))
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
