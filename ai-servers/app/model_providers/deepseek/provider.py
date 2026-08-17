import time
from typing import Any, Dict, Iterator, List, Optional

from fastapi import HTTPException

from app.model_providers.base import ChatModelProvider
from app.model_providers.runtime_config import (
    LlmRuntimeConfig,
    get_active_llm_timeout_seconds,
    resolve_llm_config,
)
from app.observability.langfuse import langchain_callbacks
from app.utils.logger import get_logger
from app.utils.prompts import KEYWORD_EXTRACTION_PROMPT, build_search_facts_prompt
from app.utils.text_utils import normalize_base_url, sanitize_keyword

logger = get_logger("providers.deepseek")


class DeepSeekProvider(ChatModelProvider):
    def __init__(self, config: Optional[LlmRuntimeConfig] = None) -> None:
        try:
            from langchain_openai import ChatOpenAI
        except Exception as exc:
            raise RuntimeError(f"缺少 langchain_openai 依赖: {exc}") from exc

        runtime_config = resolve_llm_config(config)
        if runtime_config.normalized_provider() not in {"deepseek", "openai_compatible", "openai-compatible"}:
            raise RuntimeError(f"暂不支持的模型服务商: {runtime_config.provider}")

        deepseek_api_key = runtime_config.api_key
        deepseek_base_url = runtime_config.base_url
        deepseek_model = runtime_config.model

        if not deepseek_base_url:
            raise RuntimeError("LLM Base URL 未配置：缺少 X-AI-Base-Url(ai.service.text.base-url)")
        if not deepseek_api_key:
            raise RuntimeError("LLM API Key 未配置：缺少 X-AI-Api-Key(ai.service.text.api-key)")
        if not deepseek_model:
            raise RuntimeError("LLM 模型未配置：缺少 X-AI-Model(ai.service.text.model)")

        self.model = deepseek_model
        self.llm = ChatOpenAI(
            api_key=deepseek_api_key,
            base_url=normalize_base_url(deepseek_base_url),
            model=deepseek_model,
            temperature=0.2,
            timeout=get_active_llm_timeout_seconds(),
            max_retries=1,
            callbacks=langchain_callbacks(),
        )

    def complete(self, system_prompt: str, user_prompt: str) -> str:
        from langchain_core.messages import HumanMessage, SystemMessage

        response = self.llm.invoke([
            SystemMessage(content=system_prompt),
            HumanMessage(content=user_prompt),
        ])
        content = str(response.content or "").strip()
        if not content:
            reasoning = (
                response.additional_kwargs.get("reasoning_content")
                if isinstance(response.additional_kwargs, dict)
                else None
            )
            if reasoning:
                content = str(reasoning).strip()
        return content

    def stream_complete(self, system_prompt: str, user_prompt: str) -> Iterator[str]:
        from langchain_core.messages import HumanMessage, SystemMessage

        messages = [
            SystemMessage(content=system_prompt),
            HumanMessage(content=user_prompt),
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
            raise HTTPException(status_code=502, detail="LLM 未返回可用检索关键词，已禁止本地关键词兜底")
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
        messages.append(HumanMessage(content=input_text))

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
            raise HTTPException(status_code=500, detail="LLM 返回内容为空")
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        logger.info("answer done elapsed_ms=%s answer_len=%s", elapsed_ms, len(answer_text))
        return answer_text



def to_llm_messages(history: List[Dict[str, str]]) -> List[Any]:
    from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

    messages: List[Any] = []
    for item in history:
        role = item.get("role")
        content = item.get("content")
        if not role or not content:
            continue
        if role.lower() in {"ai", "assistant"}:
            messages.append(AIMessage(content=content))
        elif role.lower() == "system":
            messages.append(SystemMessage(content=content))
        else:
            messages.append(HumanMessage(content=content))
    return messages
