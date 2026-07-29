import re
from typing import Any, Dict, Iterable, List, Tuple

MAX_IMAGE_REFERENCES = 8

_MARKDOWN_IMAGE_RE = re.compile(r"!\[[^\]]*\]\((?P<url>[^)\s]+)(?:\s+\"[^\"]*\")?\)")
_DATA_IMAGE_RE = re.compile(r"data:image/[a-zA-Z0-9.+-]+;base64,[^\s\"'`)<>]+")
_IMAGE_URL_RE = re.compile(
    r"https?://[^\s\"'`)<>]+?\.(?:png|jpe?g|gif|webp|bmp|tiff?)(?:\?[^\s\"'`)<>]+)?",
    re.IGNORECASE,
)
_IMAGE_FILE_RE = re.compile(r"\.(?:png|jpe?g|gif|webp|bmp|tiff?)(?:[?#].*)?$", re.IGNORECASE)


def normalize_image_reference(value: Any) -> str:
    text = str(value or "").strip()
    if not text:
        return ""
    if text.startswith("data:image/"):
        return text
    if text.startswith("http://") or text.startswith("https://"):
        return text
    return ""


def _dedupe(urls: Iterable[str]) -> List[str]:
    result: List[str] = []
    seen = set()
    for item in urls:
        url = normalize_image_reference(item)
        if not url or url in seen:
            continue
        result.append(url)
        seen.add(url)
        if len(result) >= MAX_IMAGE_REFERENCES:
            break
    return result


def extract_image_references(text: str) -> Tuple[str, List[str]]:
    """Extract image references from markdown/data-url/image-url text."""
    raw = str(text or "")
    urls: List[str] = []

    def replace_markdown(match: re.Match) -> str:
        urls.append(match.group("url"))
        return ""

    cleaned = _MARKDOWN_IMAGE_RE.sub(replace_markdown, raw)

    for pattern in (_DATA_IMAGE_RE, _IMAGE_URL_RE):
        urls.extend(match.group(0) for match in pattern.finditer(cleaned))
        cleaned = pattern.sub("", cleaned)

    cleaned = re.sub(r"[ \t]{2,}", " ", cleaned)
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned).strip()
    return cleaned, _dedupe(urls)


def collect_image_references(payload: Any) -> List[str]:
    """Collect explicit image URLs/data URLs from request-style payloads."""
    collected: List[str] = []

    def visit(value: Any) -> None:
        if value is None:
            return
        if isinstance(value, str):
            direct = normalize_image_reference(value)
            if direct:
                collected.append(direct)
                return
            _, extracted = extract_image_references(value)
            collected.extend(extracted)
            return
        if isinstance(value, dict):
            for key in (
                "url",
                "src",
                "image",
                "imageUrl",
                "image_url",
                "dataUrl",
                "dataURL",
                "previewDataUrl",
            ):
                if key in value:
                    visit(value.get(key))
            return
        if isinstance(value, (list, tuple, set)):
            for item in value:
                visit(item)

    visit(payload)
    return _dedupe(collected)


def collect_request_image_references(request: Any) -> List[str]:
    values: List[Any] = []
    for attr in ("imageUrls", "images", "imageDataUrls"):
        if hasattr(request, attr):
            values.append(getattr(request, attr))
    values.extend(_image_attachments(getattr(request, "attachments", None)))
    metadata = getattr(request, "metadata", None)
    if isinstance(metadata, dict):
        for key in ("imageUrls", "images", "imageDataUrls"):
            if key in metadata:
                values.append(metadata.get(key))
        values.extend(_image_attachments(metadata.get("attachments")))
    return collect_image_references(values)


def _image_attachments(attachments: Any) -> List[Dict[str, Any]]:
    if not isinstance(attachments, (list, tuple)):
        return []
    result: List[Dict[str, Any]] = []
    for raw in attachments:
        if not isinstance(raw, dict):
            continue
        kind = str(raw.get("type") or raw.get("kind") or "").strip().lower()
        mime_type = str(raw.get("mimeType") or raw.get("contentType") or "").strip().lower()
        url = str(raw.get("url") or raw.get("fileUrl") or raw.get("href") or "").strip()
        name = str(raw.get("name") or raw.get("fileName") or "").strip()
        if kind == "image" or mime_type.startswith("image/") or _IMAGE_FILE_RE.search(url or name):
            result.append(raw)
    return result


def append_image_references_to_text(input_text: str, image_urls: Iterable[str]) -> str:
    explicit_urls = _dedupe(image_urls)
    if not explicit_urls:
        return input_text

    _, existing_urls = extract_image_references(input_text)
    existing = set(existing_urls)
    missing = [url for url in explicit_urls if url not in existing]
    if not missing:
        return input_text

    lines = [f"![用户上传图片{index}]({url})" for index, url in enumerate(missing, start=1)]
    return f"{input_text.rstrip()}\n\n" + "\n".join(lines)


def append_attachment_references_to_text(input_text: str, attachments: Any) -> str:
    """Expose non-image uploads to text-only agents without duplicating image inputs."""
    if not isinstance(attachments, (list, tuple)):
        return input_text
    lines: List[str] = []
    seen = set()
    for raw in attachments[:MAX_IMAGE_REFERENCES]:
        if not isinstance(raw, dict):
            continue
        kind = str(raw.get("type") or raw.get("kind") or "file").strip().lower()
        mime_type = str(raw.get("mimeType") or raw.get("contentType") or "").strip().lower()
        url = str(raw.get("url") or raw.get("fileUrl") or raw.get("href") or "").strip()
        name = str(raw.get("name") or raw.get("fileName") or raw.get("title") or "未命名资源").strip()
        if kind == "image" or mime_type.startswith("image/") or _IMAGE_FILE_RE.search(url or name):
            continue
        if not url or url in seen:
            continue
        seen.add(url)
        lines.append(f"- {name}（{kind or 'file'}）：{url}")
    if not lines:
        return input_text
    return f"{input_text.rstrip()}\n\n用户随本次提问上传的参考资源：\n" + "\n".join(lines)


def build_multimodal_human_content(text: str) -> Any:
    cleaned_text, image_urls = extract_image_references(text)
    if not image_urls:
        return text

    content: List[Dict[str, Any]] = [{"type": "text", "text": cleaned_text or "请根据图片回答用户问题。"}]
    content.extend({"type": "image_url", "image_url": {"url": url}} for url in image_urls)
    return content
