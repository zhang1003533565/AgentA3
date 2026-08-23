import time
from typing import Any, Dict, Iterator, List, Optional

from fastapi import HTTPException

from app.model_providers.base import ChatModelProvider, extract_response_text
from app.model_providers.deepseek.provider import to_llm_messages
from app.model_providers.multimodal import build_multimodal_human_content
from app.model_providers.runtime_config import (
    LlmRuntimeConfig,
    get_active_llm_timeout_seconds,
    get_active_max_output_tokens,
    resolve_llm_config,
)
from app.observability.langfuse import langchain_callbacks
from app.utils.logger import get_logger
from app.utils.prompts import KEYWORD_EXTRACTION_PROMPT, build_search_facts_prompt
from app.utils.text_utils import normalize_base_url, sanitize_keyword

logger = get_logger("providers.xiaomi")


class XiaomiProvider(ChatModelProvider):
    def __init__(self, config: Optional[LlmRuntimeConfig] = None) -> None:
        try:
            from langchain_openai import ChatOpenAI
        except Exception as exc:
            raise RuntimeError(f"缺少 langchain_openai 依赖: {exc}") from exc

        runtime_config = resolve_llm_config(config)
        if runtime_config.normalized_provider() not in {"xiaomi", "mimo", "xiaomi_mimo", "xiaomi-mimo"}:
            raise RuntimeError(f"暂不支持的模型服务商: {runtime_config.provider}")

        api_key = runtime_config.api_key
        base_url = runtime_config.base_url
        model = runtime_config.model

        if not base_url:
            raise RuntimeError("小米 MiMo Base URL 未配置：建议配置为 https://api.xiaomimimo.com/v1")
        if not api_key:
            raise RuntimeError("小米 MiMo API Key 未配置：缺少 X-AI-Api-Key(ai.service.text.api-key)")
        if not model:
            raise RuntimeError("小米 MiMo 模型未配置：建议配置为 mimo-v2.5-pro")

        self.model = model
        self.llm = ChatOpenAI(
            api_key=api_key,
            base_url=normalize_base_url(base_url),
            model=model,
            temperature=0.2,
            timeout=get_active_llm_timeout_seconds(),
            max_retries=0,
            **({"max_tokens": get_active_max_output_tokens()} if get_active_max_output_tokens() else {}),
            default_headers={"api-key": api_key},
            callbacks=langchain_callbacks(),
        )

    def complete(self, system_prompt: str, user_prompt: str, reasoning_effort: Optional[str] = None) -> str:
        from langchain_core.messages import HumanMessage, SystemMessage

        response = self.llm.invoke([
            SystemMessage(content=system_prompt),
            HumanMessage(content=build_multimodal_human_content(user_prompt)),
        ])
        content = extract_response_text(response)
        if not content and isinstance(getattr(response, "additional_kwargs", None), dict):
            content = str(response.additional_kwargs.get("reasoning_content") or "").strip()
        return content

    def stream_complete(self, system_prompt: str, user_prompt: str, reasoning_effort: Optional[str] = None) -> Iterator[str]:
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

        started = time.perf_counter()
        logger.info("extract keyword start input_len=%s", len(input_text or ""))
        response = self.llm.invoke([
            SystemMessage(content=KEYWORD_EXTRACTION_PROMPT),
            HumanMessage(content=input_text),
        ])
        text = sanitize_keyword(str(response.content))
        if not text:
            raise HTTPException(status_code=502, detail="小米 MiMo 未返回可用检索关键词，已禁止本地关键词兜底")
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
        messages.extend(to_llm_messages(history))
        messages.append(HumanMessage(content=build_multimodal_human_content(input_text)))

        started = time.perf_counter()
        logger.info(
            "answer start messages=%s history=%s search_keyword=%s matched=%s",
            len(messages),
            len(history or []),
            search_keyword,
            len(search_results or []),
        )
        response = self.llm.invoke(messages)
        answer_text = str(response.content or "").strip()
        if not answer_text:
            raise HTTPException(status_code=500, detail="小米 MiMo 返回内容为空")
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        logger.info("answer done elapsed_ms=%s answer_len=%s", elapsed_ms, len(answer_text))
        return answer_text
