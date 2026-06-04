import time
from typing import Any, Dict, Iterator, List, Optional

from fastapi import HTTPException

from app.model_providers.base import ChatModelProvider
from app.model_providers.multimodal import build_multimodal_human_content, extract_image_references
from app.model_providers.runtime_config import LlmRuntimeConfig, resolve_llm_config
from app.utils.logger import get_logger
from app.utils.prompts import KEYWORD_EXTRACTION_PROMPT, build_search_facts_prompt
from app.utils.text_utils import normalize_base_url, sanitize_keyword

logger = get_logger("providers.qwen")

QWEN_PROVIDER_ALIASES = {
    "qwen",
    "dashscope",
    "aliyun",
    "aliyun_qwen",
    "aliyun-qwen",
    "alibaba_qwen",
    "alibaba-qwen",
    "qwen_openai",
    "qwen-openai",
}
class QwenProvider(ChatModelProvider):
    """Qwen/DashScope provider using the OpenAI-compatible Chat Completions API.

    Text-only models such as qwen-plus/qwen-max can be used normally. Vision models
    such as qwen-vl-plus/qwen-vl-max/qwen3-vl-plus receive image references as
    OpenAI-compatible content parts: text + image_url.
    """

    def __init__(self, config: Optional[LlmRuntimeConfig] = None) -> None:
        try:
            from langchain_openai import ChatOpenAI
        except Exception as exc:
            raise RuntimeError(f"缺少 langchain_openai 依赖: {exc}") from exc

        runtime_config = resolve_llm_config(config)
        if runtime_config.normalized_provider() not in QWEN_PROVIDER_ALIASES:
            raise RuntimeError(f"暂不支持的模型服务商: {runtime_config.provider}")

        api_key = runtime_config.api_key
        base_url = runtime_config.base_url
        model = runtime_config.model

        if not base_url:
            raise RuntimeError("Qwen Base URL 未配置：缺少 X-AI-Base-Url")
        if not api_key:
            raise RuntimeError("Qwen API Key 未配置：缺少 X-AI-Api-Key")
        if not model:
            raise RuntimeError("Qwen 模型未配置：缺少 X-AI-Model")

        self.model = model
        self.llm = ChatOpenAI(
            api_key=api_key,
            base_url=normalize_base_url(base_url),
            model=model,
            temperature=0.2,
            timeout=60,
            max_retries=1,
        )

    def complete(self, system_prompt: str, user_prompt: str) -> str:
        from langchain_core.messages import HumanMessage, SystemMessage

        response = self.llm.invoke([
            SystemMessage(content=system_prompt),
            HumanMessage(content=build_multimodal_human_content(user_prompt)),
        ])
        return str(response.content or "").strip()

    def stream_complete(self, system_prompt: str, user_prompt: str) -> Iterator[str]:
        from langchain_core.messages import HumanMessage, SystemMessage

        messages = [
            SystemMessage(content=system_prompt),
            HumanMessage(content=build_multimodal_human_content(user_prompt)),
        ]
        for chunk in self.llm.stream(messages):
            content = getattr(chunk, "content", "")
            if content:
                yield str(content)

    def extract_search_keyword(self, input_text: str) -> str:
        from langchain_core.messages import HumanMessage, SystemMessage

        text_only_input, image_urls = extract_image_references(input_text)
        keyword_input = text_only_input or ("图片内容" if image_urls else input_text)

        started = time.perf_counter()
        logger.info("extract keyword start input_len=%s images=%s", len(input_text or ""), len(image_urls))
        response = self.llm.invoke([
            SystemMessage(content=KEYWORD_EXTRACTION_PROMPT),
            HumanMessage(content=keyword_input),
        ])
        text = sanitize_keyword(str(response.content))
        if not text:
            raise HTTPException(status_code=502, detail="Qwen 未返回可用检索关键词，已禁止本地关键词兜底")
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        logger.info("extract keyword done elapsed_ms=%s keyword=%s", elapsed_ms, text)
        return text

    def answer(
        self,
        prompt: str,
        input_text: str,
        history: List[Dict[str, str]],
        search_keyword: str,
        search_results: List[Dict[str, Any]],
    ) -> str:
        from langchain_core.messages import HumanMessage, SystemMessage

        messages: List[Any] = [SystemMessage(content=prompt)]
        if search_keyword or search_results:
            messages.append(SystemMessage(content=build_search_facts_prompt(search_keyword, search_results)))
        messages.extend(to_qwen_messages(history))
        messages.append(HumanMessage(content=build_multimodal_human_content(input_text)))

        _, image_urls = extract_image_references(input_text)
        started = time.perf_counter()
        logger.info(
            "answer start messages=%s history=%s images=%s search_keyword=%s matched=%s",
            len(messages),
            len(history or []),
            len(image_urls),
            search_keyword,
            len(search_results or []),
        )
        response = self.llm.invoke(messages)
        answer_text = str(response.content or "").strip()
        if not answer_text:
            raise HTTPException(status_code=500, detail="Qwen 返回内容为空")
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        logger.info("answer done elapsed_ms=%s answer_len=%s", elapsed_ms, len(answer_text))
        return answer_text


def to_qwen_messages(history: List[Dict[str, str]]) -> List[Any]:
    from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

    messages: List[Any] = []
    for item in history or []:
        role = item.get("role")
        content = item.get("content")
        if not role or not content:
            continue
        if role.lower() in {"ai", "assistant"}:
            messages.append(AIMessage(content=content))
        elif role.lower() == "system":
            messages.append(SystemMessage(content=content))
        else:
            messages.append(HumanMessage(content=build_multimodal_human_content(content)))
    return messages
