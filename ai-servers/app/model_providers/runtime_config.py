import hashlib
from contextvars import ContextVar, Token
from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class LlmRuntimeConfig:
    provider: str = ""
    base_url: str = ""
    api_key: str = ""
    model: str = ""

    def normalized_provider(self) -> str:
        return (self.provider or "").strip().lower()

    def cache_key(self) -> tuple[str, str, str, str]:
        api_key_hash = hashlib.sha256((self.api_key or "").encode("utf-8")).hexdigest() if self.api_key else ""
        return (
            self.normalized_provider(),
            self.base_url or "",
            self.model or "",
            api_key_hash,
        )


_active_llm_config: ContextVar[Optional[LlmRuntimeConfig]] = ContextVar("active_llm_config", default=None)
_active_llm_timeout_seconds: ContextVar[int] = ContextVar("active_llm_timeout_seconds", default=60)
_active_max_output_tokens: ContextVar[Optional[int]] = ContextVar("active_max_output_tokens", default=None)
# 单次调用的推理强度覆盖（deepseek 等支持 reasoning_effort 的模型）：
# 内容生成等长输出任务需要保留推理，否则模型可能返回空内容
_active_reasoning_effort: ContextVar[Optional[str]] = ContextVar("active_reasoning_effort", default=None)


def set_active_reasoning_effort(effort: Optional[str]) -> Token:
    return _active_reasoning_effort.set(effort)


def reset_active_reasoning_effort(token: Token) -> None:
    _active_reasoning_effort.reset(token)


def get_active_reasoning_effort() -> Optional[str]:
    return _active_reasoning_effort.get()


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
        provider=(provider or "").strip(),
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


def set_active_llm_timeout(seconds: int) -> Token:
    return _active_llm_timeout_seconds.set(max(1, int(seconds)))


def reset_active_llm_timeout(token: Token) -> None:
    _active_llm_timeout_seconds.reset(token)


def get_active_llm_timeout_seconds() -> int:
    return _active_llm_timeout_seconds.get()


def set_active_max_output_tokens(tokens: Optional[int]) -> Token:
    value = None if tokens is None else max(1, int(tokens))
    return _active_max_output_tokens.set(value)


def reset_active_max_output_tokens(token: Token) -> None:
    _active_max_output_tokens.reset(token)


def get_active_max_output_tokens() -> Optional[int]:
    return _active_max_output_tokens.get()


def require_active_llm_config() -> LlmRuntimeConfig:
    """Require the per-request LLM config forwarded by the caller."""
    active = get_active_llm_config()
    if active is None:
        raise RuntimeError(
            "未收到调用方传入的 LLM 配置，请通过 X-AI-* 请求头传入 provider/base-url/api-key/model。"
        )

    missing_headers = []
    if not active.provider:
        missing_headers.append("X-AI-Provider")
    if not active.base_url:
        missing_headers.append("X-AI-Base-Url")
    if not active.api_key:
        missing_headers.append("X-AI-Api-Key")
    if not active.model:
        missing_headers.append("X-AI-Model")
    if missing_headers:
        raise RuntimeError(
            "LLM 配置不完整，缺少："
            + "、".join(missing_headers)
            + "。"
        )
    return active


def resolve_llm_config(config: Optional[LlmRuntimeConfig] = None) -> LlmRuntimeConfig:
    active = config or get_active_llm_config()
    if active is not None:
        return LlmRuntimeConfig(
            provider=active.provider,
            base_url=active.base_url,
            api_key=active.api_key,
            model=active.model,
        )
    return require_active_llm_config()
