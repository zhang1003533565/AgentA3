from fastapi import HTTPException

from app.model_providers.runtime_config import (
    LlmRuntimeConfig,
    reset_active_llm_config,
    set_active_llm_config,
)
from app.multi_agents import runtime


def _active_config(model="qwen3.7-max-2026-06-08"):
    return LlmRuntimeConfig(
        provider="aliyun",
        base_url="https://llm.test/v1",
        api_key="test-key",
        model=model,
    )


def test_empty_response_fails_over_without_duplicate_same_model_call(monkeypatch):
    calls = []

    class Provider:
        def complete(self, **kwargs):
            del kwargs
            calls.append(runtime.get_active_llm_config().model)
            return "" if len(calls) == 1 else "valid"

    monkeypatch.setattr(runtime, "get_chat_model_provider", lambda: Provider())
    monkeypatch.setattr(runtime, "LLM_EMPTY_RESPONSE_RETRIES", 0)
    monkeypatch.setattr(runtime, "LLM_MODEL_FALLBACK_MAX_ATTEMPTS", 2)
    token = set_active_llm_config(_active_config())
    try:
        assert runtime._complete_with_model_fallback("ppt_content_agent", "system", "user", "low") == "valid"
    finally:
        reset_active_llm_config(token)

    assert calls == ["qwen3.7-max-2026-06-08", "qwen3.7-max-2026-05-17"]


def test_empty_response_does_not_walk_entire_fallback_chain(monkeypatch):
    calls = []

    class Provider:
        def complete(self, **kwargs):
            del kwargs
            model = runtime.get_active_llm_config().model
            calls.append(model)
            return "valid" if model == "qwen3.7-max-2026-05-17" else ""

    monkeypatch.setattr(runtime, "get_chat_model_provider", lambda: Provider())
    monkeypatch.setattr(runtime, "LLM_EMPTY_RESPONSE_RETRIES", 0)
    monkeypatch.setattr(runtime, "LLM_MODEL_FALLBACK_MAX_ATTEMPTS", 2)
    token = set_active_llm_config(_active_config())
    try:
        assert runtime._complete_with_model_fallback("ppt_content_agent", "system", "user", "low") == "valid"
    finally:
        reset_active_llm_config(token)

    assert calls == ["qwen3.7-max-2026-06-08", "qwen3.7-max-2026-05-17"]


def test_empty_response_retry_can_be_enabled_explicitly(monkeypatch):
    calls = []

    class Provider:
        def complete(self, **kwargs):
            del kwargs
            calls.append(runtime.get_active_llm_config().model)
            return "" if len(calls) == 1 else "valid"

    monkeypatch.setattr(runtime, "get_chat_model_provider", lambda: Provider())
    monkeypatch.setattr(runtime, "LLM_EMPTY_RESPONSE_RETRIES", 1)
    token = set_active_llm_config(_active_config())
    try:
        assert runtime._complete_with_model_fallback("ppt_content_agent", "system", "user", "low") == "valid"
    finally:
        reset_active_llm_config(token)

    assert calls == ["qwen3.7-max-2026-06-08", "qwen3.7-max-2026-06-08"]


def test_transient_502_retries_same_model_before_fallback(monkeypatch):
    calls = []

    class Provider:
        def complete(self, **kwargs):
            del kwargs
            calls.append(runtime.get_active_llm_config().model)
            if len(calls) == 1:
                raise HTTPException(status_code=502, detail="upstream temporarily unavailable")
            return "valid"

    monkeypatch.setattr(runtime, "get_chat_model_provider", lambda: Provider())
    monkeypatch.setattr(runtime, "LLM_SAME_MODEL_RETRIES", 1)
    monkeypatch.setattr(runtime, "LLM_MODEL_FALLBACK_MAX_ATTEMPTS", 2)
    token = set_active_llm_config(_active_config())
    try:
        assert runtime._complete_with_model_fallback("ppt_content_agent", "system", "user", "low") == "valid"
    finally:
        reset_active_llm_config(token)

    assert calls == ["qwen3.7-max-2026-06-08", "qwen3.7-max-2026-06-08"]


def test_configured_cross_provider_fallback_uses_deepseek_after_opencode_failure(monkeypatch):
    calls = []

    class Provider:
        def complete(self, **kwargs):
            del kwargs
            config = runtime.get_active_llm_config()
            calls.append((config.provider, config.model))
            if config.provider == "opencode":
                raise HTTPException(status_code=500, detail="upstream internal server error")
            return "valid from deepseek"

    monkeypatch.setenv("LLM_FALLBACK_PROVIDER", "deepseek")
    monkeypatch.setenv("LLM_FALLBACK_BASE_URL", "https://api.deepseek.com")
    monkeypatch.setenv("LLM_FALLBACK_API_KEY", "fallback-key")
    monkeypatch.setenv("LLM_FALLBACK_MODEL", "deepseek-v4-flash")
    monkeypatch.setattr(runtime, "get_chat_model_provider", lambda: Provider())
    monkeypatch.setattr(runtime, "LLM_SAME_MODEL_RETRIES", 1)
    token = set_active_llm_config(LlmRuntimeConfig(
        provider="opencode",
        base_url="https://opencode.ai/zen/go/v1",
        api_key="primary-key",
        model="ox-alpha-free",
    ))
    try:
        result = runtime._complete_with_model_fallback("ppt_outline_agent", "system", "user", "low")
    finally:
        reset_active_llm_config(token)

    assert result == "valid from deepseek"
    assert calls == [
        ("opencode", "ox-alpha-free"),
        ("opencode", "ox-alpha-free"),
        ("deepseek", "deepseek-v4-flash"),
    ]
