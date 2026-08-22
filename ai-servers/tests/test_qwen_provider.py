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


def test_qwen37_clamps_thinking_budget_below_output_cap():
    token = set_active_max_output_tokens(4_000)
    try:
        body = _provider("qwen3.7-max-2026-06-08")._thinking_extra_body("medium")
    finally:
        reset_active_max_output_tokens(token)

    assert body == {
        "extra_body": {
            "enable_thinking": True,
            "thinking_budget": 3_999,
        }
    }
