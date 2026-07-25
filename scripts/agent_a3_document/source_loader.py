"""Parse reviewed Markdown sources into document-generation blocks."""

from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal
from pathlib import Path, PurePosixPath
import re


BLOCK_KINDS = frozenset(
    {
        "heading",
        "paragraph",
        "bullet",
        "numbered",
        "table",
        "figure",
        "callout",
        "code",
        "toc",
        "page_break",
        "section_break",
    }
)
EVIDENCE_STATUSES = frozenset(
    {"implemented", "partial", "planned", "known-limit"}
)
_EVIDENCE_HEADER = ("ID", "Claim", "Status", "Evidence", "Final wording")


@dataclass(frozen=True)
class Block:
    kind: str
    text: str = ""
    level: int = 0
    attrs: dict[str, str] = field(default_factory=dict)
    rows: tuple[tuple[str, ...], ...] = ()

    def __post_init__(self) -> None:
        if self.kind not in BLOCK_KINDS:
            raise ValueError(f"unsupported block kind: {self.kind}")


@dataclass(frozen=True)
class EvidenceRow:
    id: str
    claim: str
    status: str
    evidence: str
    final_wording: str


_AUTO_TOC = "<!-- AUTO_TOC -->"
_PAGE_BREAK = "<!-- PAGE_BREAK -->"
_END_CALLOUT = "<!-- END_CALLOUT -->"
_SECTION_BREAK_RE = re.compile(
    r'^<!-- SECTION_BREAK chapter="(?P<chapter>[^"]+)" -->$'
)
_FIGURE_RE = re.compile(
    r'^<!-- FIGURE src="(?P<src>[^"]+)" caption="(?P<caption>[^"]+)" '
    r'width_cm="(?P<width_cm>[^"]+)" -->$'
)
_CALLOUT_RE = re.compile(
    r'^<!-- CALLOUT type="(?P<type>note|risk|evidence)" '
    r'title="(?P<title>[^"]+)" -->$'
)
_HEADING_RE = re.compile(r"^(?P<marks>#{1,6})\s+(?P<text>.+)$")
_BULLET_RE = re.compile(r"^(?P<indent> *)(?:[-+*])\s+(?P<text>.+)$")
_NUMBERED_RE = re.compile(r"^(?P<indent> *)\d+[.)]\s+(?P<text>.+)$")
_TABLE_SEPARATOR_RE = re.compile(r"^:?-{3,}:?$")
_BACKTICK_VALUE_RE = re.compile(r"`([^`]+)`")
_POSITIVE_DECIMAL_RE = re.compile(r"(?:\d+(?:\.\d+)?|\.\d+)")
_WINDOWS_DRIVE_RE = re.compile(r"^[A-Za-z]:")


def extract_evidence_rows(path: Path) -> list[EvidenceRow]:
    """Extract and validate the evidence index's canonical Markdown table."""

    lines = path.read_text(encoding="utf-8").splitlines()
    header_index = next(
        (
            index
            for index, line in enumerate(lines)
            if line.strip().startswith("|")
            and _split_table_row(line.strip()) == _EVIDENCE_HEADER
        ),
        None,
    )
    if header_index is None:
        raise ValueError(f"{path.name}: evidence table header not found")

    separator_index = header_index + 1
    if separator_index >= len(lines):
        raise _source_error(path.name, header_index + 1, "malformed evidence table")
    separator = _split_table_row(lines[separator_index].strip())
    if len(separator) != len(_EVIDENCE_HEADER) or not all(
        _TABLE_SEPARATOR_RE.fullmatch(cell) for cell in separator
    ):
        raise _source_error(path.name, separator_index + 1, "malformed evidence table")

    rows: list[EvidenceRow] = []
    seen_ids: set[str] = set()
    index = separator_index + 1
    while index < len(lines) and lines[index].strip().startswith("|"):
        cells = _split_table_row(lines[index].strip())
        if len(cells) != len(_EVIDENCE_HEADER):
            raise _source_error(path.name, index + 1, "malformed evidence row")
        row = EvidenceRow(*cells)
        if row.status not in EVIDENCE_STATUSES:
            raise _source_error(path.name, index + 1, "unknown evidence status")
        if row.id in seen_ids:
            raise _source_error(path.name, index + 1, "duplicate evidence ID")
        _validate_evidence_paths(row.evidence, path.name, index + 1)
        seen_ids.add(row.id)
        rows.append(row)
        index += 1

    if not rows:
        raise _source_error(path.name, separator_index + 2, "evidence table is empty")
    return rows


def load_source(source_dir: Path) -> list[Block]:
    """Load the eleven numerically prefixed chapter sources in lexical order."""

    paths = sorted(source_dir.glob("[0-9][0-9]-*.md"))
    if len(paths) != 11:
        raise ValueError(f"expected 11 chapter sources, found {len(paths)}")
    return [
        block
        for path in paths
        for block in parse_markdown(
            path.read_text(encoding="utf-8"), source_name=path.name
        )
    ]


def parse_markdown(text: str, *, source_name: str = "<memory>") -> list[Block]:
    """Parse the deliberately small Markdown dialect used by the document source."""

    lines = text.splitlines()
    blocks: list[Block] = []
    index = 0

    while index < len(lines):
        raw_line = lines[index]
        line = raw_line.strip()
        line_number = index + 1

        if not line:
            index += 1
            continue

        if line == _AUTO_TOC:
            blocks.append(Block(kind="toc"))
            index += 1
            continue

        if line == _PAGE_BREAK:
            blocks.append(Block(kind="page_break"))
            index += 1
            continue

        section_match = _SECTION_BREAK_RE.fullmatch(line)
        if section_match:
            blocks.append(
                Block(kind="section_break", text=section_match.group("chapter"))
            )
            index += 1
            continue

        figure_match = _FIGURE_RE.fullmatch(line)
        if figure_match:
            attrs = figure_match.groupdict()
            _validate_figure_width(
                attrs["width_cm"], source_name=source_name, line_number=line_number
            )
            blocks.append(Block(kind="figure", attrs=attrs))
            index += 1
            continue

        callout_match = _CALLOUT_RE.fullmatch(line)
        if callout_match:
            callout_text, index = _parse_callout(
                lines,
                start_index=index,
                source_name=source_name,
            )
            blocks.append(
                Block(
                    kind="callout",
                    text=callout_text,
                    attrs=callout_match.groupdict(),
                )
            )
            continue

        if line.startswith("<!--"):
            raise _source_error(source_name, line_number, "unknown directive")

        heading_match = _HEADING_RE.fullmatch(line)
        if heading_match:
            blocks.append(
                Block(
                    kind="heading",
                    text=heading_match.group("text"),
                    level=len(heading_match.group("marks")),
                )
            )
            index += 1
            continue

        bullet_match = _BULLET_RE.fullmatch(raw_line)
        if bullet_match:
            blocks.append(
                Block(
                    kind="bullet",
                    text=bullet_match.group("text"),
                    level=_list_level(bullet_match.group("indent")),
                )
            )
            index += 1
            continue

        numbered_match = _NUMBERED_RE.fullmatch(raw_line)
        if numbered_match:
            blocks.append(
                Block(
                    kind="numbered",
                    text=numbered_match.group("text"),
                    level=_list_level(numbered_match.group("indent")),
                )
            )
            index += 1
            continue

        if line.startswith("```"):
            code_text, language, index = _parse_fenced_code(
                lines,
                start_index=index,
                source_name=source_name,
            )
            attrs = {"language": language} if language else {}
            blocks.append(Block(kind="code", text=code_text, attrs=attrs))
            continue

        if line.startswith("|"):
            rows, index = _parse_table(
                lines,
                start_index=index,
                source_name=source_name,
            )
            blocks.append(Block(kind="table", rows=rows))
            continue

        paragraph_lines = [line]
        index += 1
        while index < len(lines):
            next_raw = lines[index]
            next_line = next_raw.strip()
            if not next_line or _starts_block(next_raw):
                break
            paragraph_lines.append(next_line)
            index += 1
        blocks.append(Block(kind="paragraph", text="\n".join(paragraph_lines)))

    return blocks


def _parse_callout(
    lines: list[str], *, start_index: int, source_name: str
) -> tuple[str, int]:
    content_lines: list[str] = []
    index = start_index + 1

    while index < len(lines):
        line = lines[index].strip()
        if line == _END_CALLOUT:
            return _join_paragraphs(content_lines), index + 1
        if line.startswith("<!--"):
            raise _source_error(source_name, index + 1, "unknown directive")
        content_lines.append(lines[index])
        index += 1

    raise _source_error(source_name, start_index + 1, "unclosed callout")


def _parse_fenced_code(
    lines: list[str], *, start_index: int, source_name: str
) -> tuple[str, str, int]:
    opening = lines[start_index].strip()
    language = opening[3:].strip()
    content_lines: list[str] = []
    index = start_index + 1

    while index < len(lines):
        if lines[index].strip() == "```":
            return "\n".join(content_lines), language, index + 1
        content_lines.append(lines[index])
        index += 1

    raise _source_error(source_name, start_index + 1, "unclosed code fence")


def _parse_table(
    lines: list[str], *, start_index: int, source_name: str
) -> tuple[tuple[tuple[str, ...], ...], int]:
    index = start_index
    table_lines: list[str] = []
    while index < len(lines) and lines[index].strip().startswith("|"):
        table_lines.append(lines[index].strip())
        index += 1

    parsed_rows = [_split_table_row(line) for line in table_lines]
    column_count = len(parsed_rows[0]) if parsed_rows else 0
    valid_separator = (
        len(parsed_rows) >= 2
        and len(parsed_rows[1]) == column_count
        and all(_TABLE_SEPARATOR_RE.fullmatch(cell) for cell in parsed_rows[1])
    )
    matching_widths = column_count > 0 and all(
        len(row) == column_count for row in parsed_rows
    )
    if not valid_separator or not matching_widths:
        raise _source_error(source_name, start_index + 1, "malformed table")

    return tuple((parsed_rows[0], *parsed_rows[2:])), index


def _split_table_row(line: str) -> tuple[str, ...]:
    return tuple(cell.strip() for cell in line.strip().strip("|").split("|"))


def _validate_evidence_paths(evidence: str, source_name: str, line_number: int) -> None:
    references = _BACKTICK_VALUE_RE.findall(evidence)
    if not references:
        raise _source_error(source_name, line_number, "evidence path is required")
    for reference in references:
        normalized = PurePosixPath(reference)
        if (
            normalized.is_absolute()
            or ".." in normalized.parts
            or reference.startswith("~")
            or _WINDOWS_DRIVE_RE.match(reference)
            or "://" in reference
            or "\\" in reference
        ):
            raise _source_error(
                source_name,
                line_number,
                "evidence paths must be repository-relative",
            )


def _validate_figure_width(
    width_cm: str, *, source_name: str, line_number: int
) -> None:
    if not _POSITIVE_DECIMAL_RE.fullmatch(width_cm) or Decimal(width_cm) <= 0:
        raise _source_error(
            source_name,
            line_number,
            "figure width_cm must be a positive number",
        )


def _starts_block(raw_line: str) -> bool:
    line = raw_line.strip()
    return bool(
        line.startswith("<!--")
        or line.startswith("```")
        or line.startswith("|")
        or _HEADING_RE.fullmatch(line)
        or _BULLET_RE.fullmatch(raw_line)
        or _NUMBERED_RE.fullmatch(raw_line)
    )


def _list_level(indent: str) -> int:
    return len(indent.expandtabs(2)) // 2 + 1


def _join_paragraphs(lines: list[str]) -> str:
    paragraphs: list[str] = []
    current: list[str] = []

    for raw_line in lines:
        line = raw_line.strip()
        if line:
            current.append(line)
        elif current:
            paragraphs.append("\n".join(current))
            current = []
    if current:
        paragraphs.append("\n".join(current))
    return "\n\n".join(paragraphs)


def _source_error(source_name: str, line_number: int, message: str) -> ValueError:
    return ValueError(f"{source_name}:{line_number}: {message}")
