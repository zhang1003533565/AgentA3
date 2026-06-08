from typing import Optional

from app.model_providers.deepseek.provider import DeepSeekProvider
from app.model_providers.runtime_config import LlmRuntimeConfig, resolve_llm_config

XFYUN_PROVIDER_ALIASES = {
    "xfyun",
    "iflytek",
    "spark",
    "sparkdesk",
    "spark_desk",
    "spark-desk",
    "xunfei",
    "xunfei_spark",
    "xunfei-spark",
}


class XfyunProvider(DeepSeekProvider):
    """讯飞星火 OpenAI-compatible chat provider.

    The Spark text endpoint is OpenAI compatible, so the runtime behavior is the
    same as the generic chat-completions provider. A separate class keeps alias
    routing, error messages, and catalog ownership explicit.
    """

    def __init__(self, config: Optional[LlmRuntimeConfig] = None) -> None:
        runtime_config = resolve_llm_config(config)
        if runtime_config.normalized_provider() not in XFYUN_PROVIDER_ALIASES:
            raise RuntimeError(f"暂不支持的模型服务商: {runtime_config.provider}")

        normalized_config = LlmRuntimeConfig(
            provider="openai_compatible",
            base_url=runtime_config.base_url,
            api_key=runtime_config.api_key,
            model=runtime_config.model,
        )
        super().__init__(config=normalized_config)
