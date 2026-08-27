"""Agent 2 与 meeting_task_tool 的调用适配层（第四步）。

职责（最小闭环，不引入第二套工具协议）：
1. 从 Agent 2 的最终输出中解析 ```meeting_tasks 结构化任务块
2. 从输入正文提取「会议数字 ID」作为 meetingSessionId
3. 逐条调用 meeting_task_tool 的 create_task 保存到数据库
4. 从返回给用户的纪要中剥离该块（用户只看到干净的会议纪要）
5. 单个任务创建失败只记录日志，不影响纪要生成
"""

import asyncio
import json
import logging
import re
import threading
from typing import Any, Dict, List, Optional, Tuple

logger = logging.getLogger("multi_agents.meeting_summary_agent.tool_binding")

# 任务块标记：```meeting_tasks ... ```
MEETING_TASKS_MARKER = "```meeting_tasks"
_BLOCK_RE = re.compile(r"```meeting_tasks\s*\n(.*?)```", re.DOTALL)
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
    """流式输出时允许露出的长度：截断任务块，并扣留可能成为块开头的前缀。"""
    text = str(text or "")
    marker_index = text.find(MEETING_TASKS_MARKER)
    if marker_index >= 0:
        return marker_index
    for keep in range(min(len(MEETING_TASKS_MARKER) - 1, len(text)), 0, -1):
        if MEETING_TASKS_MARKER.startswith(text[-keep:]):
            return len(text) - keep
    return len(text)


def _parse_task_blocks(answer: str) -> List[Dict[str, Any]]:
    """解析纪要中的 meeting_tasks 代码块，返回任务列表。"""
    tasks: List[Dict[str, Any]] = []
    for match in _BLOCK_RE.finditer(str(answer or "")):
        raw = match.group(1).strip()
        if not raw:
            continue
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            logger.warning("meeting_tasks 块不是合法 JSON，已跳过")
            continue
        if isinstance(parsed, dict):
            parsed = [parsed]
        if not isinstance(parsed, list):
            logger.warning("meeting_tasks 块必须是 JSON 数组，已跳过")
            continue
        for item in parsed:
            if isinstance(item, dict):
                tasks.append(item)
    return tasks


def _strip_task_blocks(answer: str) -> str:
    """从纪要中剥离 meeting_tasks 代码块。"""
    cleaned = _BLOCK_RE.sub("", str(answer or ""))
    # 兜底：模型漏写闭合围栏时，从标记处直接截断
    marker_index = cleaned.find(MEETING_TASKS_MARKER)
    if marker_index >= 0:
        cleaned = cleaned[:marker_index]
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
    """异步总入口：解析任务块 → 执行 create_task → 返回剥离后的干净纪要和执行结果。"""
    if not str(answer or "").strip():
        return answer or "", []

    tasks = _parse_task_blocks(answer)
    cleaned = _strip_task_blocks(answer)
    if not tasks:
        return cleaned, []

    if not authorization:
        logger.warning("meeting_tasks 块存在但缺少授权 token，任务未保存")
        return cleaned, [{"success": False, "error": "缺少授权 token，任务未保存"}]

    meeting_session_id = extract_meeting_session_id(input_text)
    if meeting_session_id is None:
        logger.warning("meeting_tasks 块存在但输入中没有会议数字 ID，任务未保存")
        return cleaned, [{"success": False, "error": "输入中没有会议数字 ID，任务未保存"}]

    results = await _execute_create_tasks(tasks, meeting_session_id, authorization)
    return cleaned, results or []


def process_task_calls(
    answer: str,
    authorization: Optional[str],
    input_text: str,
) -> Tuple[str, List[Dict[str, Any]]]:
    """同步总入口：供智能体 process() 等同步上下文使用。"""
    if not str(answer or "").strip():
        return answer or "", []

    tasks = _parse_task_blocks(answer)
    cleaned = _strip_task_blocks(answer)
    if not tasks:
        return cleaned, []

    if not authorization:
        logger.warning("meeting_tasks 块存在但缺少授权 token，任务未保存")
        return cleaned, [{"success": False, "error": "缺少授权 token，任务未保存"}]

    meeting_session_id = extract_meeting_session_id(input_text)
    if meeting_session_id is None:
        logger.warning("meeting_tasks 块存在但输入中没有会议数字 ID，任务未保存")
        return cleaned, [{"success": False, "error": "输入中没有会议数字 ID，任务未保存"}]

    results = _run_coroutine(_execute_create_tasks(tasks, meeting_session_id, authorization))
    return cleaned, results or []


__all__ = [
    "MEETING_TASKS_MARKER",
    "extract_meeting_session_id",
    "process_task_calls",
    "process_task_calls_async",
    "safe_visible_length",
]
