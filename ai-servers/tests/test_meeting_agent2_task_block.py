# -*- coding: utf-8 -*-
"""
第八步：Agent 2 任务块稳定性与可观测性 —— 单元/契约测试

全部用脚本化的模型输出与工具替身驱动，不依赖真实 LLM 与 Java 后端。
重点验证：
- 任务块存在时正常执行且不 retry
- 无任务块时只在"确有任务信息"的前提下最多复检一次
- 绝不出现第二次以上的重试
- Python 侧不自建去重，重复创建交由后端幂等
"""

import json
from typing import Any, Dict, List

import pytest

from app.multi_agents.meeting_summary_agent import agent as agent_module
from app.multi_agents.meeting_summary_agent import tool_binding
from app.multi_agents.meeting_summary_agent.tool_binding import (
    has_task_signals,
    inspect_task_blocks,
)

FENCE = "```"

MEETING_INPUT = (
    "会议主题：E2E\n"
    "会议状态：已结束\n"
    "会议数字 ID：97\n"
    "参会成员及用户 ID 映射:\n"
    "- zzs [userId=105]\n"
    "- wangli [userId=106]\n"
    "\n重要说明:\n"
    "当会议中出现‘张三负责 XXX’时，创建个人任务时必须使用该 userId 作为 assigneeId。\n"
    "=== 会议记录 ===\n"
    "[说话人：zzs] 测试数据我来负责，周五之前整理完成。\n"
)

PLAIN_MEETING_INPUT = MEETING_INPUT.replace(
    "[说话人：zzs] 测试数据我来负责，周五之前整理完成。",
    "[说话人：zzs] 今天先同步到这里，细节下次再聊。",
)

MINUTES = "## 会议概览\n讨论了进展。\n"

TASKS_BLOCK = (
    f"{FENCE}meeting_tasks\n"
    + json.dumps(
        [{"meetingSessionId": 97, "assigneeId": 105, "assigneeName": "zzs", "title": "整理测试数据",
          "description": "d", "deadline": "周五之前", "evidence": "[说话人：zzs] 测试数据我来负责"}],
        ensure_ascii=False,
    )
    + f"\n{FENCE}\n"
)

COMPLETIONS_BLOCK = (
    f"{FENCE}meeting_task_completions\n"
    + json.dumps([{"taskId": 1, "assigneeId": 105, "evidence": "[说话人：zzs] 测试数据我已经完成了"}],
                 ensure_ascii=False)
    + f"\n{FENCE}\n"
)


@pytest.fixture
def wired(monkeypatch):
    """更直接的替身：只替换底层 HTTP handler，保留 tool_binding 真实解析与分发。"""

    class Wired:
        def __init__(self):
            self.script: List[str] = []
            self.llm_calls: List[str] = []
            self.tool_calls: List[Dict[str, Any]] = []
            self.results: List[Dict[str, Any]] = []

        def fake_complete(self, agent_name, input_text, evidence, model_provider=None):
            self.llm_calls.append(input_text)
            if not self.script:
                raise AssertionError("模型调用次数超出预期（疑似重复重试）")
            return self.script.pop(0)

        async def fake_handler(self, action, authorization, **kwargs):
            self.tool_calls.append({"action": action, **kwargs})
            if self.results:
                return dict(self.results.pop(0))
            return {"success": True, "data": {"id": len(self.tool_calls), "status": "PENDING"}}

    box = Wired()
    monkeypatch.setattr(agent_module, "complete_agent_or_raise", box.fake_complete)
    import app.task_tools.meeting_task_tool as meeting_task_tool
    monkeypatch.setattr(meeting_task_tool, "meeting_task_tool_handler", box.fake_handler)
    return box


def run(wired_box):
    return agent_module.meeting_summary_agent.process(
        MEETING_INPUT, [], chat_service=None, authorization="Bearer token"
    )


# ---------- T1 ----------
def test_t1_normal_new_task_creates_once(wired):
    wired.script = [MINUTES + TASKS_BLOCK]
    answer = run(wired)
    assert [c["action"] for c in wired.tool_calls] == ["create_task"]
    assert wired.tool_calls[0]["title"] == "整理测试数据"
    assert wired.tool_calls[0]["assigneeId"] == 105
    assert "meeting_tasks" not in answer, "内部块必须从纪要中剥离"
    assert len(wired.llm_calls) == 1, "已有任务块时不应 retry"


# ---------- T2 ----------
def test_t2_plain_meeting_does_not_retry(wired):
    wired.script = [MINUTES]
    answer = agent_module.meeting_summary_agent.process(
        PLAIN_MEETING_INPUT, [], chat_service=None, authorization="Bearer token"
    )
    assert not has_task_signals(PLAIN_MEETING_INPUT), "普通会议输入不应含任务信号"
    assert wired.tool_calls == []
    assert len(wired.llm_calls) == 1, "普通会议不应浪费一次复检调用"
    assert answer.strip() == MINUTES.strip()


# ---------- T3 ----------
def test_t3_missing_block_retries_once_and_creates(wired):
    wired.script = [MINUTES, MINUTES + TASKS_BLOCK]
    answer = run(wired)
    assert len(wired.llm_calls) == 2, "第一次无块且存在任务信息时应复检一次"
    assert "【系统复检提示】" in wired.llm_calls[1], "retry 必须携带复检提示"
    assert [c["action"] for c in wired.tool_calls] == ["create_task"]
    assert "meeting_tasks" not in answer


# ---------- T4 ----------
def test_t4_first_block_present_never_retries(wired):
    wired.script = [MINUTES + TASKS_BLOCK]
    run(wired)
    assert len(wired.llm_calls) == 1
    assert len(wired.tool_calls) == 1, "只创建一次"


# ---------- T5 ----------
def test_t5_retry_cap_is_one(wired):
    wired.script = [MINUTES, MINUTES]
    answer = run(wired)
    assert len(wired.llm_calls) == 2, "最多 retry 一次，禁止 retry→retry→..."
    assert wired.tool_calls == []
    assert answer.strip() == MINUTES.strip(), "复检仍无块时保留第一次纪要"


def test_t5b_third_call_is_impossible(wired):
    # 只给两次脚本：若代码尝试第三次调用，fake_complete 会直接抛 AssertionError
    wired.script = [MINUTES, MINUTES]
    run(wired)


# ---------- T6 ----------
def test_t6_owner_completion_confirms(wired):
    wired.script = [MINUTES + COMPLETIONS_BLOCK]
    agent_module.meeting_summary_agent.process(
        MEETING_INPUT, [], chat_service=None, authorization="Bearer token"
    )
    assert [c["action"] for c in wired.tool_calls] == ["confirm_task_completion"]
    assert wired.tool_calls[0]["taskId"] == 1
    assert wired.tool_calls[0]["assigneeId"] == 105
    assert wired.tool_calls[0]["meetingSessionId"] == 97
    assert len(wired.llm_calls) == 1


# ---------- T7 ----------
def test_t7_server_rejection_does_not_break_minutes(wired):
    # Java agent-confirm 拒绝非负责人代报时，Python 必须保留纪要、不谎报成功
    wired.script = [MINUTES + COMPLETIONS_BLOCK]
    wired.results = [{"success": False, "error": "确认失败：发言人是「wangli」，不是任务负责人本人「zzs」"}]
    answer = agent_module.meeting_summary_agent.process(
        MEETING_INPUT, [], chat_service=None, authorization="Bearer token"
    )
    assert len(wired.tool_calls) == 1
    assert "meeting_task_completions" not in answer
    assert "## 会议概览" in answer


# ---------- T8 ----------
def test_t8_duplicate_create_relies_on_backend_idempotency(wired):
    # Python 不自行去重：同标题重复出现时仍交由后端幂等（返回同一任务）
    wired.script = [MINUTES + TASKS_BLOCK + TASKS_BLOCK]
    wired.results = [{"success": True, "data": {"id": 7, "status": "PENDING"}},
                     {"success": True, "data": {"id": 7, "status": "PENDING"}}]
    run(wired)
    assert len(wired.tool_calls) == 2, "Python 不应实现第二套去重"
    assert wired.tool_calls[0]["title"] == wired.tool_calls[1]["title"]


# ---------- 诊断函数 ----------
def test_inspect_detects_malformed_block():
    report = inspect_task_blocks(f"{MINUTES}\n{FENCE}meeting_tasks\n不是JSON\n{FENCE}")
    assert report["has_tasks_block"] is True
    assert report["tasks_count"] == 0
    assert report["malformed_block"] is True, "块存在但解析失败必须可观测"


def test_inspect_completions_block():
    report = inspect_task_blocks(MINUTES + COMPLETIONS_BLOCK)
    assert report["has_completions_block"] is True
    assert report["completions_count"] == 1


def test_signals_ignore_java_preamble_instructions():
    # Java 注入的"重要说明"含"负责"，但转写区无任务时不应触发 retry
    assert has_task_signals(PLAIN_MEETING_INPUT) is False
    assert "负责" in PLAIN_MEETING_INPUT


def test_no_authorization_skips_retry():
    """无授权时重试无意义（工具无法落库），必须只调用一次模型。"""
    calls: List[str] = []

    def fake_complete(agent_name, input_text, evidence, model_provider=None):
        calls.append(input_text)
        return MINUTES

    original = agent_module.complete_agent_or_raise
    agent_module.complete_agent_or_raise = fake_complete
    try:
        agent_module.meeting_summary_agent.process(MEETING_INPUT, [], chat_service=None, authorization=None)
    finally:
        agent_module.complete_agent_or_raise = original
    assert len(calls) == 1
