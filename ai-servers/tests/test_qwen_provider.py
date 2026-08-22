from app.model_providers.qwen.provider import QwenProvider
from app.model_providers.runtime_config import (
    reset_active_max_output_tokens,
    set_active_max_output_tokens,
)


def _provider(model: str) -> QwenProvider:
    provider = object.__new__(QwenProvider)
    provider.model = model
    return provider


def test_qwen38_does_not_send_explicit_thinking_budget():
    token = set_active_max_output_tokens(10_000)
    try:
        body = _provider("qwen3.8-27b")._thinking_extra_body("medium")
    finally:
        reset_active_max_output_tokens(token)

    assert body == {"extra_body": {"enable_thinking": True}}


def test_qwen37_uses_server_side_thinking_budget():
    token = set_active_max_output_tokens(4_000)
    try:
        body = _provider("qwen3.7-max-2026-06-08")._thinking_extra_body("medium")
    finally:
        reset_active_max_output_tokens(token)

    assert body == {"extra_body": {"enable_thinking": True}}


def test_provider_switches_model_after_quota_like_failure(monkeypatch):
    class QuotaError(RuntimeError):
        status_code = 429

    class FailingLlm:
        def invoke(self, messages, **kwargs):
            raise QuotaError("free quota exhausted")

    class WorkingLlm:
        def invoke(self, messages, **kwargs):
            return type("Response", (), {"content": "ok"})()

    provider = _provider("qwen3.7-max-2026-06-08")
    provider.llm = FailingLlm()
    provider._build_llm = lambda model: WorkingLlm()
    monkeypatch.setenv("LLM_MODEL_FALLBACKS", "kimi-k3")

    response = provider.complete("system", "user", reasoning_effort="none")

    assert response == "ok"
    assert provider.model == "kimi-k3"
