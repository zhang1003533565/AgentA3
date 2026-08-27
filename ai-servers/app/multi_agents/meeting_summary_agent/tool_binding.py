"""Agent 2 与 meeting_task_tool 的调用适配层（第四步 + 第五步）。

职责（最小闭环，不引入第二套工具协议）：
1. 从 Agent 2 的最终输出中解析 ```meeting_tasks 结构化任务块（第四步：创建任务）
2. 从 Agent 2 的最终输出中解析 ```meeting_task_completions 结构化块
   （第五步：负责人本人明确确认完成的历史任务）
3. 从输入正文提取「会议数字 ID」作为 meetingSessionId
4. 逐条调用 meeting_task_tool 的 create_task / confirm_task_completion
5. 从返回给用户的纪要中剥离内部块（用户只看到干净的会议纪要）
6. 单个工具调用失败只记录日志，不影响纪要生成
"""

import asyncio
import json
import logging
import re
import threading
from typing import Any, Dict, List, Optional, Tuple

logger = logging.getLogger("multi_agents.meeting_summary_agent.tool_binding")

# 内部结构化块标记（均不得展示给用户）
MEETING_TASKS_MARKER = "```meeting_tasks"
COMPLETIONS_MARKER = "```meeting_task_completions"
_INTERNAL_MARKERS = (MEETING_TASKS_MARKER, COMPLETIONS_MARKER)

_BLOCK_RE = re.compile(r"```meeting_tasks\s*\n(.*?)```", re.DOTALL)
_COMPLETIONS_BLOCK_RE = re.compile(r"```meeting_task_completions\s*\n(.*?)```", re.DOTALL)
_MEETING_ID_RE = re.compile(r"会议数字\s*ID[：:]\s*(\d+)")


def extract_meeting_session_id(input_text: str) -> Optional[int]:
    """从输入正文提取数字会议 ID（Java buildAiMinutesInput 写入的「会议数字 ID」行）。"""
    match = _MEETING_ID_RE.search(str(input_text or ""))
    if not match:
        return None
    try:
        return int(match.group(1))
    except (TypeError, ValueError):
        return None


def safe_visible_length(text: str) -> int:
    """流式输出时允许露出的长度：截断任一内部块，并扣留可能成为块开头的前缀。"""
    text = str(text or "")
    visible = len(text)
    for marker in _INTERNAL_MARKERS:
        marker_index = text.find(marker)
        if marker_index >= 0:
            visible = min(visible, marker_index)
    for keep in range(min(max(len(m) for m in _INTERNAL_MARKERS) - 1, len(text)), 0, -1):
        suffix = text[-keep:]
        if any(marker.startswith(suffix) for marker in _INTERNAL_MARKERS):
            visible = min(visible, len(text) - keep)
    return visible


def _parse_json_blocks(answer: str, block_re, block_name: str) -> List[Dict[str, Any]]:
    """解析指定类型的结构化代码块，返回条目列表。"""
    items: List[Dict[str, Any]] = []
    for match in block_re.finditer(str(answer or "")):
        raw = match.group(1).strip()
        if not raw:
            continue
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            logger.warning("%s 块不是合法 JSON，已跳过", block_name)
            continue
        if isinstance(parsed, dict):
            parsed = [parsed]
        if not isinstance(parsed, list):
            logger.warning("%s 块必须是 JSON 数组，已跳过", block_name)
            continue
        for item in parsed:
            if isinstance(item, dict):
                items.append(item)
    return items


def _strip_internal_blocks(answer: str) -> str:
    """从纪要中剥离所有内部结构化块。"""
    cleaned = _BLOCK_RE.sub("", str(answer or ""))
    cleaned = _COMPLETIONS_BLOCK_RE.sub("", cleaned)
    # 兜底：模型漏写闭合围栏时，从最早出现的内部标记处直接截断
    cut = len(cleaned)
    for marker in _INTERNAL_MARKERS:
        marker_index = cleaned.find(marker)
        if marker_index >= 0:
            cut = min(cut, marker_index)
    if cut < len(cleaned):
        cleaned = cleaned[:cut]
    return cleaned.rstrip()


def _normalize_task(item: Dict[str, Any], meeting_session_id: int) -> Tuple[Optional[Dict[str, Any]], Optional[str]]:
    """校验并规范化单个任务参数；无效时返回错误原因。"""
    try:
        assignee_id = int(item.get("assigneeId"))
    except (TypeError, ValueError):
        return None, f"assigneeId 无效：{item.get('assigneeId')}"
    if assignee_id <= 0:
        return None, f"assigneeId 无效：{assignee_id}"
    title = str(item.get("title") or "").strip()
    if not title:
        return None, "title 为空"
    params = {
        "meetingSessionId": meeting_session_id,
        "assigneeId": assignee_id,
        "assigneeName": str(item.get("assigneeName") or "").strip(),
        "title": title,
        "description": str(item.get("description") or "").strip(),
        "deadline": str(item.get("deadline") or "").strip(),
        "evidence": str(item.get("evidence") or "").strip(),
    }
    return params, None


def _normalize_completion(item: Dict[str, Any], meeting_session_id: int) -> Tuple[Optional[Dict[str, Any]], Optional[str]]:
    """校验并规范化单条完成确认参数；无效时返回错误原因。"""
    try:
        task_id = int(item.get("taskId"))
    except (TypeError, ValueError):
        return None, f"taskId 无效：{item.get('taskId')}"
    if task_id <= 0:
        return None, f"taskId 无效：{task_id}"
    try:
        assignee_id = int(item.get("assigneeId"))
    except (TypeError, ValueError):
        return None, f"assigneeId 无效：{item.get('assigneeId')}"
    if assignee_id <= 0:
        return None, f"assigneeId 无效：{assignee_id}"
    evidence = str(item.get("evidence") or "").strip()
    params = {
        "taskId": task_id,
        "assigneeId": assignee_id,
        "meetingSessionId": meeting_session_id,
        "evidence": evidence,
    }
    return params, None


async def _execute_create_tasks(
    tasks: List[Dict[str, Any]],
    meeting_session_id: int,
    authorization: str,
) -> List[Dict[str, Any]]:
    """逐条调用 meeting_task_tool.create_task。单个失败不影响后续。"""
    from app.task_tools.meeting_task_tool import meeting_task_tool_handler

    results: List[Dict[str, Any]] = []
    for item in tasks:
        params, error = _normalize_task(item, meeting_session_id)
        if error is not None:
            logger.warning("跳过无效任务：%s", error)
            results.append({"success": False, "error": error, "title": item.get("title")})
            continue
        try:
            result = await meeting_task_tool_handler(
                action="create_task",
                authorization=authorization,
                **params,
            )
            results.append(result)
            logger.info(
                "create_task 完成 meetingSessionId=%s assigneeId=%s title=%s success=%s",
                meeting_session_id,
                params["assigneeId"],
                params["title"],
                result.get("success"),
            )
        except Exception as exc:  # noqa: BLE001 - 单个任务失败不能中断纪要生成
            logger.exception("create_task 执行异常 title=%s", params["title"])
            results.append({"success": False, "error": str(exc), "title": params["title"]})
    return results


async def _execute_confirm_completions(
    completions: List[Dict[str, Any]],
    meeting_session_id: int,
    authorization: str,
) -> List[Dict[str, Any]]:
    """逐条调用 meeting_task_tool.confirm_task_completion。单个失败不影响后续。

    后端会做最终校验（assigneeId 必须等于任务真实负责人、必须是本会议参会人、
    已完成任务幂等返回），因此本层只做参数形态校验，不做业务判断。
    """
    from app.task_tools.meeting_task_tool import meeting_task_tool_handler

    results: List[Dict[str, Any]] = []
    for item in completions:
        params, error = _normalize_completion(item, meeting_session_id)
        if error is not None:
            logger.warning("跳过无效的完成确认：%s", error)
            results.append({"success": False, "error": error, "taskId": item.get("taskId")})
            continue
        try:
            result = await meeting_task_tool_handler(
                action="confirm_task_completion",
                authorization=authorization,
                **params,
            )
            results.append(result)
            logger.info(
                "confirm_task_completion 完成 taskId=%s assigneeId=%s meetingSessionId=%s success=%s",
                params["taskId"],
                params["assigneeId"],
                meeting_session_id,
                result.get("success"),
            )
        except Exception as exc:  # noqa: BLE001 - 单条确认失败不能中断纪要生成
            logger.exception("confirm_task_completion 执行异常 taskId=%s", params["taskId"])
            results.append({"success": False, "error": str(exc), "taskId": params["taskId"]})
    return results


def _run_coroutine(coro):
    """在同步上下文执行协程；若当前线程已有事件循环则放到独立线程运行。"""
    try:
        asyncio.get_running_loop()
    except RuntimeError:
        return asyncio.run(coro)

    holder: Dict[str, Any] = {}

    def _target():
        holder["value"] = asyncio.run(coro)

    thread = threading.Thread(target=_target, name="meeting-task-tool-exec")
    thread.start()
    thread.join()
    return holder.get("value")


async def process_task_calls_async(
    answer: str,
    authorization: Optional[str],
    input_text: str,
) -> Tuple[str, List[Dict[str, Any]]]:
    """异步总入口：解析内部块 → 执行工具调用 → 返回剥离后的干净纪要和执行结果。"""
    if not str(answer or "").strip():
        return answer or "", []

    tasks = _parse_json_blocks(answer, _BLOCK_RE, "meeting_tasks")
    completions = _parse_json_blocks(answer, _COMPLETIONS_BLOCK_RE, "meeting_task_completions")
    cleaned = _strip_internal_blocks(answer)
    if not tasks and not completions:
        return cleaned, []

    if not authorization:
        logger.warning("内部任务块存在但缺少授权 token，工具调用未执行")
        return cleaned, [{"success": False, "error": "缺少授权 token，工具调用未执行"}]

    meeting_session_id = extract_meeting_session_id(input_text)
    if meeting_session_id is None:
        logger.warning("内部任务块存在但输入中没有会议数字 ID，工具调用未执行")
        return cleaned, [{"success": False, "error": "输入中没有会议数字 ID，工具调用未执行"}]

    results: List[Dict[str, Any]] = []
    if tasks:
        results.extend(await _execute_create_tasks(tasks, meeting_session_id, authorization))
    if completions:
        results.extend(await _execute_confirm_completions(completions, meeting_session_id, authorization))
    return cleaned, results


def process_task_calls(
    answer: str,
    authorization: Optional[str],
    input_text: str,
) -> Tuple[str, List[Dict[str, Any]]]:
    """同步总入口：供智能体 process() 等同步上下文使用。"""
    if not str(answer or "").strip():
        return answer or "", []

    tasks = _parse_json_blocks(answer, _BLOCK_RE, "meeting_tasks")
    completions = _parse_json_blocks(answer, _COMPLETIONS_BLOCK_RE, "meeting_task_completions")
    cleaned = _strip_internal_blocks(answer)
    if not tasks and not completions:
        return cleaned, []

    if not authorization:
        logger.warning("内部任务块存在但缺少授权 token，工具调用未执行")
        return cleaned, [{"success": False, "error": "缺少授权 token，工具调用未执行"}]

    meeting_session_id = extract_meeting_session_id(input_text)
    if meeting_session_id is None:
        logger.warning("内部任务块存在但输入中没有会议数字 ID，工具调用未执行")
        return cleaned, [{"success": False, "error": "输入中没有会议数字 ID，工具调用未执行"}]

    async def _run_all():
        results: List[Dict[str, Any]] = []
        if tasks:
            results.extend(await _execute_create_tasks(tasks, meeting_session_id, authorization))
        if completions:
            results.extend(await _execute_confirm_completions(completions, meeting_session_id, authorization))
        return results

    results = _run_coroutine(_run_all())
    return cleaned, results or []


__all__ = [
    "MEETING_TASKS_MARKER",
    "COMPLETIONS_MARKER",
    "extract_meeting_session_id",
    "process_task_calls",
    "process_task_calls_async",
    "safe_visible_length",
]
