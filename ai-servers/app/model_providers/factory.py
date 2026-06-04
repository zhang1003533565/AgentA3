from typing import Dict

from fastapi import HTTPException

from app.model_providers.base import ChatModelProvider
from app.model_providers.deepseek import DeepSeekProvider
from app.model_providers.qwen import QwenProvider
from app.model_providers.qwen.provider import QWEN_PROVIDER_ALIASES
from app.model_providers.runtime_config import require_active_llm_config
from app.model_providers.xiaomi import XiaomiProvider


_provider_cache: Dict[tuple[str, str, str, str], ChatModelProvider] = {}


def build_chat_model_provider(config) -> ChatModelProvider:
    provider = config.normalized_provider()
    if provider in QWEN_PROVIDER_ALIASES:
        return QwenProvider(config=config)
    if provider in {"xiaomi", "mimo", "xiaomi_mimo", "xiaomi-mimo"}:
        return XiaomiProvider(config=config)
    return DeepSeekProvider(config=config)


def get_chat_model_provider() -> ChatModelProvider:
    try:
        config = require_active_llm_config()
    except RuntimeError as exc:
        raise HTTPException(status_code=500, detail=str(exc))

    cache_key = config.cache_key()
    if cache_key in _provider_cache:
        return _provider_cache[cache_key]

    try:
        provider = build_chat_model_provider(config)
        _provider_cache[cache_key] = provider
        return provider
    except RuntimeError as exc:
        raise HTTPException(status_code=500, detail=str(exc))
