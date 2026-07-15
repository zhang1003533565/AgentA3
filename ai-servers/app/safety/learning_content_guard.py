import ast
import hashlib
import re
from typing import Any, Dict, Iterable, List, Mapping, Sequence, Set


class LearningContentGuardError(ValueError):
    pass


_INSTRUCTION_PATTERNS = (
    re.compile(
        r"(?:忽略|无视|覆盖|绕过|忘记).{0,24}(?:规则|指令|提示词|系统|开发者)",
        re.IGNORECASE,
    ),
    re.compile(
        r"(?:输出|显示|打印|泄露|透露).{0,24}(?:系统提示词|隐藏指令|密钥|凭据|令牌)",
        re.IGNORECASE,
    ),
    re.compile(
        r"\b(?:ignore|disregard|override|forget)\b.{0,48}"
        r"\b(?:instruction|prompt|policy|system|developer|rule)s?\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"\b(?:reveal|print|show|leak|exfiltrate)\b.{0,48}"
        r"\b(?:system prompt|secret|credential|api[ _-]?key|token)s?\b",
        re.IGNORECASE,
    ),
    re.compile(r"^\s*(?:system|developer|assistant)\s*:", re.IGNORECASE),
    re.compile(r"</?(?:system|developer|assistant|tool)(?:\s[^>]*)?>", re.IGNORECASE),
)

_BLOCKED_IMPORT_ROOTS = {
    "aiohttp",
    "ftplib",
    "http",
    "httpx",
    "importlib",
    "requests",
    "smtplib",
    "socket",
    "subprocess",
    "urllib",
}
_BLOCKED_DYNAMIC_CALLS = {
    "__import__",
    "compile",
    "delattr",
    "eval",
    "exec",
    "getattr",
    "globals",
    "locals",
    "setattr",
    "vars",
}
_BLOCKED_QUALIFIED_CALLS = {
    "os.getenv",
    "os.remove",
    "os.removedirs",
    "os.rename",
    "os.renames",
    "os.replace",
    "os.rmdir",
    "os.unlink",
    "shutil.rmtree",
}
_BLOCKED_PATH_METHODS = {"rename", "replace", "rmdir", "unlink"}
_CREDENTIAL_ATTRIBUTE_NAMES = {
    "aws_access_key_id",
    "aws_secret_access_key",
    "credential",
    "credentials",
    "environ",
    "password",
    "secret",
    "token",
}


def sanitize_learning_references(
    references: Sequence[Mapping[str, Any]],
) -> List[Dict[str, Any]]:
    """Canonicalize evidence and remove instruction-like text from untrusted retrieval."""
    if not isinstance(references, Sequence) or isinstance(references, (str, bytes)):
        raise LearningContentGuardError("课程证据必须是列表")
    sanitized: List[Dict[str, Any]] = []
    seen: Set[str] = set()
    for index, raw in enumerate(references):
        if not isinstance(raw, Mapping):
            raise LearningContentGuardError("课程证据项必须是对象")
        identifiers = {
            str(raw.get(key) or "").strip()
            for key in ("id", "evidenceId", "referenceId")
            if str(raw.get(key) or "").strip()
        }
        if len(identifiers) != 1:
            raise LearningContentGuardError(
                "课程证据缺少唯一 ID" if not identifiers else "课程证据 ID 冲突"
            )
        evidence_id = next(iter(identifiers))
        if evidence_id in seen:
            raise LearningContentGuardError("课程证据包含重复 ID")
        seen.add(evidence_id)
        original = str(raw.get("content") or raw.get("text") or "").strip()
        content = _strip_instruction_text(original)
        if not content:
            raise LearningContentGuardError(
                "课程证据净化后为空：第 {} 项".format(index + 1)
            )
        metadata = raw.get("metadata")
        safe_metadata = dict(metadata) if isinstance(metadata, Mapping) else {}
        safe_metadata.update({
            "untrustedData": True,
            "instructionTextRemoved": content != original,
            "originalContentDigest": "sha256:"
            + hashlib.sha256(original.encode("utf-8")).hexdigest(),
        })
        item = {
            key: value
            for key, value in raw.items()
            if key not in {"id", "evidenceId", "referenceId", "content", "text", "metadata"}
        }
        item.update({"id": evidence_id, "content": content, "metadata": safe_metadata})
        sanitized.append(item)
    if not sanitized:
        raise LearningContentGuardError("至少需要一条课程证据")
    return sanitized


def validate_evidence_ids(
    declared_ids: Iterable[str],
    allowed_ids: Iterable[str],
) -> None:
    allowed = {str(value).strip() for value in allowed_ids if str(value).strip()}
    declared = [str(value).strip() for value in declared_ids if str(value).strip()]
    unknown = sorted(set(declared) - allowed)
    if unknown:
        raise LearningContentGuardError(
            "生成内容引用了未知证据 ID：{}".format(", ".join(unknown))
        )


def validate_generated_python_code(source: str) -> None:
    """Reject dangerous generated capabilities; this function never executes source."""
    text = str(source or "")
    if not text.strip():
        raise LearningContentGuardError("生成的 Python 代码为空")
    try:
        tree = ast.parse(text)
    except SyntaxError as exc:
        raise LearningContentGuardError("生成的 Python 代码语法无效") from exc

    aliases: Dict[str, str] = {}
    path_constructor_names: Set[str] = {"Path"}
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                root = alias.name.split(".", 1)[0]
                if root in _BLOCKED_IMPORT_ROOTS:
                    raise LearningContentGuardError("生成代码包含禁止的外部能力")
                aliases[alias.asname or root] = alias.name
        elif isinstance(node, ast.ImportFrom):
            module = str(node.module or "")
            root = module.split(".", 1)[0]
            if root in _BLOCKED_IMPORT_ROOTS:
                raise LearningContentGuardError("生成代码包含禁止的外部能力")
            for alias in node.names:
                local_name = alias.asname or alias.name
                aliases[local_name] = "{}.{}".format(module, alias.name).strip(".")
                if module == "pathlib" and alias.name == "Path":
                    path_constructor_names.add(local_name)

    for node in ast.walk(tree):
        if isinstance(node, ast.Name) and node.id in _BLOCKED_DYNAMIC_CALLS:
            raise LearningContentGuardError("生成代码包含动态执行或反射能力")
        if isinstance(node, ast.Attribute):
            qualified = _qualified_name(node, aliases)
            if qualified == "os.environ" or node.attr.lower() in _CREDENTIAL_ATTRIBUTE_NAMES:
                raise LearningContentGuardError("生成代码尝试访问凭据")
        if not isinstance(node, ast.Call):
            continue
        qualified = _qualified_name(node.func, aliases)
        root = qualified.split(".", 1)[0]
        if root in _BLOCKED_IMPORT_ROOTS:
            raise LearningContentGuardError("生成代码包含网络或子进程能力")
        if qualified in _BLOCKED_QUALIFIED_CALLS:
            raise LearningContentGuardError("生成代码包含文件破坏或凭据访问能力")
        if isinstance(node.func, ast.Name) and node.func.id in _BLOCKED_DYNAMIC_CALLS:
            raise LearningContentGuardError("生成代码包含动态执行或反射能力")
        if (
            isinstance(node.func, ast.Attribute)
            and node.func.attr in _BLOCKED_PATH_METHODS
            and _is_path_expression(node.func.value, aliases, path_constructor_names)
        ):
            raise LearningContentGuardError("生成代码包含文件破坏能力")

    # Keep the workflow gate aligned with the already verified final export gate.
    # Import lazily so retrieval-only guard usage does not initialize exporters.
    from app.rag.document_conversion.generated_exporter import (
        GeneratedExportError,
        _validate_python_source,
    )

    try:
        _validate_python_source(text, "学习代码")
    except GeneratedExportError as exc:
        raise LearningContentGuardError("生成代码超出允许的学习能力边界") from exc


def _strip_instruction_text(value: str) -> str:
    kept: List[str] = []
    for line in str(value or "").splitlines():
        clauses = re.split(r"(?<=[。！？.!?])\s*", line)
        safe_clauses = [clause for clause in clauses if clause and not _is_instruction(clause)]
        if safe_clauses:
            kept.append("".join(safe_clauses).strip())
    return "\n".join(item for item in kept if item).strip()


def _is_instruction(value: str) -> bool:
    return any(pattern.search(value) for pattern in _INSTRUCTION_PATTERNS)


def _qualified_name(node: ast.AST, aliases: Mapping[str, str]) -> str:
    if isinstance(node, ast.Name):
        return aliases.get(node.id, node.id)
    if isinstance(node, ast.Attribute):
        prefix = _qualified_name(node.value, aliases)
        return "{}.{}".format(prefix, node.attr).strip(".")
    if isinstance(node, ast.Call):
        return _qualified_name(node.func, aliases)
    return ""


def _is_path_expression(
    node: ast.AST,
    aliases: Mapping[str, str],
    path_constructor_names: Set[str],
) -> bool:
    qualified = _qualified_name(node, aliases)
    return (
        qualified == "pathlib.Path"
        or qualified in path_constructor_names
        or qualified.startswith("pathlib.Path.")
    )


__all__ = [
    "LearningContentGuardError",
    "sanitize_learning_references",
    "validate_evidence_ids",
    "validate_generated_python_code",
]
