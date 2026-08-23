import json
import json
import logging
import inspect
import os
from pathlib import Path
from typing import Any, Dict, Iterator, List, Optional

from fastapi import HTTPException

from app.model_providers.base import ChatModelProvider
from app.model_providers.factory import get_chat_model_provider
from app.model_providers.runtime_config import (
    get_active_llm_config,
    get_active_reasoning_effort,
    LlmRuntimeConfig,
    set_active_llm_config,
)


AGENT_ROOT = Path(__file__).resolve().parent
logger = logging.getLogger("multi_agents.runtime")

# 同一套阿里云百炼凭据下的免费文本模型故障切换顺序。
FREE_TEXT_MODEL_FALLBACK_CHAIN = (
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

# 500 不能单独说明是模型额度问题：本地依赖缺失、配置错误也会返回 500。
# 500 只有在错误文本命中模型/网络故障标记时才允许切换。
_MODEL_FALLBACK_STATUS_CODES = {403, 404, 408, 409, 425, 429, 502, 503, 504}
_QWEN_PROVIDER_ALIASES = {
    "qwen", "dashscope", "aliyun", "aliyun_qwen", "aliyun-qwen",
    "alibaba_qwen", "alibaba-qwen", "qwen_openai", "qwen-openai",
}
_MODEL_FALLBACK_MARKERS = (
    "quota", "rate limit", "ratelimit", "too many requests",
    "insufficient balance", "balance insufficient", "access to model",
    "model not found", "model_not_found", "model unavailable",
    "model_unavailable", "permission denied", "temporarily unavailable",
    "connection error", "timeout", "timed out", "额度", "限流", "余额", "模型不存在",
    "模型不可用", "无权访问", "连接错误", "超时",
)
_MODEL_RETRY_STATUS_CODES = {408, 425, 429, 500, 502, 503, 504}
_MODEL_RETRY_MARKERS = (
    "connection", "timeout", "timed out", "temporarily unavailable",
    "rate limit", "ratelimit", "too many requests", "连接", "超时", "限流",
)
LLM_SAME_MODEL_RETRIES = max(0, min(1, int(os.getenv("LLM_SAME_MODEL_RETRIES") or 1)))
# An empty response contains no usable content and is not improved by asking
# the same unstable endpoint again.  Fail over immediately by default; this
# knob remains available for providers where an empty response is known to be
# transient.
LLM_EMPTY_RESPONSE_RETRIES = max(0, min(1, int(os.getenv("LLM_EMPTY_RESPONSE_RETRIES") or 0)))
LLM_MODEL_FALLBACK_MAX_ATTEMPTS = max(
    1,
    min(len(FREE_TEXT_MODEL_FALLBACK_CHAIN), int(os.getenv("LLM_MODEL_FALLBACK_MAX_ATTEMPTS") or 2)),
)


def load_agent_prompt(agent_name: str) -> str:
    prompt_path = AGENT_ROOT / agent_name / "prompt.md"
    try:
        prompt = prompt_path.read_text(encoding="utf-8").strip()
    except FileNotFoundError as exc:
        raise HTTPException(status_code=400, detail=f"智能体缺少 prompt.md：{agent_name}") from exc
    if not prompt:
        raise HTTPException(status_code=400, detail=f"智能体 prompt.md 为空：{agent_name}")
    return prompt


def build_agent_user_prompt(agent_name: str, input_text: str, evidence: List[Dict[str, Any]]) -> str:
    return json.dumps({
        "agent_name": agent_name,
        "user_input": input_text or "",
        "evidence": normalize_evidence(evidence),
        "failure_policy": "如果模型无法完成，请直接说明缺少什么信息或配置；不要输出本地兜底模板。",
    }, ensure_ascii=False, indent=2)


def normalize_evidence(evidence: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    normalized: List[Dict[str, Any]] = []
    for item in evidence or []:
        if not isinstance(item, dict):
            continue
        normalized.append({
            "id": item.get("id"),
            "source": item.get("source") or item.get("name") or item.get("type"),
            "content": str(item.get("content") or item.get("name") or "")[:1200],
            "score": item.get("score"),
            "metadata": item.get("metadata") if isinstance(item.get("metadata"), dict) else {},
        })
        if len(normalized) >= 8:
            break
    return normalized


def get_agent_model(model_provider: Optional[ChatModelProvider] = None) -> ChatModelProvider:
    return model_provider or get_chat_model_provider()


def _provider_supports_reasoning_effort(method: Any) -> bool:
    """Keep injected legacy/test providers compatible with the optional knob."""
    try:
        parameters = inspect.signature(method).parameters.values()
    except (TypeError, ValueError):
        return True
    return any(
        parameter.name == "reasoning_effort"
        or parameter.kind == inspect.Parameter.VAR_KEYWORD
        for parameter in parameters
    )


def _complete_with_provider(provider: ChatModelProvider, system_prompt: str, user_prompt: str, reasoning_effort: Any) -> str:
    kwargs = {"system_prompt": system_prompt, "user_prompt": user_prompt}
    if _provider_supports_reasoning_effort(provider.complete):
        kwargs["reasoning_effort"] = reasoning_effort
    return provider.complete(**kwargs)


def _stream_with_provider(provider: ChatModelProvider, system_prompt: str, user_prompt: str, reasoning_effort: Any) -> Iterator[str]:
    kwargs = {"system_prompt": system_prompt, "user_prompt": user_prompt}
    if _provider_supports_reasoning_effort(provider.stream_complete):
        kwargs["reasoning_effort"] = reasoning_effort
    yield from provider.stream_complete(**kwargs)


def _exception_status_code(error: BaseException) -> Optional[int]:
    for attribute in ("status_code", "status", "http_status"):
        value = getattr(error, attribute, None)
        try:
            if value is not None:
                return int(getattr(value, "value", value))
        except (TypeError, ValueError):
            continue
    response = getattr(error, "response", None)
    value = getattr(response, "status_code", None)
    try:
        return int(getattr(value, "value", value)) if value is not None else None
    except (TypeError, ValueError):
        return None


def _is_model_fallback_error(error: BaseException) -> bool:
    status_code = _exception_status_code(error)
    if status_code in _MODEL_FALLBACK_STATUS_CODES:
        return True
    message = str(error or "").lower()
    return any(marker in message for marker in _MODEL_FALLBACK_MARKERS)


def _is_transient_model_retry_error(error: BaseException) -> bool:
    status_code = _exception_status_code(error)
    if status_code in _MODEL_RETRY_STATUS_CODES:
        return True
    message = str(error or "").lower()
    return any(marker in message for marker in _MODEL_RETRY_MARKERS)


def _configured_fallback_config() -> Optional[LlmRuntimeConfig]:
    """Read an explicit cross-provider fallback without exposing credentials."""
    values = {
        "provider": os.getenv("LLM_FALLBACK_PROVIDER", "").strip(),
        "base_url": os.getenv("LLM_FALLBACK_BASE_URL", "").strip(),
        "api_key": os.getenv("LLM_FALLBACK_API_KEY", "").strip(),
        "model": os.getenv("LLM_FALLBACK_MODEL", "").strip(),
    }
    if not any(values.values()):
        return None
    if not all(values.values()):
        logger.warning(
            "ignoring incomplete cross-provider fallback config provider=%s model=%s",
            values["provider"],
            values["model"],
        )
        return None
    return LlmRuntimeConfig(**values)


def _is_configured_fallback_error(error: BaseException) -> bool:
    """Allow an explicitly configured provider to absorb upstream 4xx/5xx errors."""
    status_code = _exception_status_code(error)
    if status_code in {400, 401, 403, 404, 408, 409, 425, 429, 500, 502, 503, 504}:
        return True
    return _is_model_fallback_error(error)


def _fallback_models(current_model: str) -> List[str]:
    normalized = str(current_model or "").strip().lower()
    for index, model in enumerate(FREE_TEXT_MODEL_FALLBACK_CHAIN):
        if model.lower() == normalized:
            return list(FREE_TEXT_MODEL_FALLBACK_CHAIN[index:])
    # 旧配置如 qwen-plus 不在新免费链中，直接从链首开始，避免继续调用旧模型。
    return list(FREE_TEXT_MODEL_FALLBACK_CHAIN)


def _uses_strict_ppt_recovery(agent_name: str) -> bool:
    return str(agent_name or "").strip().lower().startswith("ppt_")


def _complete_with_model_fallback(
    agent_name: str,
    system_prompt: str,
    user_prompt: str,
    reasoning_effort: Any,
) -> str:
    config = get_active_llm_config()
    if config is None:
        return _complete_with_provider(
            get_chat_model_provider(), system_prompt, user_prompt, reasoning_effort
        )

    last_exception: Optional[BaseException] = None
    strict_ppt_recovery = _uses_strict_ppt_recovery(agent_name)
    configured_fallback = _configured_fallback_config()
    candidates: List[LlmRuntimeConfig] = []
    if config.normalized_provider() in _QWEN_PROVIDER_ALIASES:
        models = _fallback_models(config.model)
        if strict_ppt_recovery:
            models = models[:LLM_MODEL_FALLBACK_MAX_ATTEMPTS]
        candidates.extend(
            LlmRuntimeConfig(
                provider=config.provider,
                base_url=config.base_url,
                api_key=config.api_key,
                model=model,
            )
            for model in models
        )
    else:
        candidates.append(config)
    if configured_fallback and all(candidate.cache_key() != configured_fallback.cache_key() for candidate in candidates):
        candidates.append(configured_fallback)
    if len(candidates) == 1:
        return _complete_with_provider(
            get_chat_model_provider(), system_prompt, user_prompt, reasoning_effort
        )
    # Outline generation already has a configured cross-provider fallback.
    # Retrying the same slow endpoint first can consume the whole task deadline
    # before the fallback model gets a chance to answer.
    transient_retries = 0 if agent_name == "ppt_outline_agent" else (LLM_SAME_MODEL_RETRIES if strict_ppt_recovery else 0)
    empty_response_retries = LLM_EMPTY_RESPONSE_RETRIES if strict_ppt_recovery else 0
    for index, candidate in enumerate(candidates):
        model = candidate.model
        # 成功后保留当前 ContextVar 中的候选模型，使同一个 PPT 任务的后续
        # 批次继续使用已验证可用的模型，而不是每批重新撞击故障主模型。
        set_active_llm_config(candidate)
        for same_model_attempt in range(max(transient_retries, empty_response_retries) + 1):
            try:
                provider = get_chat_model_provider()
                answer = _complete_with_provider(provider, system_prompt, user_prompt, reasoning_effort)
                if str(answer or "").strip():
                    if index > 0:
                        logger.warning(
                            "model fallback succeeded agent=%s provider=%s model=%s fallback_index=%s",
                            agent_name,
                            candidate.provider,
                            model,
                            index,
                        )
                    return answer
                logger.warning(
                    "model returned empty content agent=%s model=%s fallback_index=%s same_model_attempt=%s",
                    agent_name,
                    model,
                    index,
                    same_model_attempt,
                )
                if same_model_attempt < empty_response_retries:
                    logger.warning(
                        "retrying same model after empty response agent=%s model=%s retry=%s",
                        agent_name,
                        model,
                        same_model_attempt + 1,
                    )
                    continue
                break
            except Exception as error:
                last_exception = error
                can_fallback = (
                    _is_configured_fallback_error(error)
                    if index == 0 and configured_fallback is not None
                    else _is_model_fallback_error(error)
                )
                if not can_fallback:
                    raise
                if (
                    same_model_attempt < transient_retries
                    and _is_transient_model_retry_error(error)
                ):
                    logger.warning(
                        "retrying same model after transient error agent=%s model=%s retry=%s error=%s",
                        agent_name,
                        model,
                        same_model_attempt + 1,
                        str(error)[:240],
                    )
                    continue
                logger.warning(
                    "model unavailable, trying fallback agent=%s model=%s fallback_index=%s error=%s",
                    agent_name,
                    model,
                    index,
                    str(error)[:240],
                )
                break

    if last_exception is not None:
        raise last_exception
    return ""


def complete_agent(
    agent_name: str,
    input_text: str,
    evidence: List[Dict[str, Any]],
    model_provider: Optional[ChatModelProvider] = None,
) -> str:
    system_prompt = load_agent_prompt(agent_name)
    user_prompt = build_agent_user_prompt(agent_name, input_text, evidence)
    reasoning_effort = get_active_reasoning_effort()
    if model_provider is not None:
        provider = model_provider
        answer = _complete_with_provider(provider, system_prompt, user_prompt, reasoning_effort)
    else:
        provider = None
        answer = _complete_with_model_fallback(
            agent_name,
            system_prompt,
            user_prompt,
            reasoning_effort,
        )
    if not str(answer or "").strip():
        config = get_active_llm_config()
        logger.warning(
            "agent LLM returned empty content agent=%s provider=%s model=%s provider_class=%s",
            agent_name,
            getattr(config, "provider", "") or "",
            getattr(config, "model", "") or getattr(provider, "model", "") or "",
            provider.__class__.__name__ if provider is not None else "fallback-chain",
        )
    return answer


def stream_agent(
    agent_name: str,
    input_text: str,
    evidence: List[Dict[str, Any]],
    model_provider: Optional[ChatModelProvider] = None,
) -> Iterator[str]:
    from app.model_providers.runtime_config import get_active_reasoning_effort

    provider = get_agent_model(model_provider)
    yield from _stream_with_provider(
        provider,
        load_agent_prompt(agent_name),
        build_agent_user_prompt(agent_name, input_text, evidence),
        get_active_reasoning_effort(),
    )


def complete_agent_or_raise(
    agent_name: str,
    input_text: str,
    evidence: List[Dict[str, Any]],
    model_provider: Optional[ChatModelProvider] = None,
) -> str:
    answer = (complete_agent(agent_name, input_text, evidence, model_provider=model_provider) or "").strip()
    if not answer:
        raise HTTPException(status_code=502, detail=f"{agent_name} LLM 返回内容为空，已禁止本地模板兜底")
    return answer
