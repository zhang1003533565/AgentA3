import base64
import binascii
import json
import re
from typing import Any, Dict, List, Mapping, Tuple
from urllib.parse import urlparse

from app.learning_workflow.models import LearningWorkflowResult, WorkflowResource
from app.rag.document_conversion import (
    export_generated_answer,
    export_presentation,
    export_python_code_lab,
)
from app.rag.document_conversion import generated_exporter
from app.safety.learning_content_guard import validate_generated_python_code


class EmptyResourceExportError(ValueError):
    pass


class MindMapContractError(ValueError):
    pass


def export_learning_resources(
    result: LearningWorkflowResult,
) -> Tuple[
    Dict[str, List[Dict[str, Any]]],
    List[Dict[str, Any]],
    List[Dict[str, Any]],
]:
    """Create real local artifacts for reviewed resources without executing code."""
    attachments_by_type: Dict[str, List[Dict[str, Any]]] = {}
    all_attachments: List[Dict[str, Any]] = []
    failures: List[Dict[str, Any]] = []
    for resource in result.resources:
        if resource.reviewStatus != "passed":
            attachments_by_type[resource.resourceType] = []
            failures.append(_review_failure(resource))
            continue
        try:
            metadata = _export_metadata(resource, result)
            exported: List[Dict[str, Any]] = []
            payload = resource.payload.model_dump(mode="json")
            if resource.resourceType == "code_lab":
                payload, sources = _prepare_code_lab_payload(resource, payload)
                for source in sources:
                    validate_generated_python_code(source)
                exported = export_python_code_lab(payload, metadata).attachments
            elif resource.resourceType == "mind_map":
                exported = _export_mind_map(resource, payload, metadata)
            elif resource.resourceType == "presentation":
                exported = [export_presentation(payload.get("outline"), metadata)]
            else:
                content, answer_type = _generic_export_input(resource, payload)
                exported = export_generated_answer(content, answer_type, metadata).attachments
            exported = _require_attachments(exported)
        except Exception as exc:
            failures.append({
                "resourceType": resource.resourceType,
                "stage": "exporting",
                "retryable": True,
                "message": "{}交付失败，请重试".format(
                    _resource_title(resource.resourceType, "")
                ).strip(" ·"),
                "errorType": exc.__class__.__name__,
            })
            attachments_by_type[resource.resourceType] = []
            continue
        attachments_by_type[resource.resourceType] = [dict(item) for item in exported]
        all_attachments.extend(dict(item) for item in exported)
    return attachments_by_type, all_attachments, failures


def _review_failure(resource: WorkflowResource) -> Dict[str, Any]:
    title = _resource_title(resource.resourceType, "").strip(" ·")
    if resource.reviewStatus == "rejected":
        message = "{}审核未通过，请重试".format(title)
        error_type = "ResourceReviewRejected"
    else:
        message = "{}审核尚未完成，请重试".format(title)
        error_type = "ResourceReviewIncomplete"
    return {
        "resourceType": resource.resourceType,
        "stage": "reviewing",
        "retryable": True,
        "message": message,
        "errorType": error_type,
    }


def _require_attachments(exported: Any) -> List[Dict[str, Any]]:
    if not isinstance(exported, list):
        raise EmptyResourceExportError("资源导出器未返回附件列表")
    normalized = []
    for raw in exported:
        if not isinstance(raw, Mapping):
            continue
        attachment = dict(raw)
        if any(
            attachment.get(key)
            for key in ("fileName", "name", "title", "url", "storageKey")
        ):
            normalized.append(attachment)
    if not normalized:
        raise EmptyResourceExportError("资源导出器未生成可交付附件")
    return normalized


def _export_metadata(
    resource: WorkflowResource,
    result: LearningWorkflowResult,
) -> Dict[str, Any]:
    return {
        "courseKey": "python",
        "resourceKind": resource.resourceType,
        "reviewStatus": resource.reviewStatus,
        "executedAgent": resource.agentName,
        "answerType": _answer_type(resource.resourceType),
        "title": _resource_title(resource.resourceType, result.packageMetadata.title),
        "evidenceIds": list(resource.evidenceIds),
    }


def _generic_export_input(
    resource: WorkflowResource,
    payload: Mapping[str, Any],
) -> Tuple[str, str]:
    if resource.resourceType == "practice_set":
        return json.dumps(
            {"questions": payload.get("questions") or []},
            ensure_ascii=False,
        ), "question_bank"
    return resource.content, _answer_type(resource.resourceType)


def _prepare_code_lab_payload(
    resource: WorkflowResource,
    payload: Mapping[str, Any],
) -> Tuple[Dict[str, Any], List[str]]:
    normalized = dict(payload)
    sources = _python_sources(normalized)
    if sources:
        return normalized, sources

    fenced_sources = _python_fenced_blocks(resource.content)
    if not fenced_sources:
        raise EmptyResourceExportError("代码实验 Markdown 缺少 Python 代码块")
    code_lab = normalized.get("codeLab")
    code_lab = dict(code_lab) if isinstance(code_lab, Mapping) else {}
    code_lab.setdefault("markdown", resource.content)
    code_lab["source"] = fenced_sources[0]
    normalized["codeLab"] = code_lab
    return normalized, fenced_sources


def _python_fenced_blocks(content: str) -> List[str]:
    matches = re.finditer(
        r"```[ \t]*(?:python3?|py)\b[^\r\n]*\r?\n(?P<source>.*?)```",
        str(content or ""),
        flags=re.IGNORECASE | re.DOTALL,
    )
    return [
        match.group("source").strip()
        for match in matches
        if match.group("source").strip()
    ]


def _export_mind_map(
    resource: WorkflowResource,
    payload: Mapping[str, Any],
    metadata: Mapping[str, Any],
) -> List[Dict[str, Any]]:
    mind_map = payload.get("mindMap")
    if not isinstance(mind_map, Mapping):
        raise MindMapContractError("思维导图 payload.mindMap 必须是对象")

    images = mind_map.get("images")
    if isinstance(images, list):
        return _mind_map_image_attachments(mind_map, images)
    if mind_map.get("url") or mind_map.get("base64"):
        return _mind_map_image_attachments(mind_map, [mind_map])

    mermaid_body = _mind_map_mermaid_body(resource.content, mind_map)
    if mermaid_body:
        declaration = next(
            (line.strip().lower() for line in mermaid_body.splitlines() if line.strip()),
            "",
        )
        if declaration != "mindmap":
            raise MindMapContractError("思维导图 Mermaid 类型必须为 mindmap")
        mermaid = "```mermaid\n{}\n```".format(mermaid_body.strip())
        return export_generated_answer(
            mermaid,
            "mermaid_mindmap",
            dict(metadata),
        ).attachments
    raise MindMapContractError("思维导图既不是图片结果也不是 Mermaid mindmap")


def _mind_map_image_attachments(
    response: Mapping[str, Any],
    images: List[Any],
) -> List[Dict[str, Any]]:
    response_status = str(response.get("status") or "").strip().lower()
    if response_status in {"failed", "cancelled"}:
        return []
    task_id = str(response.get("taskId") or "diagram_mind_map_agent").strip()
    attachments: List[Dict[str, Any]] = []
    for index, raw in enumerate(images, start=1):
        if not isinstance(raw, Mapping):
            continue
        item_status = str(raw.get("status") or "success").strip().lower()
        if item_status not in {"success", "completed", "partial_success"}:
            continue
        url = str(raw.get("url") or "").strip()
        if url:
            attachment = _remote_image_attachment(url, raw, task_id, index)
            if attachment:
                attachments.append(attachment)
                continue
        encoded = str(raw.get("base64") or "").strip()
        if encoded:
            attachments.append(_persist_inline_image(encoded, task_id, index))
    return attachments


def _remote_image_attachment(
    url: str,
    item: Mapping[str, Any],
    task_id: str,
    index: int,
) -> Dict[str, Any]:
    try:
        parsed = urlparse(url)
    except ValueError:
        return {}
    if parsed.scheme.lower() not in {"http", "https"} or not parsed.netloc:
        return {}
    extension = _image_extension_from_name(parsed.path)
    mime_type = str(item.get("mimeType") or "").strip().lower()
    if not mime_type.startswith("image/"):
        mime_type = "image/{}".format("jpeg" if extension == "jpg" else extension)
    name = "python-mind-map-{}.{}".format(index, extension)
    return {
        "name": name,
        "fileName": name,
        "type": "image",
        "kind": "image",
        "ext": extension,
        "mimeType": mime_type,
        "url": url,
        "previewUrl": url,
        "source": "generated_image",
        "sourceType": "generated_image",
        "sourceId": task_id,
        "serverGenerated": True,
    }


def _persist_inline_image(encoded: str, task_id: str, index: int) -> Dict[str, Any]:
    mime_hint = ""
    raw_value = encoded
    data_url = re.fullmatch(
        r"data:(?P<mime>image/[a-z0-9.+-]+);base64,(?P<data>.+)",
        encoded,
        flags=re.IGNORECASE | re.DOTALL,
    )
    if data_url:
        mime_hint = data_url.group("mime").lower()
        raw_value = data_url.group("data")
    try:
        content = base64.b64decode(raw_value, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise MindMapContractError("思维导图图片 base64 无效") from exc
    extension, mime_type = _detect_image_format(content)
    if mime_hint and mime_hint != mime_type:
        raise MindMapContractError("思维导图图片 MIME 与内容不一致")

    root = generated_exporter._current_export_root()
    generated_exporter.cleanup_generated_exports(root=root)
    path = generated_exporter._new_export_path("python-mind-map", extension)
    try:
        generated_exporter._atomic_write_payload(
            path,
            lambda temporary_path: temporary_path.write_bytes(content),
        )
        attachment = generated_exporter._attachment_for_file(
            path,
            "image_generation_tool",
            "思维导图图片",
        )
        attachment.update({
            "name": "python-mind-map-{}.{}".format(index, extension),
            "fileName": "python-mind-map-{}.{}".format(index, extension),
            "type": "image",
            "kind": "image",
            "ext": extension,
            "mimeType": mime_type,
            "source": "generated_image",
            "sourceType": "generated_image",
            "sourceId": task_id,
        })
        result = generated_exporter._finalize_export_batch(
            generated_exporter.GeneratedExportResult(
                attachments=[attachment],
                diagnostics={
                    "skipped": False,
                    "contentKind": "mind_map_image",
                    "producedFormats": [extension],
                },
            )
        )
        return result.attachments[0]
    except Exception:
        generated_exporter._delete_export_pair(root, path.name)
        raise


def _detect_image_format(content: bytes) -> Tuple[str, str]:
    if content.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png", "image/png"
    if content.startswith(b"\xff\xd8\xff"):
        return "jpg", "image/jpeg"
    if content.startswith((b"GIF87a", b"GIF89a")):
        return "gif", "image/gif"
    if len(content) >= 12 and content.startswith(b"RIFF") and content[8:12] == b"WEBP":
        return "webp", "image/webp"
    raise MindMapContractError("思维导图图片格式不受支持")


def _image_extension_from_name(path: str) -> str:
    extension = str(path or "").rsplit("/", 1)[-1].rsplit(".", 1)[-1].lower()
    return extension if extension in {"png", "jpg", "jpeg", "gif", "webp"} else "png"


def _mind_map_mermaid_body(content: str, mind_map: Mapping[str, Any]) -> str:
    candidates = [
        mind_map.get("content"),
        mind_map.get("mermaid"),
        mind_map.get("source"),
        content,
    ]
    for candidate in candidates:
        text = str(candidate or "").strip()
        if not text:
            continue
        fenced = re.search(
            r"```[ \t]*mermaid[^\r\n]*\r?\n(?P<body>.*?)```",
            text,
            flags=re.IGNORECASE | re.DOTALL,
        )
        if fenced and fenced.group("body").strip():
            return fenced.group("body").strip()
        first_line = next((line.strip() for line in text.splitlines() if line.strip()), "")
        if first_line.lower() == "mindmap" or first_line.lower().startswith("flowchart"):
            return text
    return ""


def _python_sources(value: Any) -> List[str]:
    sources: List[str] = []
    if isinstance(value, Mapping):
        language = str(value.get("language") or "").strip().lower()
        for key, item in value.items():
            if key in {"source", "testSource"} and isinstance(item, str) and item.strip():
                sources.append(item)
            elif key == "code" and isinstance(item, str) and item.strip() and language in {"", "python", "py"}:
                sources.append(item)
            else:
                sources.extend(_python_sources(item))
    elif isinstance(value, list):
        for item in value:
            sources.extend(_python_sources(item))
    if not sources:
        return []
    unique = []
    for source in sources:
        if source not in unique:
            unique.append(source)
    return unique


def _answer_type(resource_type: str) -> str:
    return {
        "mind_map": "mermaid_mindmap",
        "practice_set": "question_bank",
        "code_lab": "code",
        "presentation": "ppt_outline",
    }.get(resource_type, "markdown")


def _resource_title(resource_type: str, package_title: str) -> str:
    label = {
        "knowledge_note": "课程讲义",
        "mind_map": "思维导图",
        "practice_set": "练习题",
        "code_lab": "代码实验",
        "presentation": "教学课件",
        "extended_reading": "拓展阅读",
    }.get(resource_type, "学习资源")
    return "{} · {}".format(package_title, label)


__all__ = ["export_learning_resources"]
