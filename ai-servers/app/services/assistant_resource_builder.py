import copy
import hashlib
import ipaddress
import json
import math
import re
from datetime import datetime, timezone
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Tuple
from urllib.parse import urlparse


RESOURCE_SCHEMA_VERSION = "assistant-resource-v1"
EVIDENCE_SCHEMA_VERSION = "assistant-evidence-v1"
MAX_SOURCES = 20
MAX_EXCERPT_CHARS = 800
MAX_RESOURCES = 40
MAX_CONTENT_CHARS = 12_000
MAX_ENVELOPE_BYTES = 256 * 1024

BUSINESS_CARD_FIELDS: Dict[str, Tuple[str, ...]] = {
    "course": ("businessId", "courseName", "teacherName", "weekday", "startSection", "endSection", "classroom", "weekText"),
    "activity": ("businessId", "title", "category", "startTime", "endTime", "location", "status"),
    "meeting": ("businessId", "title", "startTime", "endTime", "location", "status"),
    "dining": ("businessId", "name", "category", "location", "openingHours", "rating", "priceRange", "imageUrl"),
    "facility": ("businessId", "name", "category", "location", "openingHours", "status", "longitude", "latitude"),
    "secondhand": ("businessId", "title", "category", "price", "condition", "status", "createdAt", "imageUrl"),
}

_KIND_ALIASES = {
    "course": "course", "course_schedule": "course", "course_schedule_summary": "course",
    "activity": "activity", "meeting": "meeting",
    "dining": "dining", "restaurant": "dining", "stall": "dining", "dish": "dining", "coupon": "dining",
    "facility": "facility", "facility_location": "facility",
    "secondhand": "secondhand", "secondhand_item": "secondhand",
}
_FIELD_ALIASES = {
    ("course", "businessId"): ("businessId", "courseId", "id"),
    ("course", "courseName"): ("courseName", "name", "title"),
    ("course", "weekday"): ("weekday", "weekdayNumber"),
    ("course", "startSection"): ("startSection", "sectionStart"),
    ("course", "endSection"): ("endSection", "sectionEnd"),
    ("course", "classroom"): ("classroom", "classroomName", "location"),
    ("course", "weekText"): ("weekText", "semesterLabel"),
    ("activity", "businessId"): ("businessId", "activityId", "id"),
    ("activity", "title"): ("title", "name"),
    ("meeting", "businessId"): ("businessId", "meetingId", "id"),
    ("meeting", "title"): ("title", "name"),
    ("meeting", "startTime"): ("startTime", "scheduledStartTime"),
    ("meeting", "endTime"): ("endTime", "scheduledEndTime"),
    ("meeting", "location"): ("location", "roomCode"),
    ("dining", "businessId"): ("businessId", "restaurantId", "stallId", "dishId", "couponId", "id"),
    ("dining", "name"): ("name", "title"),
    ("dining", "category"): ("category", "typeName"),
    ("dining", "location"): ("location", "pickupLocation"),
    ("dining", "openingHours"): ("openingHours", "businessHours"),
    ("dining", "priceRange"): ("priceRange", "avgPrice", "price"),
    ("facility", "businessId"): ("businessId", "facilityId", "id"),
    ("facility", "name"): ("name", "title"),
    ("facility", "category"): ("category", "facilityTypeName"),
    ("secondhand", "businessId"): ("businessId", "itemId", "id"),
    ("secondhand", "title"): ("title", "name"),
}
_SAFE_RESOURCE_METADATA = {
    "source", "sourceId", "sourceType", "sourceVersion", "retrievedAt",
    "location", "route", "status", "legacy", "serverGenerated",
}
_SAFE_TRACE_DETAIL = {
    "agentName", "targetAgent", "toolName", "toolDisplayName", "routeReason",
    "intent", "resultCount", "documentCount", "strategy", "summarizedByModel",
}
_CONTEXT_SOURCES = {"user_profile", "profile", "conversation_context", "chat_history", "history"}
_FORBIDDEN_KEYS = {
    "userid", "sellerid", "phone", "contact", "memberlist", "participants",
    "transcript", "token", "raw", "authorization", "apikey", "capability", "profile",
}


def build_assistant_resource_bundle(
    *,
    answer: str,
    answer_type: str = "text",
    documents: Optional[Sequence[Any]] = None,
    trace: Optional[Sequence[Any]] = None,
    metadata: Optional[Mapping[str, Any]] = None,
    attachments: Optional[Sequence[Any]] = None,
    request_context: Optional[Mapping[str, Any]] = None,
) -> Dict[str, Any]:
    metadata = _mapping(metadata)
    request_context = _mapping(request_context)
    documents = list(documents or ())
    generated_at = _timestamp(request_context.get("generatedAt") or metadata.get("generatedAt"))
    context_used = _context_used(metadata, request_context) or _has_context_document(documents)
    query = _first(request_context, "query", "input", "requestInput", "originalInput", "currentPrompt")
    answer_text = _text(answer, MAX_CONTENT_CHARS)
    query_digest, answer_digest = _digest_text(query), _digest_text(str(answer or ""))
    request_id = _request_id(request_context.get("requestId") or metadata.get("requestId"), query_digest, answer_digest)

    sources, source_ids, business_items = _build_sources(documents, generated_at)
    chain_status = "grounded" if sources else ("context_only" if context_used else "model_only")
    resources = []
    for kind, item, evidence_id in business_items:
        resource = _business_resource(kind, item, evidence_id, generated_at)
        if resource:
            resources.append(resource)
    for raw in list(attachments or ()):
        if len(resources) >= MAX_RESOURCES:
            break
        attachment = _mapping(raw)
        evidence_ids = _attachment_evidence_ids(attachment, source_ids)
        status = "grounded" if evidence_ids else ("context_only" if context_used else "model_only")
        resource = _attachment_resource(attachment, evidence_ids, status, generated_at)
        if resource:
            resources.append(resource)
    if answer_text and not resources:
        resources.append(_content_resource(
            answer_text, answer_type, [item["evidenceId"] for item in sources],
            chain_status, generated_at, metadata,
        ))

    generation = {
        "agent": _text(
            _first(request_context, "agent", "agentName")
            or _first(metadata, "executedAgent", "targetAgent", "agentName")
            or "leader_agent", 64,
        ),
        "model": _text(_first(request_context, "model") or _first(metadata, "model"), 128),
        "answerType": _text(answer_type or metadata.get("answerType") or "text", 64),
        "profileContextUsed": bool(request_context.get("profileContextUsed") or metadata.get("profileContextUsed")),
    }
    links = [{"resourceId": item["id"], "evidenceIds": list(item["evidenceIds"])} for item in resources]
    chain_seed = {
        "requestId": request_id, "queryDigest": query_digest, "answerDigest": answer_digest,
        "evidenceIds": [item["evidenceId"] for item in sources],
        "resourceIds": [item["id"] for item in resources],
    }
    chain = {
        "schemaVersion": EVIDENCE_SCHEMA_VERSION,
        "chainId": "chain_" + _hex_digest(chain_seed)[:24],
        "requestId": request_id,
        "status": chain_status,
        "generatedAt": generated_at,
        "evidenceState": "available",
        "queryDigest": query_digest,
        "answerDigest": answer_digest,
        "sources": sources,
        "steps": _trace_steps(trace or ()),
        "resourceLinks": links,
        "generation": generation,
    }
    chain["integrity"] = _integrity(chain)
    return _fit_envelope({"resources": resources, "evidenceChain": chain})


def finalize_assistant_response(response: Any, *, request_context: Optional[Mapping[str, Any]] = None) -> Any:
    if response is None:
        return None
    bundle = build_assistant_resource_bundle(
        answer=getattr(response, "answer", ""),
        answer_type=getattr(response, "answerType", "text"),
        documents=getattr(response, "documents", ()) or (),
        trace=getattr(response, "trace", ()) or (),
        metadata=getattr(response, "metadata", {}) or {},
        attachments=getattr(response, "attachments", ()) or (),
        request_context=request_context,
    )
    response.resources = bundle["resources"]
    response.evidenceChain = bundle["evidenceChain"]
    return response


def verify_evidence_integrity(chain: Mapping[str, Any]) -> bool:
    if not isinstance(chain, Mapping) or not isinstance(chain.get("integrity"), Mapping):
        return False
    expected = str(chain["integrity"].get("digest") or "").lower()
    unsigned = copy.deepcopy(dict(chain))
    unsigned.pop("integrity", None)
    return bool(re.fullmatch(r"sha256:[0-9a-f]{64}", expected)) and expected == _digest_value(unsigned)


def verify_assistant_resource_bundle(bundle: Mapping[str, Any]) -> bool:
    if not isinstance(bundle, Mapping):
        return False
    chain = bundle.get("evidenceChain")
    if not isinstance(chain, Mapping) or not verify_evidence_integrity(chain):
        return False
    resources = bundle.get("resources")
    resource_links = chain.get("resourceLinks")
    if not isinstance(resources, list) or not isinstance(resource_links, list):
        return False
    resource_map = _evidence_link_map(resources, "id")
    chain_map = _evidence_link_map(resource_links, "resourceId")
    return resource_map is not None and chain_map is not None and resource_map == chain_map


def canonical_json(value: Any) -> str:
    return json.dumps(_json_safe(value), ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)


def _build_sources(documents: Sequence[Any], generated_at: str):
    sources, source_ids, cards, seen = [], {}, [], set()
    for raw in documents:
        if len(sources) >= MAX_SOURCES:
            break
        document = _mapping(raw)
        metadata = _mapping(document.get("metadata"))
        if _non_factual_document(document, metadata):
            continue
        content = str(document.get("content") or "")
        raw_document_id = _text(document.get("id"), 256)
        source_id = _text(raw_document_id or metadata.get("sourceId"), 256)
        if re.fullmatch(r"tool:\d+", source_id):
            source_id = ""
        source_id = source_id or "source_" + _hex_digest(content)[:24]
        kind, business_item = _business_item(metadata)
        source_type = "java_backend" if kind else _identifier(metadata.get("sourceType") or document.get("source"), "knowledge_base")
        content_digest = _digest_text(content)
        evidence_id = "ev_" + _hex_digest({
            "sourceType": source_type, "sourceId": source_id, "contentDigest": content_digest,
        })[:24]
        source_ids[source_id] = evidence_id
        if raw_document_id and not re.fullmatch(r"tool:\d+", raw_document_id):
            source_ids[raw_document_id] = evidence_id
        if evidence_id in seen:
            continue
        seen.add(evidence_id)
        title_source = business_item or metadata
        source = {
            "evidenceId": evidence_id,
            "sourceType": source_type,
            "sourceId": source_id,
            "title": _text(_first(title_source, "title", "name", "courseName") or source_id, 240),
            "excerpt": _text(content, MAX_EXCERPT_CHARS),
            "retrievedAt": _timestamp(metadata.get("retrievedAt") or generated_at),
            "contentDigest": content_digest,
            "accessScope": _identifier(metadata.get("accessScope"), "request_user"),
        }
        if metadata.get("sourceVersion"):
            source["sourceVersion"] = _text(metadata["sourceVersion"], 128)
        sources.append(source)
        if kind and business_item:
            cards.append((kind, business_item, evidence_id))
    return sources, source_ids, cards


def _business_resource(kind: str, item: Mapping[str, Any], evidence_id: str, generated_at: str):
    payload = {"type": "business"}
    for field in BUSINESS_CARD_FIELDS[kind]:
        aliases = _FIELD_ALIASES.get((kind, field), (field,))
        value = _payload_value(field, _first_value(item, aliases))
        if value not in (None, ""):
            payload[field] = value
    business_id = _text(payload.get("businessId"), 200)
    if not business_id:
        return None
    title = _text(payload.get("courseName") or payload.get("title") or payload.get("name") or kind, 240)
    return _resource(
        resource_id="res_" + _hex_digest({"kind": kind, "businessId": business_id, "evidenceIds": [evidence_id]})[:24],
        kind=kind, delivery_type="business_card", status="grounded", title=title,
        summary=_business_summary(kind, payload), payload=payload, evidence_ids=[evidence_id],
        generated_at=generated_at, mime_type="application/json", source_type="java_backend",
        source_id=f"{kind}:{business_id}", auth_scope="request_user",
        actions=[{"type": "open_resource", "label": "打开", "target": "resource", "requiresAuth": True}],
    )


def _attachment_resource(attachment, evidence_ids, status, generated_at):
    if not any(attachment.get(key) for key in ("fileName", "name", "title", "url", "storageKey")):
        return None
    name = _text(attachment.get("fileName") or attachment.get("name") or attachment.get("title") or "文件", 240)
    url = _safe_url(attachment.get("url"), relative=True)
    preview_url = _safe_url(attachment.get("previewUrl"), relative=True)
    storage_key = _text(attachment.get("storageKey"), 256)
    mime_type = _text(attachment.get("mimeType"), 160)
    file_format = _attachment_format(attachment, name, mime_type)
    kind, delivery_type = _attachment_kind(file_format, mime_type, attachment.get("kind"))
    size = _non_negative_int(attachment.get("size"))
    digest = _normalize_digest(attachment.get("sha256") or attachment.get("digest"))
    payload = {"type": "file", "format": file_format}
    if size is not None:
        payload["size"] = size
    if digest:
        payload["digest"] = digest
    integrity = None if digest == "" and size is None else {
        "algorithm": "SHA-256", "digest": digest.removeprefix("sha256:"), "size": size or 0,
    }
    actions = []
    if delivery_type in {"image", "video", "audio"}:
        actions.append({"type": "preview", "label": "预览", "target": "resource", "requiresAuth": True})
    if url or storage_key:
        actions.append({"type": "download", "label": "下载", "target": "resource", "requiresAuth": True})
    return _resource(
        resource_id="res_" + _hex_digest({"kind": kind, "storageKey": storage_key, "url": url, "title": name, "digest": digest})[:24],
        kind=kind, delivery_type=delivery_type, status=status, title=name,
        summary=_text(attachment.get("summary") or f"{name} 附件", 400), payload=payload,
        evidence_ids=evidence_ids, generated_at=_timestamp(attachment.get("createdAt") or generated_at),
        mime_type=mime_type, source_type=_identifier(attachment.get("sourceType"), "generated_export"),
        source_id=_text(attachment.get("sourceId") or "agent_generated_file", 256),
        auth_scope="session_owner" if storage_key else "request_user", actions=actions[:3],
        storage_key=storage_key, url=url, preview_url=preview_url,
        expires_at=_optional_timestamp(attachment.get("expiresAt")), integrity=integrity,
        metadata=_safe_metadata(attachment),
    )


def _content_resource(answer, answer_type, evidence_ids, status, generated_at, metadata):
    kind = _content_kind(answer_type, metadata)
    payload = {"type": "content", "content": answer, "language": _content_language(answer, answer_type)}
    return _resource(
        resource_id="res_" + _hex_digest({"kind": kind, "contentDigest": _digest_text(answer)})[:24],
        kind=kind, delivery_type="content", status=status,
        title=_text(metadata.get("title") or _content_title(kind), 240),
        summary=_text(metadata.get("summary") or answer, 400), payload=payload,
        evidence_ids=evidence_ids, generated_at=generated_at,
        mime_type="text/markdown" if answer_type in {"markdown", "code"} else "text/plain",
        source_type="response_content", source_id="assistant_answer", auth_scope="request_user",
        metadata=_safe_metadata(metadata),
    )


def _resource(
    *, resource_id, kind, delivery_type, status, title, summary, payload, evidence_ids,
    generated_at, mime_type, source_type, source_id, auth_scope, actions=(), storage_key="",
    url="", preview_url="", expires_at=None, integrity=None, metadata=None,
):
    return {
        "schemaVersion": RESOURCE_SCHEMA_VERSION, "id": resource_id, "messageId": None,
        "kind": kind, "deliveryType": delivery_type, "groundingStatus": status,
        "title": title, "summary": summary, "mimeType": mime_type,
        "storageKey": storage_key, "url": url, "previewUrl": preview_url,
        "sourceType": source_type, "sourceId": source_id, "evidenceIds": list(evidence_ids),
        "actions": list(actions), "authScope": auth_scope, "createdAt": generated_at,
        "expiresAt": expires_at, "integrity": integrity, "payload": payload,
        "metadata": metadata or {},
    }


def _trace_steps(trace):
    result = []
    for raw in list(trace)[:20]:
        step, detail = _mapping(raw), _mapping(_mapping(raw).get("detail"))
        result.append({
            "stage": _identifier(step.get("stage"), "unknown"),
            "detail": {key: _scalar(value, 300) for key, value in detail.items()
                       if key in _SAFE_TRACE_DETAIL and not _forbidden(key) and _scalar(value, 300) is not None},
        })
    return result


def _integrity(chain):
    unsigned = copy.deepcopy(dict(chain))
    unsigned.pop("integrity", None)
    return {
        "algorithm": "SHA-256", "digest": _digest_value(unsigned),
        "scope": "canonical-json-without-integrity", "signed": False,
    }


def _fit_envelope(bundle):
    if len(canonical_json(bundle).encode("utf-8")) <= MAX_ENVELOPE_BYTES:
        return bundle
    for resource in bundle["resources"]:
        payload = resource.get("payload") or {}
        if payload.get("type") == "content":
            payload["content"] = _text(payload.get("content"), 2_000)
        resource["summary"], resource["metadata"] = _text(resource.get("summary"), 160), {}
    for source in bundle["evidenceChain"].get("sources", []):
        source["excerpt"] = _text(source.get("excerpt"), 320)
    while len(canonical_json(bundle).encode("utf-8")) > MAX_ENVELOPE_BYTES and bundle["resources"]:
        removed_id = bundle["resources"].pop()["id"]
        bundle["evidenceChain"]["resourceLinks"] = [
            link for link in bundle["evidenceChain"]["resourceLinks"] if link.get("resourceId") != removed_id
        ]
    bundle["evidenceChain"]["integrity"] = _integrity(bundle["evidenceChain"])
    return bundle


def _business_item(metadata):
    item = _mapping(metadata.get("item")) or metadata
    raw_kind = metadata.get("kind") or item.get("kind") or item.get("type")
    kind = _KIND_ALIASES.get(str(raw_kind or "").strip().lower(), "")
    return kind, item if kind else {}


def _attachment_evidence_ids(attachment, source_ids):
    candidates = [attachment.get(key) for key in ("sourceId", "documentId", "evidenceSourceId")]
    if isinstance(attachment.get("sourceIds"), list):
        candidates.extend(attachment["sourceIds"])
    result = []
    for candidate in candidates:
        candidate = str(candidate or "").strip()
        if not candidate:
            continue
        evidence_id = source_ids.get(candidate)
        if evidence_id and evidence_id not in result:
            result.append(evidence_id)
    return result


def _non_factual_document(document, metadata):
    source = str(metadata.get("sourceType") or document.get("source") or "").strip().lower()
    if source in _CONTEXT_SOURCES or metadata.get("profileContextUsed"):
        return True
    if metadata.get("serverGenerated") or document.get("serverGenerated"):
        return True
    signals = " ".join(str(value or "").lower() for value in (
        document.get("source"), metadata.get("sourceType"), metadata.get("type"), metadata.get("kind"),
    ))
    return "generated_export" in signals or "agent_generated_file" in signals


def _has_context_document(documents):
    for raw in documents:
        document, metadata = _mapping(raw), _mapping(_mapping(raw).get("metadata"))
        source = str(metadata.get("sourceType") or document.get("source") or "").strip().lower()
        if source in _CONTEXT_SOURCES or metadata.get("profileContextUsed"):
            return True
    return False


def _content_kind(answer_type, metadata):
    answer_type = str(answer_type or "").lower()
    agent = str(metadata.get("executedAgent") or metadata.get("targetAgent") or "").lower()
    if "mind" in answer_type or "mind_map" in agent:
        return "mind_map"
    if "mermaid" in answer_type or "diagram" in answer_type or "diagram" in agent:
        return "diagram"
    if "question" in answer_type or "exercise" in answer_type:
        return "exercise"
    return "code_example" if "code" in answer_type or "programming" in agent else "explanation"


def _content_language(answer, answer_type):
    match = re.match(r"\s*```([A-Za-z0-9_+-]+)", str(answer or ""))
    if match:
        return _identifier(match.group(1), "text")
    if str(answer_type or "").lower() == "code":
        return "python"
    if "mermaid" in str(answer_type or "").lower():
        return "mermaid"
    return "markdown" if str(answer_type or "").lower() == "markdown" else "text"


def _content_title(kind):
    return {"mind_map": "思维导图", "diagram": "图解", "exercise": "练习题", "code_example": "代码示例"}.get(kind, "回答内容")


def _attachment_format(attachment, name, mime_type):
    explicit = attachment.get("ext") or attachment.get("format") or attachment.get("type")
    if explicit:
        value = str(explicit).lower().strip().lstrip(".")
        return _identifier({"excel": "xlsx", "document": "file", "presentation": "pptx"}.get(value, value), "file")
    if "." in name:
        return _identifier(name.rsplit(".", 1)[1].lower(), "file")
    return _identifier(mime_type.rsplit("/", 1)[-1].lower() if "/" in mime_type else "", "file")


def _attachment_kind(file_format, mime_type, explicit):
    explicit = str(explicit or "").strip().lower()
    if explicit in {"image", "video", "audio", "document", "presentation", "spreadsheet", "bundle"}:
        return explicit, explicit
    groups = (
        ("image", {"png", "jpg", "jpeg", "gif", "webp", "bmp", "svg"}),
        ("video", {"mp4", "mov", "webm", "m4v"}),
        ("audio", {"mp3", "wav", "m4a", "ogg"}),
        ("presentation", {"ppt", "pptx"}), ("spreadsheet", {"xls", "xlsx", "csv"}),
        ("bundle", {"zip", "tar", "gz"}),
    )
    for kind, extensions in groups:
        if file_format in extensions or mime_type.startswith(kind + "/"):
            return kind, kind
    return "document", "document"


def _business_summary(kind, payload):
    fields = {
        "course": ("teacherName", "weekday", "classroom"), "activity": ("category", "startTime", "location"),
        "meeting": ("startTime", "location", "status"), "dining": ("category", "location", "openingHours"),
        "facility": ("category", "location", "status"), "secondhand": ("category", "price", "condition"),
    }[kind]
    return _text(" · ".join(str(payload[field]) for field in fields if payload.get(field) not in (None, "")), 400)


def _payload_value(field, value):
    if value is None or isinstance(value, (dict, list, tuple, set)):
        return None
    if field in {"longitude", "latitude", "rating", "price", "startSection", "endSection", "weekday"}:
        if isinstance(value, bool):
            return None
        try:
            number = float(value)
            return int(number) if math.isfinite(number) and number.is_integer() else number if math.isfinite(number) else None
        except (TypeError, ValueError):
            return _text(value, 80)
    return _safe_url(value, relative=False) if field == "imageUrl" else _text(value, 300)


def _safe_metadata(value):
    return {
        key: _scalar(item, 300) for key, item in value.items()
        if key in _SAFE_RESOURCE_METADATA and not _forbidden(key) and _scalar(item, 300) is not None
    }


def _context_used(*values):
    keys = ("historyContextUsed", "profileContextUsed", "conversationContextUsed", "conversationHistoryUsed")
    return any(bool(value.get(key)) for value in values for key in keys)


def _request_id(raw, query_digest, answer_digest):
    value = _identifier(raw, "", 128, colon=True)
    return value or "req_" + _hex_digest({"query": query_digest, "answer": answer_digest})[:24]


def _safe_url(value, *, relative):
    value = _text(value, 1_000)
    if relative and value.startswith("/") and not value.startswith("//"):
        parsed_relative = urlparse(value)
        allowed_path = parsed_relative.path.startswith(("/api/", "/uploads/"))
        safe_segments = all(segment not in {".", ".."} for segment in parsed_relative.path.split("/"))
        return value if allowed_path and safe_segments and "\\" not in value else ""
    try:
        parsed = urlparse(value)
        host = (parsed.hostname or "").lower().rstrip(".")
        parsed.port
    except ValueError:
        return ""
    if parsed.scheme.lower() not in {"http", "https"} or not parsed.netloc or "@" in parsed.netloc:
        return ""
    if not host or host == "localhost" or host.endswith((".local", ".internal", ".localhost")):
        return ""
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        return value if not _ambiguous_numeric_host(host) and _is_fqdn(host) else ""
    if isinstance(address, ipaddress.IPv6Address) and address.ipv4_mapped:
        address = address.ipv4_mapped
    return value if address.is_global else ""


def _is_fqdn(host):
    try:
        ascii_host = host.encode("idna").decode("ascii")
    except UnicodeError:
        return False
    if "." not in ascii_host or len(ascii_host) > 253:
        return False
    label_pattern = re.compile(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?", re.IGNORECASE)
    return all(label_pattern.fullmatch(label) for label in ascii_host.split("."))


def _ambiguous_numeric_host(host):
    return bool(
        re.fullmatch(r"[0-9.]+", host)
        or re.fullmatch(r"0x[0-9a-f]+", host)
        or re.fullmatch(r"0x[0-9a-f]+(?:\.(?:0x[0-9a-f]+|[0-9]+))+", host)
    )


def _evidence_link_map(items, id_key):
    result = {}
    for item in items:
        if not isinstance(item, Mapping):
            return None
        item_id = item.get(id_key)
        evidence_ids = item.get("evidenceIds")
        if not isinstance(item_id, str) or not item_id or item_id in result:
            return None
        if not isinstance(evidence_ids, list) or any(not isinstance(value, str) or not value for value in evidence_ids):
            return None
        if len(evidence_ids) != len(set(evidence_ids)):
            return None
        result[item_id] = evidence_ids
    return result


def _normalize_digest(value):
    value = str(value or "").strip().lower().removeprefix("sha256:")
    return f"sha256:{value}" if re.fullmatch(r"[0-9a-f]{64}", value) else ""


def _digest_text(value):
    return "sha256:" + hashlib.sha256(str(value or "").encode("utf-8")).hexdigest()


def _hex_digest(value):
    return hashlib.sha256(canonical_json(value).encode("utf-8")).hexdigest()


def _digest_value(value):
    return "sha256:" + _hex_digest(value)


def _json_safe(value):
    if value is None or isinstance(value, (str, bool, int)):
        return value
    if isinstance(value, float):
        return value if math.isfinite(value) else None
    if isinstance(value, Mapping):
        return {str(key): _json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_safe(item) for item in value]
    return _json_safe(value.model_dump()) if hasattr(value, "model_dump") else str(value)


def _mapping(value):
    if isinstance(value, Mapping):
        return dict(value)
    if hasattr(value, "model_dump"):
        value = value.model_dump()
        return dict(value) if isinstance(value, Mapping) else {}
    return {}


def _first(value, *keys):
    for key in keys:
        candidate = value.get(key)
        if candidate is not None and str(candidate).strip():
            return str(candidate).strip()
    return ""


def _first_value(value, keys: Iterable[str]):
    for key in keys:
        if key in value and not _forbidden(key):
            return value.get(key)
    return None


def _text(value, limit):
    return str(value or "").replace("\x00", "").strip()[:limit]


def _identifier(value, default, limit=64, *, colon=False):
    pattern = r"[^A-Za-z0-9_.:-]+" if colon else r"[^A-Za-z0-9_.-]+"
    return re.sub(pattern, "_", str(value or "").strip())[:limit].strip("_.-") or default


def _forbidden(key):
    normalized = re.sub(r"[^a-z0-9]", "", str(key or "").lower())
    return any(
        normalized == item or normalized.endswith(item)
        or (item in {"token", "capability", "authorization", "apikey"} and item in normalized)
        for item in _FORBIDDEN_KEYS
    )


def _scalar(value, limit):
    if value is None or isinstance(value, (bool, int)):
        return value
    if isinstance(value, float):
        return value if math.isfinite(value) else None
    return _text(value, limit) if isinstance(value, str) else None


def _non_negative_int(value):
    try:
        value = int(value)
        return value if value >= 0 else None
    except (TypeError, ValueError):
        return None


def _timestamp(value):
    if isinstance(value, datetime):
        parsed = value
    else:
        text = str(value or "").strip()
        try:
            parsed = datetime.fromisoformat(text.replace("Z", "+00:00")) if text else datetime.now(timezone.utc)
        except ValueError:
            parsed = datetime.now(timezone.utc)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def _optional_timestamp(value):
    return _timestamp(value) if str(value or "").strip() else None
