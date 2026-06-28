import json
import os
import re
import tempfile
import uuid
import zipfile
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional
from urllib.parse import quote
from xml.sax.saxutils import escape

from docx import Document

EXPORT_ROOT = Path(os.getenv("AI_SERVER_EXPORT_ROOT") or (Path(tempfile.gettempdir()) / "agent-a3-ai-exports")).resolve()
EXPORT_URL_PATH = "/uploads/ai-exports"
PUBLIC_BASE_URL = os.getenv("AI_SERVER_PUBLIC_BASE_URL", "http://localhost:8000").rstrip("/")

EXPORTABLE_MARKDOWN_ANSWER_TYPES = {
    "markdown",
    "ppt_outline",
    "ppt_layout",
    "ppt_review",
    "tool_result",
}
EXPORTABLE_MARKDOWN_AGENTS = {
    "textbook_knowledge_agent",
    "meeting_controller_agent",
    "meeting_transcription_agent",
    "meeting_summary_agent",
    "meeting_member_analysis_agent",
    "meeting_resource_recommendation_agent",
    "meeting_voice_broadcast_agent",
    "ppt_outline_agent",
    "ppt_layout_agent",
    "ppt_review_agent",
}
EXPORTABLE_DIAGRAM_ANSWER_TYPES = {
    "mermaid_mindmap",
    "mermaid_flowchart",
    "mermaid_activity_flowchart",
    "mermaid_architecture",
}


@dataclass
class GeneratedExportResult:
    attachments: List[Dict[str, Any]] = field(default_factory=list)
    diagnostics: Dict[str, Any] = field(default_factory=dict)


def export_generated_answer(answer: str, answer_type: str, metadata: Optional[Dict[str, Any]] = None) -> GeneratedExportResult:
    metadata = metadata or {}
    agent = str(metadata.get("executedAgent") or metadata.get("targetAgent") or metadata.get("agentName") or "").strip()
    normalized_type = str(answer_type or metadata.get("answerType") or "").strip()
    content = str(answer or "").strip()
    if not content:
        return GeneratedExportResult(diagnostics={"skipped": True, "reason": "empty_answer"})
    if not metadata.get("allowGeneratedExportTool") and (agent == "generated_export_tools" or normalized_type == "document_export"):
        return GeneratedExportResult(diagnostics={"skipped": True, "reason": "already_exported_by_generated_export_tools"})

    if normalized_type == "question_bank" or agent.startswith("textbook_question_"):
        payload = _parse_json_object(content)
        if not payload or not isinstance(payload.get("questions"), list):
            return GeneratedExportResult(diagnostics={"skipped": True, "reason": "invalid_question_bank_json"})
        return _export_question_bank(payload, metadata)

    if normalized_type in EXPORTABLE_DIAGRAM_ANSWER_TYPES or _extract_mermaid_code(content):
        return _export_diagram_source(content, metadata)

    if _should_export_markdown(normalized_type, agent, metadata, content):
        return _export_markdown_content(content, metadata)

    return GeneratedExportResult(diagnostics={"skipped": True, "reason": "not_exportable_answer_type"})


def _should_export_markdown(answer_type: str, agent: str, metadata: Dict[str, Any], content: str) -> bool:
    requested = str(metadata.get("requestedOutputType") or metadata.get("preferredOutputType") or "").strip().lower()
    if requested in {"document", "file", "docx", "word", "excel", "md", "markdown"}:
        return True
    if agent in EXPORTABLE_MARKDOWN_AGENTS:
        return True
    if answer_type in EXPORTABLE_MARKDOWN_ANSWER_TYPES and _looks_like_markdown(content):
        return True
    return False


def _export_question_bank(payload: Dict[str, Any], metadata: Dict[str, Any]) -> GeneratedExportResult:
    title = _title_from_metadata(metadata, "题库导出")
    slug = _slugify(title or "question-bank")
    markdown = _question_bank_to_markdown(payload, title)
    rows = _question_bank_rows(payload)
    paths = [
        _write_text_file(slug, "md", markdown),
        _write_question_bank_docx(slug, title, payload),
        _write_xlsx(slug, "题库", rows),
    ]
    attachments = [
        _attachment_for_file(paths[0], "markdown_export_tool", "Markdown"),
        _attachment_for_file(paths[1], "docx_export_tool", "Word 文档"),
        _attachment_for_file(paths[2], "excel_export_tool", "Excel 表格"),
        _attachment_for_file(_write_archive(slug, paths), "content_archive_tool", "打包文件"),
    ]
    return GeneratedExportResult(
        attachments=attachments,
        diagnostics={
            "skipped": False,
            "contentKind": "question_bank",
            "questionCount": len(payload.get("questions") or []),
            "producedFormats": ["md", "docx", "xlsx", "zip"],
        },
    )


def _export_markdown_content(content: str, metadata: Dict[str, Any]) -> GeneratedExportResult:
    title = _title_from_markdown(content) or _title_from_metadata(metadata, "知识整理")
    slug = _slugify(title or "knowledge")
    rows = _markdown_rows(content)
    paths = [
        _write_text_file(slug, "md", content),
        _write_markdown_docx(slug, title, content),
    ]
    if rows:
        paths.append(_write_xlsx(slug, "知识清单", rows))
    attachments = [
        _attachment_for_file(paths[0], "markdown_export_tool", "Markdown"),
        _attachment_for_file(paths[1], "docx_export_tool", "Word 文档"),
    ]
    if rows:
        attachments.append(_attachment_for_file(paths[2], "excel_export_tool", "Excel 表格"))
    attachments.append(_attachment_for_file(_write_archive(slug, paths), "content_archive_tool", "打包文件"))
    return GeneratedExportResult(
        attachments=attachments,
        diagnostics={
            "skipped": False,
            "contentKind": "markdown_content",
            "itemCount": max(len(rows) - 1, 0),
            "producedFormats": ["md", "docx", "xlsx", "zip"] if rows else ["md", "docx", "zip"],
        },
    )


def _export_diagram_source(content: str, metadata: Dict[str, Any]) -> GeneratedExportResult:
    title = _title_from_metadata(metadata, "图表源码")
    slug = _slugify(title or "diagram-source")
    mermaid_code = _extract_mermaid_code(content) or str(content or "").strip()
    markdown = f"# {title or '图表源码'}\n\n```mermaid\n{mermaid_code}\n```\n"
    paths = [
        _write_text_file(slug, "mmd", mermaid_code.strip() + "\n"),
        _write_text_file(f"{slug}-mermaid", "md", markdown),
    ]
    attachments = [
        _attachment_for_file(paths[0], "diagram_source_export_tool", "Mermaid 源文件"),
        _attachment_for_file(paths[1], "markdown_export_tool", "Markdown"),
        _attachment_for_file(_write_archive(slug, paths), "content_archive_tool", "打包文件"),
    ]
    return GeneratedExportResult(
        attachments=attachments,
        diagnostics={
            "skipped": False,
            "contentKind": "diagram_source",
            "producedFormats": ["mmd", "md", "zip"],
        },
    )


def _write_text_file(slug: str, ext: str, content: str) -> Path:
    path = _new_export_path(slug, ext)
    path.write_text(content, encoding="utf-8")
    return path


def _write_markdown_docx(slug: str, title: str, content: str) -> Path:
    path = _new_export_path(slug, "docx")
    doc = Document()
    doc.add_heading(title or "知识整理", level=1)
    in_code_block = False
    for raw_line in str(content or "").splitlines():
        line = raw_line.rstrip()
        stripped = line.strip()
        if not stripped:
            continue
        if stripped.startswith("```"):
            in_code_block = not in_code_block
            continue
        if in_code_block:
            paragraph = doc.add_paragraph()
            paragraph.add_run(line).font.name = "Consolas"
            continue
        heading = re.match(r"^(#{1,6})\s+(.+)$", stripped)
        if heading:
            level = min(len(heading.group(1)) + 1, 4)
            doc.add_heading(_clean_inline_markdown(heading.group(2)), level=level)
            continue
        if re.match(r"^[-*]\s+", stripped):
            doc.add_paragraph(_clean_inline_markdown(re.sub(r"^[-*]\s+", "", stripped)), style="List Bullet")
            continue
        if re.match(r"^\d+[.)]\s+", stripped):
            doc.add_paragraph(_clean_inline_markdown(re.sub(r"^\d+[.)]\s+", "", stripped)), style="List Number")
            continue
        doc.add_paragraph(_clean_inline_markdown(stripped))
    doc.save(path)
    return path


def _write_question_bank_docx(slug: str, title: str, payload: Dict[str, Any]) -> Path:
    path = _new_export_path(slug, "docx")
    doc = Document()
    doc.add_heading(title or "题库导出", level=1)
    questions = payload.get("questions") if isinstance(payload.get("questions"), list) else []
    for index, question in enumerate(questions, start=1):
        if not isinstance(question, dict):
            continue
        qtype = str(question.get("type") or "").strip()
        stem = str(question.get("stem") or question.get("title") or "").strip()
        doc.add_heading(f"{index}. {stem or '未命名题目'}", level=2)
        doc.add_paragraph(f"题型：{qtype or '-'}")
        if question.get("score") is not None:
            doc.add_paragraph(f"分值：{question.get('score')}")
        if question.get("difficulty"):
            doc.add_paragraph(f"难度：{question.get('difficulty')}")
        options = _extract_options(question)
        if options:
            doc.add_paragraph("选项：")
            for option in options:
                doc.add_paragraph(f"{option.get('key') or ''}. {option.get('text') or ''}".strip(), style="List Bullet")
        answer = _format_answer(question.get("answer"))
        if answer:
            doc.add_paragraph(f"答案：{answer}")
        analysis = str(question.get("analysis") or question.get("explanation") or "").strip()
        if analysis:
            doc.add_paragraph(f"解析：{analysis}")
        knowledge = _join_values(question.get("knowledgePoints"))
        if knowledge:
            doc.add_paragraph(f"知识点：{knowledge}")
        tags = _join_values(question.get("tags"))
        if tags:
            doc.add_paragraph(f"标签：{tags}")
    doc.save(path)
    return path


def _write_xlsx(slug: str, sheet_name: str, rows: List[List[Any]]) -> Path:
    path = _new_export_path(slug, "xlsx")
    safe_rows = rows if rows else [["内容"]]
    sheet_xml = _sheet_xml(safe_rows)
    escaped_sheet_name = escape(_safe_sheet_name(sheet_name), {'"': "&quot;"})
    workbook_xml = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="{escaped_sheet_name}" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""
    workbook_rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    root_rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
    content_types = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""
    styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("[Content_Types].xml", content_types)
        archive.writestr("_rels/.rels", root_rels)
        archive.writestr("xl/workbook.xml", workbook_xml)
        archive.writestr("xl/_rels/workbook.xml.rels", workbook_rels)
        archive.writestr("xl/worksheets/sheet1.xml", sheet_xml)
        archive.writestr("xl/styles.xml", styles)
    return path


def _write_archive(slug: str, paths: List[Path]) -> Path:
    path = _new_export_path(f"{slug}-bundle", "zip")
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for item in paths:
            if item and item.exists():
                archive.write(item, arcname=item.name)
    return path


def _sheet_xml(rows: List[List[Any]]) -> str:
    row_xml = []
    for row_index, row in enumerate(rows, start=1):
        cells = []
        for col_index, value in enumerate(row, start=1):
            cell_ref = f"{_column_name(col_index)}{row_index}"
            cells.append(f'<c r="{cell_ref}" t="inlineStr"><is><t>{escape(_cell_text(value))}</t></is></c>')
        row_xml.append(f'<row r="{row_index}">{"".join(cells)}</row>')
    return f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews><sheetView workbookViewId="0"/></sheetViews>
  <sheetData>{"".join(row_xml)}</sheetData>
</worksheet>"""


def _question_bank_to_markdown(payload: Dict[str, Any], title: str) -> str:
    lines = [f"# {title or '题库导出'}", ""]
    questions = payload.get("questions") if isinstance(payload.get("questions"), list) else []
    for index, question in enumerate(questions, start=1):
        if not isinstance(question, dict):
            continue
        lines.append(f"## {index}. {question.get('stem') or question.get('title') or '未命名题目'}")
        lines.append("")
        lines.append(f"- 题型：{question.get('type') or '-'}")
        if question.get("score") is not None:
            lines.append(f"- 分值：{question.get('score')}")
        if question.get("difficulty"):
            lines.append(f"- 难度：{question.get('difficulty')}")
        options = _extract_options(question)
        if options:
            lines.append("- 选项：")
            lines.extend(f"  - {option.get('key') or ''}. {option.get('text') or ''}".rstrip() for option in options)
        answer = _format_answer(question.get("answer"))
        if answer:
            lines.append(f"- 答案：{answer}")
        analysis = str(question.get("analysis") or question.get("explanation") or "").strip()
        if analysis:
            lines.append(f"- 解析：{analysis}")
        knowledge = _join_values(question.get("knowledgePoints"))
        if knowledge:
            lines.append(f"- 知识点：{knowledge}")
        tags = _join_values(question.get("tags"))
        if tags:
            lines.append(f"- 标签：{tags}")
        lines.append("")
    missing = payload.get("missingInfo")
    if isinstance(missing, list) and missing:
        lines.append("## 缺失信息")
        lines.extend(f"- {item}" for item in missing)
    return "\n".join(lines).strip() + "\n"


def _question_bank_rows(payload: Dict[str, Any]) -> List[List[Any]]:
    rows = [[
        "序号",
        "题型",
        "题干",
        "选项",
        "答案",
        "解析",
        "分值",
        "难度",
        "知识点",
        "标签",
    ]]
    questions = payload.get("questions") if isinstance(payload.get("questions"), list) else []
    for index, question in enumerate(questions, start=1):
        if not isinstance(question, dict):
            continue
        options = "；".join(f"{item.get('key') or ''}.{item.get('text') or ''}".strip(".") for item in _extract_options(question))
        rows.append([
            index,
            question.get("type") or "",
            question.get("stem") or question.get("title") or "",
            options,
            _format_answer(question.get("answer")),
            question.get("analysis") or question.get("explanation") or "",
            question.get("score") if question.get("score") is not None else "",
            question.get("difficulty") or "",
            _join_values(question.get("knowledgePoints")),
            _join_values(question.get("tags")),
        ])
    return rows


def _markdown_rows(content: str) -> List[List[Any]]:
    rows = [["序号", "类型", "层级", "内容"]]
    for line in str(content or "").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("```"):
            continue
        heading = re.match(r"^(#{1,6})\s+(.+)$", stripped)
        if heading:
            rows.append([len(rows), "标题", len(heading.group(1)), _clean_inline_markdown(heading.group(2))])
            continue
        if re.match(r"^[-*]\s+", stripped):
            rows.append([len(rows), "要点", "", _clean_inline_markdown(re.sub(r"^[-*]\s+", "", stripped))])
            continue
        if re.match(r"^\d+[.)]\s+", stripped):
            rows.append([len(rows), "步骤", "", _clean_inline_markdown(re.sub(r"^\d+[.)]\s+", "", stripped))])
            continue
        if len(stripped) <= 240:
            rows.append([len(rows), "正文", "", _clean_inline_markdown(stripped)])
        if len(rows) >= 201:
            break
    return rows if len(rows) > 1 else []


def _attachment_for_file(path: Path, tool_name: str, format_label: str) -> Dict[str, Any]:
    ext = path.suffix.lower().lstrip(".")
    attachment_type = "docx" if ext == "docx" else "excel" if ext == "xlsx" else "file"
    return {
        "url": _public_url(path),
        "name": path.name,
        "type": attachment_type,
        "ext": ext,
        "toolName": tool_name,
        "formatLabel": format_label,
        "source": "generated_content_export",
    }


def _new_export_path(slug: str, ext: str) -> Path:
    EXPORT_ROOT.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.utcnow().strftime("%Y%m%d%H%M%S")
    filename = f"{timestamp}-{uuid.uuid4().hex[:8]}-{_slugify(slug)}.{ext}"
    return EXPORT_ROOT / filename


def _public_url(path: Path) -> str:
    return f"{PUBLIC_BASE_URL}{EXPORT_URL_PATH}/{quote(path.name)}"


def _parse_json_object(content: str) -> Dict[str, Any]:
    raw = str(content or "").strip()
    if raw.startswith("```"):
        raw = re.sub(r"^```(?:json)?", "", raw, flags=re.IGNORECASE).strip()
        raw = re.sub(r"```$", "", raw).strip()
    try:
        parsed = json.loads(raw)
        return parsed if isinstance(parsed, dict) else {}
    except Exception:
        return {}


def _title_from_markdown(content: str) -> str:
    for line in str(content or "").splitlines():
        match = re.match(r"^#\s+(.+)$", line.strip())
        if match:
            return _clean_inline_markdown(match.group(1))[:60]
    return ""


def _title_from_metadata(metadata: Dict[str, Any], fallback: str) -> str:
    for key in ("sourceTitle", "title", "topic", "intent"):
        value = str(metadata.get(key) or "").strip()
        if value:
            return value[:60]
    agent = str(metadata.get("executedAgent") or metadata.get("targetAgent") or metadata.get("agentName") or "").strip()
    return _agent_title(agent) or fallback


def _agent_title(agent: str) -> str:
    titles = {
        "textbook_knowledge_agent": "教材知识整理",
        "meeting_summary_agent": "会议纪要",
        "meeting_resource_recommendation_agent": "学习资源推荐",
        "ppt_outline_agent": "PPT 大纲",
        "ppt_layout_agent": "PPT 布局方案",
        "ppt_review_agent": "PPT 审查报告",
    }
    if agent.startswith("textbook_question_"):
        return "题库导出"
    return titles.get(agent, "")


def _extract_options(question: Dict[str, Any]) -> List[Dict[str, Any]]:
    body = question.get("body") if isinstance(question.get("body"), dict) else {}
    options = body.get("options") or question.get("options") or []
    if not isinstance(options, list):
        return []
    normalized = []
    for option in options:
        if isinstance(option, dict):
            normalized.append({
                "key": str(option.get("key") or option.get("label") or "").strip(),
                "text": str(option.get("text") or option.get("content") or "").strip(),
            })
        else:
            normalized.append({"key": "", "text": str(option or "").strip()})
    return normalized


def _format_answer(answer: Any) -> str:
    if answer is None:
        return ""
    if isinstance(answer, dict):
        for key in (
            "correctOption",
            "correctOptions",
            "correctKey",
            "correctKeys",
            "correct",
            "blanks",
            "referenceAnswer",
            "answerPoints",
            "keyPoints",
            "finalAnswer",
            "steps",
            "proofSteps",
            "conclusion",
            "solutionOutline",
            "referenceSolution",
            "testCases",
            "expectedResult",
            "pairs",
            "orderedKeys",
            "value",
            "values",
        ):
            if key in answer:
                return _join_values(answer.get(key))
        return json.dumps(answer, ensure_ascii=False)
    return _join_values(answer)


def _join_values(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, list):
        return "、".join(str(item) for item in value if str(item).strip())
    if isinstance(value, dict):
        return json.dumps(value, ensure_ascii=False)
    return str(value).strip()


def _looks_like_markdown(content: str) -> bool:
    return bool(re.search(r"^#{1,6}\s+|^[-*]\s+|^\d+[.)]\s+", content, flags=re.MULTILINE))


def _extract_mermaid_code(content: str) -> str:
    match = re.search(r"```mermaid\s*([\s\S]*?)```", str(content or ""), flags=re.IGNORECASE)
    if match:
        return match.group(1).strip()
    stripped = str(content or "").strip()
    if re.match(r"^(mindmap|flowchart|graph|sequenceDiagram|classDiagram|stateDiagram|erDiagram|journey|gantt)\b", stripped, flags=re.IGNORECASE):
        return stripped
    return ""


def _clean_inline_markdown(text: str) -> str:
    value = str(text or "")
    value = re.sub(r"`([^`]+)`", r"\1", value)
    value = re.sub(r"\*\*([^*]+)\*\*", r"\1", value)
    value = re.sub(r"\*([^*]+)\*", r"\1", value)
    value = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", value)
    return value.strip()


def _safe_sheet_name(value: str) -> str:
    name = re.sub(r"[\[\]:*?/\\]", "", str(value or "Sheet1")).strip() or "Sheet1"
    return name[:31]


def _cell_text(value: Any) -> str:
    text = "" if value is None else str(value)
    return re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", "", text)[:32000]


def _column_name(index: int) -> str:
    result = ""
    value = index
    while value:
        value, remainder = divmod(value - 1, 26)
        result = chr(65 + remainder) + result
    return result or "A"


def _slugify(value: str) -> str:
    text = re.sub(r"[^\w\u4e00-\u9fff-]+", "-", str(value or "").strip(), flags=re.UNICODE)
    text = re.sub(r"-+", "-", text).strip("-")
    return text[:48] or "generated-content"


__all__ = [
    "EXPORT_ROOT",
    "EXPORT_URL_PATH",
    "GeneratedExportResult",
    "export_generated_answer",
]
