"""Credential-safe serialization helpers for local evaluation artifacts."""

import json
import re
from typing import Any, Dict


REDACTED = "[REDACTED]"
_REDACTION_MARKER = "__AGENT_A3_REDACTED__"
SENSITIVE_KEYS = {
    "accountid",
    "apikey",
    "authorization",
    "bearertoken",
    "capability",
    "cookie",
    "credentials",
    "exportcapability",
    "internaltoken",
    "knowledgeid",
    "password",
    "passwd",
    "pythoncapability",
    "resourcecapability",
    "secret",
    "sessionid",
    "setcookie",
    "token",
    "userid",
}
_URL_USERINFO_RE = re.compile(
    r"(?i)\b([a-z][a-z0-9+.-]*://)([^/@\s]+)@"
)
_COOKIE_HEADER_RE = re.compile(
    r"(?im)(\b(?:set-cookie|cookie)\s*:\s*)[^\r\n]*"
)
_BEARER_RE = re.compile(r"(?i)\bbearer\s+[^\s,;\"']+")
_SENSITIVE_QUERY_RE = re.compile(
    r"(?i)([?&](?:access[_-]?token|api[_-]?key|authorization|auth|password|passwd|secret|session(?:id)?|token|cookie)=)[^&#\s]+"
)
_SENSITIVE_ASSIGNMENT_RE = re.compile(
    r"(?ix)"
    r"(\b(?:access[_-]?token|api[_-]?key|authorization|password|passwd|secret|session(?:id)?|token)\b"
    r"[\"']?\s*[:=]\s*)"
    r"(?:\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])*'|[^,;&\r\n}\]]+)"
)


def _normalized_key(value: Any) -> str:
    return re.sub(r"[^a-z0-9]", "", str(value).lower())


def is_sensitive_key(key: Any) -> bool:
    normalized = _normalized_key(key)
    return (
        normalized in SENSITIVE_KEYS
        or normalized.endswith("token")
        or normalized.endswith("password")
        or normalized.endswith("passwd")
        or normalized.endswith("secret")
        or normalized.endswith("capability")
        or "authorization" in normalized
        or "apikey" in normalized
        or normalized in {"cookie", "setcookie"}
    )


def sanitize_text(value: str) -> str:
    """Redact secrets from free text, URLs, headers, and JSON encoded as text."""
    text = str(value)
    try:
        parsed = json.loads(text)
    except (TypeError, json.JSONDecodeError):
        parsed = None
    if isinstance(parsed, (dict, list)):
        return json.dumps(
            sanitize_artifact(parsed),
            ensure_ascii=False,
            separators=(",", ":"),
        )

    text = _URL_USERINFO_RE.sub(
        lambda match: f"{match.group(1)}{_REDACTION_MARKER}@",
        text,
    )
    text = _COOKIE_HEADER_RE.sub(
        lambda match: f"{match.group(1)}{_REDACTION_MARKER}",
        text,
    )
    text = _BEARER_RE.sub(f"Bearer {_REDACTION_MARKER}", text)
    text = _SENSITIVE_QUERY_RE.sub(
        lambda match: f"{match.group(1)}{_REDACTION_MARKER}",
        text,
    )
    text = _SENSITIVE_ASSIGNMENT_RE.sub(
        lambda match: f"{match.group(1)}{_REDACTION_MARKER}",
        text,
    )
    return text.replace(_REDACTION_MARKER, REDACTED)


def sanitize_artifact(value: Any) -> Any:
    """Return a stable JSON-compatible deep copy with credentials removed."""
    if isinstance(value, dict):
        redacted: Dict[str, Any] = {}
        for key, item in value.items():
            redacted[str(key)] = REDACTED if is_sensitive_key(key) else sanitize_artifact(item)
        return redacted
    if isinstance(value, (list, tuple)):
        return [sanitize_artifact(item) for item in value]
    if isinstance(value, str):
        return sanitize_text(value)
    if value is None or isinstance(value, (bool, int, float)):
        return value
    return sanitize_text(str(value))
