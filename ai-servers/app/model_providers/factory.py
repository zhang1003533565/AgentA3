from typing import Dict

from fastapi import HTTPException

from app.model_providers.base import ChatModelProvider
from app.observability.langfuse import provider_cache_key
from app.model_providers.deepseek import DeepSeekProvider
from app.model_providers.qwen import QwenProvider
from app.model_providers.qwen.provider import QWEN_PROVIDER_ALIASES
from app.model_providers.runtime_config import get_active_llm_timeout_seconds, require_active_llm_config
from app.model_providers.volcengine import VolcengineProvider
from app.model_providers.volcengine.provider import VOLCENGINE_PROVIDER_ALIASES
from app.model_providers.xfyun import XfyunProvider
from app.model_providers.xfyun.provider import XFYUN_PROVIDER_ALIASES
from app.model_providers.xiaomi import XiaomiProvider


_provider_cache: Dict[tuple[str, str, str, str], ChatModelProvider] = {}


def build_chat_model_provider(config) -> ChatModelProvider:
    provider = config.normalized_provider()
    if provider in QWEN_PROVIDER_ALIASES:
        return QwenProvider(config=config)
    if provider in XFYUN_PROVIDER_ALIASES:
        return XfyunProvider(config=config)
    if provider in VOLCENGINE_PROVIDER_ALIASES:
        return VolcengineProvider(config=config)
    if provider in {"xiaomi", "mimo", "xiaomi_mimo", "xiaomi-mimo"}:
        return XiaomiProvider(config=config)
    return DeepSeekProvider(config=config)


def get_chat_model_provider() -> ChatModelProvider:
    try:
        config = require_active_llm_config()
    except RuntimeError as exc:
        raise HTTPException(status_code=500, detail=str(exc))

    cache_key = (config.cache_key(), provider_cache_key(), get_active_llm_timeout_seconds())
    if cache_key in _provider_cache:
        return _provider_cache[cache_key]

    try:
        provider = build_chat_model_provider(config)
        _provider_cache[cache_key] = provider
        return provider
    except RuntimeError as exc:
        raise HTTPException(status_code=500, detail=str(exc))
