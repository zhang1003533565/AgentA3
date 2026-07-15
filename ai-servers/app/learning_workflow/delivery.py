import base64
import binascii
from io import BytesIO
import json
import re
from typing import Any, Dict, List, Mapping, Tuple
from urllib.parse import urlparse

from PIL import Image, UnidentifiedImageError

from app.learning_workflow.models import LearningWorkflowResult, WorkflowResource
from app.rag.document_conversion import (
    export_generated_answer,
    export_presentation,
    export_python_code_lab,
)
from app.rag.document_conversion import generated_exporter
from app.safety.learning_content_guard import validate_generated_python_code
from app.services.assistant_resource_builder import _safe_url as _safe_resource_url


class EmptyResourceExportError(ValueError):
    pass


class MindMapContractError(ValueError):
    pass


_IMAGE_CONTENT_TYPES = {
    "image/png": ("png", "image/png"),
    "image/jpeg": ("jpg", "image/jpeg"),
    "image/jpg": ("jpg", "image/jpeg"),
    "image/gif": ("gif", "image/gif"),
    "image/webp": ("webp", "image/webp"),
}
_IMAGE_EXTENSIONS = {
    "png": ("png", "image/png"),
    "jpg": ("jpg", "image/jpeg"),
    "jpeg": ("jpg", "image/jpeg"),
    "gif": ("gif", "image/gif"),
    "webp": ("webp", "image/webp"),
}


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
        storage_key = str(attachment.get("storageKey") or "").strip()
        safe_url = _safe_resource_url(attachment.get("url"), relative=False)
        if storage_key or safe_url:
            if safe_url:
                attachment["url"] = safe_url
                if attachment.get("previewUrl"):
                    attachment["previewUrl"] = _safe_resource_url(
                        attachment.get("previewUrl"),
                        relative=False,
                    )
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
    try:
        source, test_source = generated_exporter._extract_code_lab_sources(normalized)
    except generated_exporter.GeneratedExportError:
        source = ""
        test_source = ""
    if source:
        return normalized, [item for item in (source, test_source) if item]

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
        r"```(?P<info>[^\r\n`]*)\r?\n(?P<source>.*?)```",
        str(content or ""),
        flags=re.IGNORECASE | re.DOTALL,
    )
    explicit: List[str] = []
    unlabelled: List[str] = []
    for match in matches:
        source = match.group("source").strip()
        if not source:
            continue
        info = match.group("info").strip().lower()
        language = info.split(None, 1)[0] if info else ""
        if language in {"python", "python3", "py"}:
            explicit.append(source)
            continue
        if info:
            continue
        try:
            validate_generated_python_code(source)
        except Exception:
            continue
        unlabelled.append(source)
    return explicit or unlabelled


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
    safe_url = _safe_resource_url(url, relative=False)
    if not safe_url:
        return {}
    try:
        parsed = urlparse(safe_url)
    except ValueError:
        return {}
    extension, mime_type = _remote_image_format(parsed.path, item)
    name = "python-mind-map-{}.{}".format(index, extension)
    return {
        "name": name,
        "fileName": name,
        "type": "image",
        "kind": "image",
        "ext": extension,
        "mimeType": mime_type,
        "url": safe_url,
        "previewUrl": safe_url,
        "source": "generated_image",
        "sourceType": "generated_image",
        "sourceId": task_id,
        "serverGenerated": True,
    }


def _persist_inline_image(encoded: str, task_id: str, index: int) -> Dict[str, Any]:
    content, mime_hint = _decode_image_base64(encoded)
    extension, mime_type = _detect_image_format(content)
    if mime_hint:
        normalized_hint = _IMAGE_CONTENT_TYPES.get(mime_hint)
        if normalized_hint is None or normalized_hint[1] != mime_type:
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
    if not content:
        raise MindMapContractError("思维导图图片内容为空")
    try:
        with Image.open(BytesIO(content)) as image:
            image_format = str(image.format or "").upper()
            image.verify()
        with Image.open(BytesIO(content)) as image:
            image.load()
            if image.width < 1 or image.height < 1:
                raise MindMapContractError("思维导图图片尺寸无效")
    except (
        Image.DecompressionBombError,
        UnidentifiedImageError,
        OSError,
        SyntaxError,
        ValueError,
    ) as exc:
        raise MindMapContractError("思维导图图片无法通过真实解码校验") from exc
    normalized = {
        "PNG": ("png", "image/png"),
        "JPEG": ("jpg", "image/jpeg"),
        "GIF": ("gif", "image/gif"),
        "WEBP": ("webp", "image/webp"),
    }.get(image_format)
    if normalized is None:
        raise MindMapContractError("思维导图图片格式不受支持")
    return normalized


def _image_extension_from_name(path: str) -> str:
    extension = str(path or "").rsplit("/", 1)[-1].rsplit(".", 1)[-1].lower()
    return extension if extension in _IMAGE_EXTENSIONS else ""


def _remote_image_format(path: str, item: Mapping[str, Any]) -> Tuple[str, str]:
    declared = str(
        item.get("contentType") or item.get("mimeType") or ""
    ).split(";", 1)[0].strip().lower()
    if declared:
        normalized = _IMAGE_CONTENT_TYPES.get(declared)
        if normalized is None:
            raise MindMapContractError("思维导图远程图片 Content-Type 不受支持")
        return normalized

    encoded = str(item.get("base64") or "").strip()
    if encoded:
        try:
            content, _ = _decode_image_base64(encoded)
            return _detect_image_format(content)
        except MindMapContractError:
            pass

    extension = _image_extension_from_name(path)
    if extension:
        return _IMAGE_EXTENSIONS[extension]
    return "png", "image/png"


def _decode_image_base64(encoded: str) -> Tuple[bytes, str]:
    mime_hint = ""
    raw_value = str(encoded or "").strip()
    data_url = re.fullmatch(
        r"data:(?P<mime>image/[a-z0-9.+-]+);base64,(?P<data>.+)",
        raw_value,
        flags=re.IGNORECASE | re.DOTALL,
    )
    if data_url:
        mime_hint = data_url.group("mime").lower()
        raw_value = data_url.group("data")
    raw_value = re.sub(r"\s+", "", raw_value)
    try:
        return base64.b64decode(raw_value, validate=True), mime_hint
    except (binascii.Error, ValueError) as exc:
        raise MindMapContractError("思维导图图片 base64 无效") from exc


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
