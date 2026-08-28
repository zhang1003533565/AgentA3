import json
import importlib
from types import SimpleNamespace

from fastapi import HTTPException
import pytest

content_module = importlib.import_module("app.multi_agents.ppt_content_agent.agent")
outline_module = importlib.import_module("app.multi_agents.ppt_outline_agent.agent")
structure_module = importlib.import_module("app.multi_agents.ppt_structure_agent.agent")


def test_content_agent_retries_once_after_invalid_json(monkeypatch):
    answers = iter(["not json", '{"slides":[{"title":"A","content":["内容"]}]}'])
    prompts = []

    def fake_complete(agent_name, input_text, evidence, model_provider=None):
        del agent_name, evidence, model_provider
        prompts.append(input_text)
        return next(answers)

    monkeypatch.setattr(content_module, "complete_agent_or_raise", fake_complete)
    result = json.loads(content_module.ppt_content_agent.process("原始请求", []))

    assert result["slides"][0]["title"] == "A"
    assert len(prompts) == 2
    assert "只返回一个可直接 json.loads 的 JSON 对象" in prompts[1]


def test_structure_agent_retries_once_after_invalid_json(monkeypatch):
    answers = iter(["not json", '{"layouts":[{"slideIndex":1,"layoutId":"title_intro"}]}'])
    prompts = []

    def fake_complete(agent_name, input_text, evidence, model_provider=None):
        del agent_name, evidence, model_provider
        prompts.append(input_text)
        return next(answers)

    monkeypatch.setattr(structure_module, "complete_agent_or_raise", fake_complete)
    result = json.loads(structure_module.ppt_structure_agent.process("原始请求", []))

    assert result["layouts"][0]["layoutId"] == "title_intro"
    assert len(prompts) == 2
    assert "只返回一个可直接 json.loads 的 JSON 对象" in prompts[1]


def test_outline_agent_reports_format_error_to_service_without_hidden_retry(monkeypatch):
    answers = iter(["first response"])
    prompts = []
    normalize_calls = []

    def fake_complete(agent_name, input_text, evidence, model_provider=None):
        del agent_name, evidence, model_provider
        prompts.append(input_text)
        return next(answers)

    monkeypatch.setattr(outline_module, "complete_agent_or_raise", fake_complete)
    def fake_normalize(answer, input_text):
        del input_text
        normalize_calls.append(answer)
        if len(normalize_calls) == 1:
            raise HTTPException(status_code=502, detail="invalid outline")
        return answer

    monkeypatch.setattr(outline_module, "normalize_ppt_outline_answer", fake_normalize)
    with pytest.raises(HTTPException) as error:
        outline_module.ppt_outline_agent.process("原始请求", [])

    assert error.value.status_code == 422
    assert len(prompts) == 1
    assert len(normalize_calls) == 1


@pytest.mark.parametrize(
    "module_name,agent_attr",
    [
        ("content_module", "ppt_content_agent"),
        ("structure_module", "ppt_structure_agent"),
        ("outline_module", "ppt_outline_agent"),
    ],
)
def test_empty_llm_response_does_not_trigger_second_structured_retry(
    monkeypatch, module_name, agent_attr, request
):
    module = globals()[module_name]
    calls = []

    def fake_complete(*args, **kwargs):
        del args, kwargs
        calls.append(True)
        raise HTTPException(status_code=502, detail="ppt_agent LLM 返回内容为空")

    monkeypatch.setattr(module, "complete_agent_or_raise", fake_complete)
    with pytest.raises(HTTPException):
        getattr(module, agent_attr).process("原始请求", [])

    assert len(calls) == 1


def test_opencode_provider_omits_unsupported_reasoning_parameters(monkeypatch):
    provider_module = importlib.import_module("app.model_providers.deepseek.provider")
    captured = {}

    class FakeChatOpenAI:
        def __init__(self, **kwargs):
            captured["constructor"] = kwargs

        def invoke(self, messages, **kwargs):
            del messages
            captured["invoke"] = kwargs
            return SimpleNamespace(content="OK")

    monkeypatch.setattr(provider_module, "langchain_callbacks", lambda: [])
    fake_module = SimpleNamespace(ChatOpenAI=FakeChatOpenAI)
    monkeypatch.setitem(__import__("sys").modules, "langchain_openai", fake_module)

    from app.model_providers.runtime_config import LlmRuntimeConfig

    provider = provider_module.DeepSeekProvider(
        config=LlmRuntimeConfig(
            provider="opencode",
            base_url="https://opencode.ai/zen/go/v1",
            api_key="test-key",
            model="ox-alpha-free",
        )
    )
    assert provider.complete("system", "user", reasoning_effort="medium") == "OK"
    assert "temperature" not in captured["constructor"]
    assert "reasoning_effort" not in captured["constructor"]
    assert captured["invoke"] == {}
