"""goal_decomposition_agent 纯 JSON 输出守卫测试。

- 裸 JSON / ```json 围栏 / 前后解释性文字均能解析
- 缺失字段按约定补全（priority 默认中、status pending、is_completed false）
- estimated_days 非法值回退，order_num 强制连续重编号
- 无标题 / 无有效任务直接 502 拒绝
"""

import json

import pytest
from fastapi import HTTPException

from app.multi_agents.goal_decomposition_agent.agent import (
    build_user_prompt,
    parse_goal_payload,
)
from app.services.goal_ai_service import GoalDecompositionAIService


@pytest.fixture()
def clean_llm_env(monkeypatch):
    for field in ("provider", "base_url", "model", "api_key"):
        monkeypatch.delenv(f"LLM_{field.upper()}", raising=False)
    return monkeypatch


def test_runtime_config_uses_env_fallback_when_headers_missing(clean_llm_env):
    clean_llm_env.setenv("LLM_PROVIDER", "opencode")
    clean_llm_env.setenv("LLM_BASE_URL", "https://example.test/v1")
    clean_llm_env.setenv("LLM_MODEL", "deepseek-v4-flash")
    clean_llm_env.setenv("LLM_API_KEY", "env-provided-key")
    config = GoalDecompositionAIService()._runtime_config(None)
    assert (config.provider, config.base_url, config.model) == (
        "opencode", "https://example.test/v1", "deepseek-v4-flash",
    )
    assert config.api_key == "env-provided-key"


def test_passthrough_headers_take_priority_over_env(clean_llm_env):
    clean_llm_env.setenv("LLM_PROVIDER", "opencode")
    clean_llm_env.setenv("LLM_BASE_URL", "https://env.example/v1")
    clean_llm_env.setenv("LLM_MODEL", "env-model")
    clean_llm_env.setenv("LLM_API_KEY", "env-key")
    headers = {
        "provider": "qwen", "base_url": "https://header.example/v1",
        "model": "header-model", "api_key": "header-key",
    }
    config = GoalDecompositionAIService()._runtime_config(headers)
    assert (config.provider, config.model, config.api_key) == ("qwen", "header-model", "header-key")


def test_runtime_config_missing_model_is_none_even_with_partial_headers(clean_llm_env):
    clean_llm_env.setenv("LLM_PROVIDER", "opencode")
    headers = {"provider": "qwen"}
    assert GoalDecompositionAIService()._runtime_config(headers) is None


def _dumps(payload) -> str:
    return json.dumps(payload, ensure_ascii=False)


def _task(**overrides):
    task = {
        "task_name": "学习Python基础语法",
        "stage": "基础阶段",
        "estimated_days": 10,
        "priority": "高",
        "order_num": 1,
        "status": "pending",
        "is_completed": False,
        "description": "掌握变量、循环、函数等基础语法",
    }
    task.update(overrides)
    return task


def test_parse_plain_json_payload():
    payload = parse_goal_payload(
        _dumps(
            {
                "goal": {"title": "30天学会Python爬虫", "description": "零基础到爬虫项目"},
                "tasks": [_task(), _task(task_name="练习", order_num=2), _task(task_name="项目", order_num=3)],
            }
        )
    )
    assert payload["goal"]["title"] == "30天学会Python爬虫"
    assert len(payload["tasks"]) == 3
    assert payload["tasks"][0]["order_num"] == 1


def test_parse_fenced_json_with_prose_noise():
    raw = (
        "好的，以下是拆解结果：\n```json\n"
        '{"goal": {"title": "考研英语复习", "description": ""}, '
        '"tasks": [{"task_name": "背核心单词"}]}'
        "\n```\n希望对你有帮助。"
    )
    payload = parse_goal_payload(raw)
    assert payload["tasks"][0]["task_name"] == "背核心单词"


def test_default_values_are_applied_for_missing_fields():
    payload = parse_goal_payload(
        '{"goal": {"title": "学习计划"}, "tasks": [{"task_name": "任务A"}, {"task_name": "任务B"}]}'
    )
    first, second = payload["tasks"]
    assert first["priority"] == "中"
    assert first["status"] == "pending"
    assert first["is_completed"] is False
    assert first["description"] == ""
    # 缺失阶段继承上一个任务的阶段；首个任务回退默认阶段
    assert first["stage"] == "基础阶段"
    assert second["stage"] == "基础阶段"
    assert [task["order_num"] for task in payload["tasks"]] == [1, 2]


def test_invalid_estimated_days_and_priority_are_normalized():
    raw = (
        '{"goal": {"title": "备考计划"}, '
        '"tasks": ["被丢弃的非对象项", {"task_name": "刷题", "estimated_days": "三天", "priority": "urgent"}, '
        '{"task_name": "复盘", "estimated_days": -4, "priority": "低优先级"}]}'
    )
    payload = parse_goal_payload(raw)
    tasks = payload["tasks"]
    assert len(tasks) == 2
    assert tasks[0]["estimated_days"] == 1 and tasks[0]["priority"] == "中"
    assert tasks[1]["estimated_days"] == 1 and tasks[1]["priority"] == "低"


def test_order_num_is_renumbered_even_if_model_duplicates():
    payload = parse_goal_payload(
        _dumps({"goal": {"title": "计划"}, "tasks": [_task(order_num=7), _task(task_name="第二项", order_num=7)]})
    )
    assert [task["order_num"] for task in payload["tasks"]] == [1, 2]
    # 拆解产物不允许携带完成态
    assert all(not task["is_completed"] for task in payload["tasks"])
    assert all(task["status"] == "pending" for task in payload["tasks"])


def test_task_output_is_capped_at_thirty_items():
    payload = parse_goal_payload(
        _dumps({"goal": {"title": "计划"}, "tasks": [_task(task_name=f"任务{i}") for i in range(35)]})
    )
    assert len(payload["tasks"]) == 30
    assert payload["tasks"][-1]["order_num"] == 30


def test_empty_answer_is_rejected():
    with pytest.raises(HTTPException) as exc_info:
        parse_goal_payload("   ")
    assert exc_info.value.status_code == 502


def test_missing_goal_title_is_rejected():
    with pytest.raises(HTTPException) as exc_info:
        parse_goal_payload('{"goal": {}, "tasks": [{"task_name": "任务A"}]}')
    assert exc_info.value.status_code == 502


def test_tasks_without_valid_entries_are_rejected():
    with pytest.raises(HTTPException) as exc_info:
        parse_goal_payload('{"goal": {"title": "计划"}, "tasks": [{"stage": "基础阶段"}, "文本"]}')
    assert exc_info.value.status_code == 502


def test_user_prompt_contains_content_and_source_hint():
    prompt = build_user_prompt("csv", "任务名称|阶段\n基础|基础阶段")
    assert "CSV" in prompt or "数据表" in prompt
    assert "任务名称|阶段" in prompt
