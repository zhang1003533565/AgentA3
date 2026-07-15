import json
from typing import Any, Dict, List, Mapping, Tuple

from app.learning_workflow.models import LearningWorkflowResult, WorkflowResource
from app.rag.document_conversion import (
    export_generated_answer,
    export_presentation,
    export_python_code_lab,
)
from app.safety.learning_content_guard import validate_generated_python_code


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
            continue
        try:
            metadata = _export_metadata(resource, result)
            exported: List[Dict[str, Any]] = []
            payload = resource.payload.model_dump(mode="json")
            if resource.resourceType == "code_lab":
                sources = _python_sources(payload)
                if not sources:
                    raise ValueError("代码实验缺少 Python 源码")
                for source in sources:
                    validate_generated_python_code(source)
                exported = export_python_code_lab(payload, metadata).attachments
            elif resource.resourceType == "presentation":
                exported = [export_presentation(payload.get("outline"), metadata)]
            else:
                content, answer_type = _generic_export_input(resource, payload)
                exported = export_generated_answer(content, answer_type, metadata).attachments
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
        "mind_map": "mermaid_mind_map",
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
