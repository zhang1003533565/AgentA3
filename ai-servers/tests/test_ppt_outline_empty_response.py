from types import SimpleNamespace

from fastapi import HTTPException


def _outline_markdown(page_count=5):
    lines = [
        "## PPT 大纲",
        "",
        "### 大纲信息",
        "- 主题：数据结构",
        "- 受众：学生",
        f"- 建议页数：{page_count} 页",
        "- 整体目标：建立清晰的演示结构",
        "- 风格建议：简洁清晰",
        "",
    ]
    for index in range(1, page_count + 1):
        lines.extend([
            f"### 第{index}页",
            f"- 页标题：第{index}页",
            "- 页面类型：内容页",
            "- 本页目标：理解本页重点",
            "- 核心内容：",
            f"  - 要点{index}",
            "- 展示建议：突出核心信息",
            "- 素材建议：简洁配图",
            "",
        ])
    return "\n".join(lines)


def test_extract_response_text_supports_content_blocks_and_reasoning():
    from app.model_providers.base import extract_response_text

    assert extract_response_text(SimpleNamespace(content="可见答案")) == "可见答案"
    assert extract_response_text(SimpleNamespace(content=[{"type": "text", "text": "分块答案"}])) == "分块答案"
    assert extract_response_text(SimpleNamespace(content="", additional_kwargs={"reasoning_content": "推理模型答案"})) == "推理模型答案"


def test_outline_uses_one_fallback_chain_with_low_reasoning(monkeypatch):
    from app.model_providers.runtime_config import get_active_reasoning_effort
    from app.ppt_generation import service as svc

    calls = []

    def fake_run_specialist_agent(agent_name, prompt, evidence):
        calls.append((agent_name, get_active_reasoning_effort()))
        return _outline_markdown()

    monkeypatch.setattr(svc, "run_specialist_agent", fake_run_specialist_agent)
    service = svc.PptGenerationService()
    result = service.generate_outline({"topic": "数据结构", "sourceContent": "资料内容"}, None)

    assert len(result["items"]) == 5
    assert len(calls) == 1
    assert calls == [
        ("ppt_outline_agent", "low"),
    ]


def test_outline_request_accepts_java_null_optional_fields():
    from app.ppt_generation.routes import OutlineRequest

    request = OutlineRequest(
        sourceName="数据结构",
        sourceContent="资料内容",
        sourceFileId=None,
        outlineMode=None,
        topic=None,
        audience=None,
        tone=None,
    )

    assert request.sourceFileId is None
    assert request.outlineMode is None


def test_qwen_outline_reasoning_is_forwarded_to_bailian(monkeypatch):
    from app.model_providers.qwen.provider import QwenProvider

    calls = []

    class FakeLlm:
        def invoke(self, messages, **kwargs):
            calls.append((messages, kwargs))
            return SimpleNamespace(content="答案")

    provider = QwenProvider.__new__(QwenProvider)
    provider.model = "qwen3.7-flash"
    provider.llm = FakeLlm()

    answer = provider.complete("system", "user", reasoning_effort="medium")

    assert answer == "答案"
    assert calls[0][1]["extra_body"] == {
        "enable_thinking": True,
    }


def test_qwen_reasoning_budget_is_compatible_with_output_cap():
    from app.model_providers.qwen.provider import QwenProvider
    from app.model_providers.runtime_config import (
        reset_active_max_output_tokens,
        set_active_max_output_tokens,
    )

    token = set_active_max_output_tokens(5000)
    try:
        provider = QwenProvider.__new__(QwenProvider)
        provider.model = "qwen3.7-flash"

        assert provider._thinking_extra_body("medium") == {
            "extra_body": {"enable_thinking": True}
        }
    finally:
        reset_active_max_output_tokens(token)


def test_qwen_reasoning_can_be_explicitly_disabled():
    from app.model_providers.qwen.provider import QwenProvider

    provider = QwenProvider.__new__(QwenProvider)
    provider.model = "qwen3.7-flash"

    assert provider._thinking_extra_body("none") == {
        "extra_body": {"enable_thinking": False}
    }


def test_model_quota_failure_switches_to_next_free_model(monkeypatch):
    from app.model_providers.runtime_config import (
        LlmRuntimeConfig,
        get_active_llm_config,
        reset_active_llm_config,
        set_active_llm_config,
    )
    from app.multi_agents import runtime

    calls = []

    class FakeProvider:
        def __init__(self, model):
            self.model = model

        def complete(self, system_prompt, user_prompt, reasoning_effort=None):
            calls.append(self.model)
            if self.model == "qwen3.8-27b":
                raise RuntimeError("429 quota exhausted")
            return "可用模型返回内容"

    def fake_get_provider():
        return FakeProvider(get_active_llm_config().model)

    monkeypatch.setattr(runtime, "get_chat_model_provider", fake_get_provider)
    token = set_active_llm_config(LlmRuntimeConfig(
        provider="aliyun",
        base_url="https://llm.test/v1",
        api_key="test-key",
        model="qwen3.8-27b",
    ))
    try:
        answer = runtime.complete_agent("ppt_outline_agent", "主题：数据结构", [])
        assert answer == "可用模型返回内容"
        assert calls == ["deepseek-v4-flash-0731"]
        assert get_active_llm_config().model == "deepseek-v4-flash-0731"
    finally:
        reset_active_llm_config(token)


def test_local_dependency_error_does_not_rotate_models():
    from app.multi_agents.runtime import _is_model_fallback_error

    error = HTTPException(status_code=500, detail="缺少 langchain_openai 依赖")

    assert not _is_model_fallback_error(error)


def test_request_timed_out_is_eligible_for_model_fallback():
    from app.multi_agents.runtime import _is_model_fallback_error

    assert _is_model_fallback_error(RuntimeError("Request timed out."))
