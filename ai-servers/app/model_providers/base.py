from abc import ABC, abstractmethod
from typing import Any, Dict, Iterator, List, Optional


def _content_to_text(content: Any) -> str:
    """Extract visible text from OpenAI-compatible message content blocks."""
    if isinstance(content, str):
        return content.strip()
    if isinstance(content, dict):
        return _content_to_text(content.get("text") or content.get("content") or "")
    if isinstance(content, (list, tuple)):
        parts: List[str] = []
        for item in content:
            if isinstance(item, str):
                value = item.strip()
            elif isinstance(item, dict):
                value = item.get("text") or item.get("content") or ""
                value = str(value).strip()
            else:
                value = str(getattr(item, "text", "") or getattr(item, "content", "") or "").strip()
            if value:
                parts.append(value)
        return "\n".join(parts).strip()
    return str(content or "").strip()


def extract_response_text(response: Any) -> str:
    """Return visible model text, with compatible reasoning-field fallback.

    Some reasoning models put the answer in ``content`` while others expose
    only a provider-specific reasoning field. The fallback is still model
    output, never a locally invented PPT template.
    """
    if response is None:
        return ""
    content = _content_to_text(getattr(response, "content", None))
    if content:
        return content
    additional_kwargs = getattr(response, "additional_kwargs", {})
    if not isinstance(additional_kwargs, dict):
        additional_kwargs = {}
    for key in ("reasoning_content", "reasoning", "thinking", "analysis"):
        value = _content_to_text(additional_kwargs.get(key))
        if value:
            return value
    return ""


class ChatModelProvider(ABC):
    @abstractmethod
    def extract_search_keyword(self, input_text: str) -> str:
        raise NotImplementedError

    def complete(self, system_prompt: str, user_prompt: str, reasoning_effort: Optional[str] = None) -> str:
        raise NotImplementedError

    def complete_vision(
        self,
        system_prompt: str,
        user_text: str,
        image_urls: List[str],
        reasoning_effort: Optional[str] = None,
    ) -> str:
        raise NotImplementedError

    def stream_complete(
        self,
        system_prompt: str,
        user_prompt: str,
        reasoning_effort: Optional[str] = None,
    ) -> Iterator[str]:
        yield self.complete(system_prompt, user_prompt, reasoning_effort=reasoning_effort)

    @abstractmethod
    def answer(
        self,
        prompt: str,
        input_text: str,
        history: List[Dict[str, str]],
        search_keyword: str,
        search_results: List[Dict[str, Any]],
    ) -> str:
        raise NotImplementedError
