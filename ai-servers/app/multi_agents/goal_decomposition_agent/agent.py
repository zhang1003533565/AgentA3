"""学习计划结构化拆解智能体（goal_decomposition_agent）。

输入：用户上传的学习计划文本或数据表序列化文本。
输出：严格 JSON {"goal": {...}, "tasks": [...]}，字段契约见本目录 prompt.md。

与 architecture_ai_service 类似走独立内部接口直连大模型，不经 chat leader 路由，
因此不需要在 multi_agents/catalog.py 注册。模型输出允许夹带传输噪声
（代码块围栏、前后缀说明），由 parse_goal_payload 统一守卫为纯 JSON 结构。
"""

import json
import re
from typing import Any, Dict, List

from fastapi import HTTPException

GOAL_TITLE_MAX_LENGTH = 30
TASK_NAME_MAX_LENGTH = 120
STAGE_MAX_LENGTH = 60
DESCRIPTION_MAX_LENGTH = 500
MAX_TASKS = 30
MAX_SUBTASKS = 6
ALLOWED_PRIORITIES = ("高", "中", "低")
DEFAULT_PRIORITY = "中"
DEFAULT_STAGE = "基础阶段"
MIN_ESTIMATED_DAYS = 1
MAX_ESTIMATED_DAYS = 3650

_JSON_BLOCK_RE = re.compile(r"```(?:json)?\s*([\s\S]*?)```", re.IGNORECASE)

SYSTEM_PROMPT = """你是一名资深学习规划师，负责把用户上传的学习计划或数据表内容拆解为可执行、可勾选的结构化任务。

【输入说明】
- 输入可能是一段学习计划描述文本。
- 也可能是数据表序列化后的文本：第一行为表头，列以 | 分隔；后续每行是一条任务记录。
- 表格常见表头：任务名称、阶段、预计天数、优先级、说明。请按列名对号入座，无法识别的列忽略。

【输出要求】
只输出一个 JSON 对象，禁止输出任何解释性文字、Markdown 代码块标记、前后缀说明。格式：
{
  "goal": {"title": "目标名称", "description": "一句话描述"},
  "tasks": [
    {
      "task_name": "具体任务名",
      "stage": "所属阶段",
      "estimated_days": 3,
      "priority": "高",
      "order_num": 1,
      "status": "pending",
      "is_completed": false,
      "description": "补充说明，可为空字符串",
      "subtasks": [
        {
          "task_name": "一个可在一次学习中完成的动作",
          "description": "完成标准或产出物",
          "estimated_days": 1,
          "order_num": 1
        }
      ]
    }
  ]
}

【字段规则】
- goal.title：提炼目标名称，不超过 30 字
- goal.description：一句话概述目标范围和预期成果，可为空字符串
- tasks[].task_name：具体可执行的任务名
- stage：所属阶段（如 基础阶段/进阶阶段/冲刺阶段），同一阶段命名保持一致
- estimated_days：正整数天数，信息不足时按任务复杂度合理估计
- priority：只能是 高/中/低，信息不足默认 中
- order_num：从 1 开始连续编号，表示执行顺序
- status：固定为 "pending"
- is_completed：固定为 false
- description：补充说明，可为空字符串
- subtasks：预计超过 1 天的父任务必须拆成 2~6 个可执行的叶子步骤；每个步骤应有明确动作和完成标准
- subtasks[].task_name：具体到一次学习行动，不能只是重复父任务标题
- subtasks[].description：完成标准或产出物，可为空字符串
- subtasks[].estimated_days：正整数天数，所有细分任务天数之和应与父任务工作量相符
- subtasks[].order_num：从 1 开始连续编号

【补全规则】
1. 缺失阶段时，根据任务在计划中的先后位置归入合理阶段，并对相邻任务沿用统一命名。
2. 任务数量 3~30 个，按学习顺序排列；输入只有目标没有明细时自行规划合理的里程碑任务。
3. 若输入是表格，逐行识别任务名称、阶段、天数、优先级、说明列；空值按默认规则补全。
4. 不臆造用户未提及的目标之外的范围，不输出任务以外的任何字段。
5. 预计超过 1 天的父任务不得输出空数组；只有原始输入已经是明确的一天原子动作时才允许没有细分任务。
6. 每个细分任务的 description 必须写清完成标准、练习结果或产出物；不能留空。
7. 父任务 estimated_days 必须等于其细分任务 estimated_days 之和。
"""

_SOURCE_TYPE_HINTS = {
    "text": "输入来源：粘贴的学习计划文本。",
    "xlsx": "输入来源：Excel 数据表序列化文本，首行为表头，列以 | 分隔。",
    "csv": "输入来源：CSV 数据表序列化文本，首行为表头，列以 | 分隔。",
}


def build_user_prompt(source_type: str, content: str) -> str:
    """构造拆解请求的用户提示词。"""
    hint = _SOURCE_TYPE_HINTS.get((source_type or "").strip().lower(), _SOURCE_TYPE_HINTS["text"])
    return f"{hint}\n\n请将以下内容拆解为目标与结构化任务：\n\n{content}\n\n只输出约定的 JSON 对象。"


def parse_goal_payload(raw: Any) -> Dict[str, Any]:
    """把大模型原始返回守卫为合法的 { goal, tasks } 结构。

    兼容三类噪声：```json 围栏、JSON 前后的解释性文字、尾随逗号以外的轻微
    非法片段（截取首个 { 到最后一个 } 再解析）。拆解结果必须携带目标标题和
    至少一条有名任务，否则视为无效输出直接拒绝。
    """
    json_str = _extract_json(raw)
    data = _loads_with_fallback(json_str)
    if not isinstance(data, dict):
        raise HTTPException(status_code=502, detail="目标拆解 JSON 顶层必须是对象")

    goal_raw = data.get("goal") if isinstance(data.get("goal"), dict) else {}
    title = _clean_text(goal_raw.get("title"))
    if not title:
        raise HTTPException(status_code=502, detail="目标拆解缺少 goal.title")
    description = _clean_text(goal_raw.get("description"), max_length=DESCRIPTION_MAX_LENGTH)

    raw_tasks = data.get("tasks") if isinstance(data.get("tasks"), list) else []
    tasks: List[Dict[str, Any]] = []
    previous_stage = ""
    for raw_task in raw_tasks[:MAX_TASKS]:
        if not isinstance(raw_task, dict):
            continue
        task_name = _clean_text(raw_task.get("task_name") or raw_task.get("name"), max_length=TASK_NAME_MAX_LENGTH)
        if not task_name:
            continue
        stage = _clean_text(raw_task.get("stage"), max_length=STAGE_MAX_LENGTH) or previous_stage or DEFAULT_STAGE
        previous_stage = stage
        tasks.append(
            {
                "task_name": task_name,
                "stage": stage,
                "estimated_days": _to_positive_int(
                    raw_task.get("estimated_days"),
                    default=MIN_ESTIMATED_DAYS,
                    minimum=MIN_ESTIMATED_DAYS,
                    maximum=MAX_ESTIMATED_DAYS,
                ),
                "priority": _normalize_priority(raw_task.get("priority")),
                # 拆解产物是初始计划：无论模型给什么，完成态一律归零
                "order_num": 0,
                "status": "pending",
                "is_completed": False,
                "description": _clean_text(raw_task.get("description"), max_length=DESCRIPTION_MAX_LENGTH),
                "subtasks": _normalize_subtasks(raw_task.get("subtasks")),
            }
        )
    if not tasks:
        raise HTTPException(status_code=502, detail="目标拆解结果中没有有效任务")

    for index, task in enumerate(tasks, start=1):
        task["order_num"] = index
    return {"goal": {"title": title[:GOAL_TITLE_MAX_LENGTH], "description": description}, "tasks": tasks}


def validate_plan_quality(payload: Dict[str, Any], source_type: str = "text") -> str | None:
    """校验拆解结果是否达到可执行任务树的最低质量。"""
    tasks = payload.get("tasks") if isinstance(payload, dict) else None
    if not isinstance(tasks, list) or not tasks:
        return "没有可执行的父任务"

    for task_index, task in enumerate(tasks, start=1):
        parent_name = str(task.get("task_name") or "").strip()
        parent_days = int(task.get("estimated_days") or MIN_ESTIMATED_DAYS)
        subtasks = task.get("subtasks") if isinstance(task.get("subtasks"), list) else []
        if parent_days > 1 and len(subtasks) < 2:
            return f"第{task_index}个父任务“{parent_name}”预计{parent_days}天，必须拆成至少2个细分任务"
        if not subtasks:
            continue

        child_names = set()
        child_days = 0
        for subtask_index, subtask in enumerate(subtasks, start=1):
            child_name = str(subtask.get("task_name") or "").strip()
            if child_name == parent_name:
                return f"第{task_index}个父任务的第{subtask_index}个细分任务不能重复父任务名称"
            name_key = child_name.casefold()
            if name_key in child_names:
                return f"第{task_index}个父任务存在重复的细分任务“{child_name}”"
            child_names.add(name_key)
            if not str(subtask.get("description") or "").strip():
                return f"第{task_index}个父任务的细分任务“{child_name}”缺少完成标准"
            child_days += int(subtask.get("estimated_days") or MIN_ESTIMATED_DAYS)

        if child_days != parent_days:
            return f"第{task_index}个父任务预计{parent_days}天，但细分任务合计{child_days}天"
    return None


def _normalize_subtasks(value: Any) -> List[Dict[str, Any]]:
    subtasks: List[Dict[str, Any]] = []
    raw_subtasks = value if isinstance(value, list) else []
    for raw_subtask in raw_subtasks:
        if not isinstance(raw_subtask, dict):
            continue
        task_name = _clean_text(
            raw_subtask.get("task_name") or raw_subtask.get("name"),
            max_length=TASK_NAME_MAX_LENGTH,
        )
        if not task_name:
            continue
        subtasks.append(
            {
                "task_name": task_name,
                "description": _clean_text(raw_subtask.get("description"), max_length=DESCRIPTION_MAX_LENGTH),
                "estimated_days": _to_positive_int(
                    raw_subtask.get("estimated_days"),
                    default=MIN_ESTIMATED_DAYS,
                    minimum=MIN_ESTIMATED_DAYS,
                    maximum=MAX_ESTIMATED_DAYS,
                ),
                "order_num": len(subtasks) + 1,
            }
        )
        if len(subtasks) >= MAX_SUBTASKS:
            break
    return subtasks


def _extract_json(raw: Any) -> str:
    text = str(raw or "").strip()
    if not text:
        raise HTTPException(status_code=502, detail="目标拆解 LLM 返回内容为空")
    match = _JSON_BLOCK_RE.search(text)
    if match and match.group(1).strip():
        return match.group(1).strip()
    return text


def _loads_with_fallback(json_str: str) -> Any:
    try:
        return json.loads(json_str)
    except json.JSONDecodeError:
        pass
    start = json_str.find("{")
    end = json_str.rfind("}")
    if start < 0 or end <= start:
        raise HTTPException(status_code=502, detail="目标拆解 LLM 返回的不是合法 JSON")
    try:
        return json.loads(json_str[start : end + 1])
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=502, detail=f"目标拆解 LLM 返回的不是合法 JSON：{exc.msg}") from exc


def _clean_text(value: Any, max_length: int = TASK_NAME_MAX_LENGTH) -> str:
    text = re.sub(r"\s+", " ", str(value if value is not None else "")).strip()
    return text[:max_length]


def _normalize_priority(value: Any) -> str:
    text = str(value if value is not None else "").strip()
    for priority in ALLOWED_PRIORITIES:
        if priority in text:
            return priority
    lowered = text.lower()
    if lowered.startswith(("high", "p0", "p1")):
        return "高"
    if lowered.startswith(("low", "p2")):
        return "低"
    return DEFAULT_PRIORITY


def _to_positive_int(value: Any, default: int, minimum: int, maximum: int) -> int:
    try:
        number = int(float(value))
    except (TypeError, ValueError):
        return default
    if number < minimum:
        return minimum
    return min(number, maximum)


__all__ = [
    "SYSTEM_PROMPT",
    "build_user_prompt",
    "parse_goal_payload",
    "validate_plan_quality",
]
