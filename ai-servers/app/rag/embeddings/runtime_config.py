from contextvars import ContextVar, Token
from typing import Optional

from app.model_providers.runtime_config import LlmRuntimeConfig, build_llm_runtime_config


_active_embedding_config: ContextVar[Optional[LlmRuntimeConfig]] = ContextVar("active_embedding_config", default=None)


def build_embedding_runtime_config(
    provider: Optional[str] = None,
    base_url: Optional[str] = None,
    api_key: Optional[str] = None,
    model: Optional[str] = None,
) -> Optional[LlmRuntimeConfig]:
    return build_llm_runtime_config(provider=provider, base_url=base_url, api_key=api_key, model=model)


def get_active_embedding_config() -> Optional[LlmRuntimeConfig]:
    return _active_embedding_config.get()


def set_active_embedding_config(config: Optional[LlmRuntimeConfig]) -> Token:
    return _active_embedding_config.set(config)


def reset_active_embedding_config(token: Token) -> None:
    _active_embedding_config.reset(token)
