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


MAX_TASK_BLOCK_RETRY = 1

# retry 时追加到用户输入末尾的提示：只提醒"按既有协议补块"，不改变任何识别规则。
TASK_BLOCK_RETRY_HINT = """

---

【系统复检提示】
请重新检查上一份会议分析结果。
如果会议原文中存在符合任务创建或历史任务完成规则的内容，必须按照既有工具调用输出协议
生成对应的 meeting_tasks 或 meeting_task_completions JSON 块（放在纪要最末尾）。
如果不存在符合规则的内容，则不要生成任务块。
不要凭空创建任务。
不要改变任务负责人判断规则。
不要推测负责人。
不要推测截止时间。
必须继续遵守："谁说了什么 ≠ 谁负责什么"；只有任务负责人本人明确表达完成才能确认完成。
"""

# 会议输入中"应当存在任务块"的信号：命中任意一条即认为值得复检一次。
# 只做保守的词法判断，宁可漏触发也不引入新业务规则。
_TASK_SIGNAL_PATTERNS = (
    re.compile(r"我(?:来|会)?负责"),
    re.compile(r"由\s*[\w\u4e00-\u9fa5]{1,20}\s*(?:负责|处理|跟进|整理|完成)"),
    re.compile(r"[\w\u4e00-\u9fa5]{1,20}\s*负责"),
    re.compile(r"(?:安排|指派|分工)\s*[\w\u4e00-\u9fa5]{1,20}"),
    re.compile(r"(?:完成了|已经完成|已完成|做完|整理完|处理完|搞定)"),
)

# Java buildAiMinutesInput 的转写区起点：只扫描真实发言内容。
_TRANSCRIPT_START_RE = re.compile(r"===\s*(?:会议记录|弹幕/聊天)")

# 历史 PENDING 任务清单段本身就是强信号（负责人本人可能在会上确认完成）
_HISTORY_TASK_SECTION_MARKER = "历史待完成任务"


def has_task_signals(input_text: str) -> bool:
    """
    会议输入中是否明显包含任务分工 / 负责人 / 完成表达等信息。

    只扫描转写区与历史任务清单段：Java 注入的「重要说明」「历史任务说明」等指导文字
    本身含有"负责""完成"字样，若扫描全文会让普通会议也误触发 retry。
    """
    text = str(input_text or "")
    if _HISTORY_TASK_SECTION_MARKER in text:
        return True
    transcript = _TRANSCRIPT_START_RE.search(text)
    region = text[transcript.start():] if transcript else text
    return any(pattern.search(region) for pattern in _TASK_SIGNAL_PATTERNS)


def inspect_task_blocks(answer: str) -> Dict[str, Any]:
    """
    统计 Agent 2 输出的诊断信息（第八步可观测性）。

    只返回长度、存在性与条数等指标，绝不返回会议原文或模型原文内容。
    """
    text = str(answer or "")
    tasks = _parse_json_blocks(text, _BLOCK_RE, "meeting_tasks")
    completions = _parse_json_blocks(text, _COMPLETIONS_BLOCK_RE, "meeting_task_completions")
    has_tasks = MEETING_TASKS_MARKER in text
    has_completions = COMPLETIONS_MARKER in text
    marker_present = has_tasks or has_completions
    return {
        "response_length": len(text),
        "has_tasks_block": has_tasks,
        "has_completions_block": has_completions,
        "tasks_count": len(tasks),
        "completions_count": len(completions),
        # 标记存在但一条都没解析出来 => 模型输出了块但结构不合法
        "malformed_block": marker_present and not (tasks or completions),
    }


def format_block_report_for_log(report: Dict[str, Any]) -> str:
    """把诊断结果格式化为固定字段日志片段（不含任何正文内容）。"""
    return (
        "responseLength={response_length} tasksBlock={has_tasks_block} completionsBlock={has_completions_block} "
        "tasksCount={tasks_count} completionsCount={completions_count} malformed={malformed_block}"
    ).format(**report)


__all__ = [
    "MEETING_TASKS_MARKER",
    "COMPLETIONS_MARKER",
    "MAX_TASK_BLOCK_RETRY",
    "TASK_BLOCK_RETRY_HINT",
    "extract_meeting_session_id",
    "format_block_report_for_log",
    "has_task_signals",
    "inspect_task_blocks",
    "process_task_calls",
    "process_task_calls_async",
    "safe_visible_length",
]
