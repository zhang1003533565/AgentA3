import os
import time
from typing import Any, Dict, List, Optional

from fastapi import HTTPException
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

from app.utils.logger import get_logger
from app.utils.prompts import KEYWORD_EXTRACTION_PROMPT, build_search_facts_prompt
from app.utils.text_utils import normalize_base_url, sanitize_keyword

logger = get_logger("services.langchain")


class LangChainChatService:
    def __init__(self) -> None:
        deepseek_api_key = os.getenv("DEEPSEEK_API_KEY", "")
        deepseek_base_url = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
        deepseek_model = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

        if not deepseek_api_key:
            raise RuntimeError("DEEPSEEK_API_KEY 未配置")
        self.llm = ChatOpenAI(
            api_key=deepseek_api_key,
            base_url=normalize_base_url(deepseek_base_url),
            model=deepseek_model,
            temperature=0.2,
            timeout=60,
            max_retries=1,
        )

    def extract_search_keyword(self, input_text: str) -> str:
        started = time.perf_counter()
        logger.info("extract keyword start input_len=%s", len(input_text or ""))
        response = self.llm.invoke([
            SystemMessage(content=KEYWORD_EXTRACTION_PROMPT),
            HumanMessage(content=input_text),
        ])
        text = sanitize_keyword(str(response.content))
        if not text:
            text = sanitize_keyword(input_text)
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


chat_service: Optional[LangChainChatService] = None


def get_chat_service() -> LangChainChatService:
    global chat_service
    if chat_service is not None:
        return chat_service
    try:
        chat_service = LangChainChatService()
        return chat_service
    except RuntimeError as exc:
        raise HTTPException(status_code=500, detail=str(exc))


def to_llm_messages(history: List[Dict[str, str]]) -> List[Any]:
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
