from typing import Optional

from app.model_providers.deepseek.provider import DeepSeekProvider
from app.model_providers.runtime_config import LlmRuntimeConfig, resolve_llm_config

VOLCENGINE_PROVIDER_ALIASES = {
    "volcengine",
    "volc",
    "volc_engine",
    "volc-engine",
    "ark",
    "volcengine_ark",
    "volcengine-ark",
    "huoshan",
    "huoshan_ark",
    "huoshan-ark",
}


class VolcengineProvider(DeepSeekProvider):
    """火山引擎方舟 OpenAI-compatible chat provider."""

    def __init__(self, config: Optional[LlmRuntimeConfig] = None) -> None:
        runtime_config = resolve_llm_config(config)
        if runtime_config.normalized_provider() not in VOLCENGINE_PROVIDER_ALIASES:
            raise RuntimeError(f"暂不支持的模型服务商: {runtime_config.provider}")

        normalized_config = LlmRuntimeConfig(
            provider="openai_compatible",
            base_url=runtime_config.base_url,
            api_key=runtime_config.api_key,
            model=runtime_config.model,
        )
        super().__init__(config=normalized_config)
