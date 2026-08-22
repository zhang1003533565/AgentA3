import time
import os
from typing import Any, Dict, Iterator, List, Optional

from fastapi import HTTPException

from app.model_providers.base import ChatModelProvider, extract_response_text
from app.model_providers.multimodal import build_multimodal_human_content, extract_image_references
from app.model_providers.runtime_config import (
    LlmRuntimeConfig,
    get_active_llm_timeout_seconds,
    get_active_max_output_tokens,
    get_active_reasoning_effort,
    get_active_llm_config,
    resolve_llm_config,
)
from app.observability.langfuse import langchain_callbacks
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

QWEN_MODEL_FALLBACKS = (
    "qwen3.7-max-2026-06-08",
    "qwen3.7-max-2026-05-17",
    "qwen3.7-max-preview",
    "kimi-k3",
    "deepseek-v4-flash-0731",
    "glm-5.2",
    "kimi-k2.7-code",
    "deepseek-v4-pro-0813",
    "qwen3.5-ocr",
    "qwen3.7-plus-2026-05-26",
)

_FALLBACK_STATUS_CODES = {400, 401, 403, 404, 408, 409, 429, 500, 502, 503, 504}
_FALLBACK_ERROR_MARKERS = (
    "quota", "rate limit", "too many requests", "insufficient", "exhausted",
    "free quota", "model not found", "model does not exist", "model unavailable",
    "invalid model", "thinking_budget", "额度", "余额", "限流", "用完", "不可用",
)


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

        self._chat_openai = ChatOpenAI
        self._api_key = api_key
        self._base_url = normalize_base_url(base_url)
        self._max_output_tokens = get_active_max_output_tokens()
        self.model = model
        self.llm = self._build_llm(model)

    def _build_llm(self, model: str):
        return self._chat_openai(
            api_key=self._api_key,
            base_url=self._base_url,
            model=model,
            temperature=0.2,
            timeout=get_active_llm_timeout_seconds(),
            max_retries=0,
            **({"max_tokens": self._max_output_tokens} if self._max_output_tokens else {}),
            callbacks=langchain_callbacks(),
        )

    def _thinking_extra_body(
        self,
        reasoning_effort: Optional[str] = None,
        model_override: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Build DashScope thinking controls for Qwen3 mixed-thinking models.

        `reasoning_effort` is not an OpenAI-standard Chat Completions parameter.
        Bailian expects the Qwen-specific controls in `extra_body`, otherwise the
        application-level reasoning setting is silently ignored.
        """
        model = str(model_override or self.model or "").strip().lower()
        # Short structural calls often have a small visible-output cap. If no
        # caller explicitly asks for reasoning, keep thinking disabled so the
        # model does not consume the whole cap before producing content.
        effort = str(reasoning_effort or get_active_reasoning_effort() or "none").strip().lower()
        if model.startswith("qwen3.8"):
            # qwen3.8 deployments may expose a zero thinking-budget ceiling,
            # and the current DashScope API also documents that qwen3.8-max
            # should not combine explicit thinking_budget with model reasoning
            # controls. Let the server choose its supported default.
            return {"extra_body": {"enable_thinking": effort != "none"}}
        supported = (
            model.startswith("qwen3.7")
            or model.startswith("qwen3.6")
            or model.startswith("qwen3.5")
            or model.startswith("qwen3-")
        )
        if not supported:
            return {}
        if effort == "none":
            return {"extra_body": {"enable_thinking": False}}
        # Do not send an explicit thinking_budget. Different Bailian
        # deployments expose different thinking ceilings (including zero),
        # while the server-side default is valid for all supported models.
        return {"extra_body": {"enable_thinking": True}}

    def _fallback_models(self) -> List[str]:
        # When the request-scoped runtime config is present, the multi-agent
        # runtime owns model rotation. Returning only the active candidate here
        # prevents Provider fallback and Runtime fallback from walking the same
        # ten-model chain twice for one request.
        active_config = get_active_llm_config()
        if active_config is not None and str(active_config.model or "").strip().lower() == str(self.model or "").strip().lower():
            return [self.model]
        configured = [
            item.strip()
            for item in str(os.getenv("LLM_MODEL_FALLBACKS") or "").split(",")
            if item.strip()
        ]
        candidates: List[str] = []
        seen = set()
        for item in [self.model, *configured, *QWEN_MODEL_FALLBACKS]:
            normalized = item.strip()
            key = normalized.lower()
            if normalized and key not in seen:
                seen.add(key)
                candidates.append(normalized)
        return candidates

    @staticmethod
    def _status_code(error: BaseException) -> Optional[int]:
        for value in (
            getattr(error, "status_code", None),
            getattr(getattr(error, "response", None), "status_code", None),
        ):
            try:
                if value is not None:
                    return int(value)
            except (TypeError, ValueError):
                continue
        return None

    @classmethod
    def _should_fallback(cls, error: BaseException) -> bool:
        status = cls._status_code(error)
        if status in _FALLBACK_STATUS_CODES:
            if status != 400:
                return True
            message = str(error).lower()
            return any(marker in message for marker in _FALLBACK_ERROR_MARKERS)
        message = str(error).lower()
        return any(marker in message for marker in _FALLBACK_ERROR_MARKERS)

    def _invoke_with_fallback(
        self,
        messages: List[Any],
        reasoning_effort: Optional[str] = None,
        use_thinking: bool = True,
    ):
        candidates = self._fallback_models()
        for position, model in enumerate(candidates):
            candidate_llm = self.llm if model == self.model else self._build_llm(model)
            try:
                response = candidate_llm.invoke(
                    messages,
                    **(
                        self._thinking_extra_body(reasoning_effort, model_override=model)
                        if use_thinking else {}
                    ),
                )
                if model != self.model:
                    logger.warning("LLM model fallback succeeded from %s to %s", self.model, model)
                    self.model = model
                    self.llm = candidate_llm
                return response
            except Exception as exc:
                has_next = position + 1 < len(candidates)
                if not has_next or not self._should_fallback(exc):
                    raise
                logger.warning(
                    "LLM model failed, trying fallback model current=%s next=%s error=%s",
                    model,
                    candidates[position + 1],
                    str(exc)[:300],
                )
        raise RuntimeError("没有可用的 LLM 模型")

    def complete(self, system_prompt: str, user_prompt: str, reasoning_effort: Optional[str] = None) -> str:
        from langchain_core.messages import HumanMessage, SystemMessage

        response = self._invoke_with_fallback([
            SystemMessage(content=system_prompt),
            HumanMessage(content=build_multimodal_human_content(user_prompt)),
        ], reasoning_effort)
        return extract_response_text(response)

    def stream_complete(self, system_prompt: str, user_prompt: str, reasoning_effort: Optional[str] = None) -> Iterator[str]:
        from langchain_core.messages import HumanMessage, SystemMessage

        messages = [
            SystemMessage(content=system_prompt),
            HumanMessage(content=build_multimodal_human_content(user_prompt)),
        ]
        candidates = self._fallback_models()
        for position, model in enumerate(candidates):
            candidate_llm = self.llm if model == self.model else self._build_llm(model)
            emitted = False
            try:
                for chunk in candidate_llm.stream(
                    messages,
                    **self._thinking_extra_body(reasoning_effort, model_override=model),
                ):
                    content = getattr(chunk, "content", "")
                    if content:
                        emitted = True
                        yield str(content)
                if model != self.model:
                    logger.warning("LLM stream fallback succeeded from %s to %s", self.model, model)
                    self.model = model
                    self.llm = candidate_llm
                return
            except Exception as exc:
                if emitted or position + 1 >= len(candidates) or not self._should_fallback(exc):
                    raise
                logger.warning(
                    "LLM stream model failed before output, trying fallback model current=%s next=%s error=%s",
                    model,
                    candidates[position + 1],
                    str(exc)[:300],
                )

    def extract_search_keyword(self, input_text: str) -> str:
        from langchain_core.messages import HumanMessage, SystemMessage

        text_only_input, image_urls = extract_image_references(input_text)
        keyword_input = text_only_input or ("图片内容" if image_urls else input_text)

        started = time.perf_counter()
        logger.info("extract keyword start input_len=%s images=%s", len(input_text or ""), len(image_urls))
        response = self._invoke_with_fallback([
            SystemMessage(content=KEYWORD_EXTRACTION_PROMPT),
            HumanMessage(content=keyword_input),
        ], use_thinking=False)
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
        response = self._invoke_with_fallback(messages, use_thinking=False)
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
