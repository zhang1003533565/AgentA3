import hashlib
from contextvars import ContextVar, Token
from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class LlmRuntimeConfig:
    provider: str = "deepseek"
    base_url: str = ""
    api_key: str = ""
    model: str = ""

    def normalized_provider(self) -> str:
        return (self.provider or "deepseek").strip().lower()

    def cache_key(self) -> tuple[str, str, str, str]:
        api_key_hash = hashlib.sha256((self.api_key or "").encode("utf-8")).hexdigest() if self.api_key else ""
        return (
            self.normalized_provider(),
            self.base_url or "",
            self.model or "",
            api_key_hash,
        )


_active_llm_config: ContextVar[Optional[LlmRuntimeConfig]] = ContextVar("active_llm_config", default=None)


def build_llm_runtime_config(
    provider: Optional[str] = None,
    base_url: Optional[str] = None,
    api_key: Optional[str] = None,
    model: Optional[str] = None,
) -> Optional[LlmRuntimeConfig]:
    values = [provider, base_url, api_key, model]
    if not any((value or "").strip() for value in values):
        return None
    return LlmRuntimeConfig(
        provider=(provider or "deepseek").strip(),
        base_url=(base_url or "").strip(),
        api_key=(api_key or "").strip(),
        model=(model or "").strip(),
    )


def get_active_llm_config() -> Optional[LlmRuntimeConfig]:
    return _active_llm_config.get()


def set_active_llm_config(config: Optional[LlmRuntimeConfig]) -> Token:
    return _active_llm_config.set(config)


def reset_active_llm_config(token: Token) -> None:
    _active_llm_config.reset(token)


def require_active_llm_config() -> LlmRuntimeConfig:
    """Require the per-request LLM config forwarded by the Java backend."""
    active = get_active_llm_config()
    if active is None:
        raise RuntimeError(
            "未收到 Java 后端传入的 LLM 配置。请检查 system_config 中 "
            "ai.service.base-url、ai.service.api-key、ai.service.model 是否已配置，"
            "并确认 Java 已转发 X-AI-* 请求头；AI Server 已禁止本地兜底。"
        )

    missing_headers = []
    if not active.base_url:
        missing_headers.append("X-AI-Base-Url(ai.service.base-url)")
    if not active.api_key:
        missing_headers.append("X-AI-Api-Key(ai.service.api-key)")
    if not active.model:
        missing_headers.append("X-AI-Model(ai.service.model)")
    if missing_headers:
        raise RuntimeError(
            "Java 后端传入的 LLM 配置不完整，缺少："
            + "、".join(missing_headers)
            + "；AI Server 已禁止本地兜底。"
        )
    return active


def resolve_llm_config(config: Optional[LlmRuntimeConfig] = None) -> LlmRuntimeConfig:
    active = config or get_active_llm_config()
    if active is not None:
        return LlmRuntimeConfig(
            provider=active.provider or "deepseek",
            base_url=active.base_url,
            api_key=active.api_key,
            model=active.model,
        )
    return require_active_llm_config()
