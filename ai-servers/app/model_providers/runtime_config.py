import hashlib
import os
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


def require_active_llm_config() -> LlmRuntimeConfig:
    """Resolve LLM config from Java forwarded headers first, then ai-server env fallback."""
    active = get_active_llm_config()
    env_config = _build_env_llm_runtime_config()
    if active is None:
        if env_config is not None:
            return env_config
        raise RuntimeError(
            "未收到 Java 后端传入的 LLM 配置，且 ai-server 环境变量也未配置可用模型。"
        )

    # Java 传入不完整时，允许用 ai-server 环境变量补齐，保证 RAG 可用性。
    if env_config is not None:
        active = LlmRuntimeConfig(
            provider=active.provider or env_config.provider,
            base_url=active.base_url or env_config.base_url,
            api_key=active.api_key or env_config.api_key,
            model=active.model or env_config.model,
        )

    if active is None:
        raise RuntimeError("LLM 配置不可用")

    missing_headers = []
    if not active.provider:
        missing_headers.append("X-AI-Provider(ai.service.text.provider)")
    if not active.base_url:
        missing_headers.append("X-AI-Base-Url(ai.service.text.base-url)")
    if not active.api_key:
        missing_headers.append("X-AI-Api-Key(ai.service.text.api-key)")
    if not active.model:
        missing_headers.append("X-AI-Model(ai.service.text.model)")
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


def _build_env_llm_runtime_config() -> Optional[LlmRuntimeConfig]:
    provider = (os.getenv("LLM_PROVIDER") or "").strip()
    base_url = (os.getenv("LLM_BASE_URL") or "").strip()
    api_key = (os.getenv("LLM_API_KEY") or "").strip()
    model = (os.getenv("LLM_MODEL") or "").strip()
    if provider and base_url and api_key and model:
        return LlmRuntimeConfig(provider=provider, base_url=base_url, api_key=api_key, model=model)

    deepseek_api_key = (os.getenv("DEEPSEEK_API_KEY") or "").strip()
    if deepseek_api_key:
        return LlmRuntimeConfig(
            provider="deepseek",
            base_url=(os.getenv("DEEPSEEK_BASE_URL") or "https://api.deepseek.com").strip(),
            api_key=deepseek_api_key,
            model=(os.getenv("DEEPSEEK_MODEL") or "deepseek-chat").strip(),
        )

    dashscope_api_key = (os.getenv("DASHSCOPE_API_KEY") or "").strip()
    if dashscope_api_key:
        return LlmRuntimeConfig(
            provider="qwen",
            base_url=(os.getenv("QWEN_BASE_URL") or "https://dashscope.aliyuncs.com/compatible-mode/v1").strip(),
            api_key=dashscope_api_key,
            model=(os.getenv("QWEN_MODEL") or "qwen-plus").strip(),
        )

    return None
