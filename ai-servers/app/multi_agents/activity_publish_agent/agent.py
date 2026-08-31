"""活动发布智能体（业务契约实现）。

职责：与后台活动发布人员进行多轮自然语言对话，从对话中提取并补全现有
Activity 表单的 10 个字段，识别缺失/歧义字段并追问，最终输出可回填表单的
结构化数据。

本模块只负责：
- 解析并校验输入契约（userInput / activityDraft / categoryOptions / ...）；
- 构造 LLM 载荷并调用 ``complete_agent_or_raise``；
- 对 LLM 输出执行严格 JSON 校验（见 validate_and_serialize_answer），
  校验失败抛 HTTPException(502)，不本地兜底。

本模块不负责：写数据库、发布活动、修改表单字段、编造业务信息。
"""

import json
import re
from datetime import datetime
from typing import Any, Dict, List

from fastapi import HTTPException

from app.multi_agents.runtime import complete_agent_or_raise


ACTIVITY_FIELDS = (
    "title",
    "organizerName",
    "coverImage",
    "categoryId",
    "maxPeople",
    "location",
    "startTime",
    "endTime",
    "signupEndTime",
    "content",
)

REQUIRED_FIELDS = (
    "title",
    "categoryId",
    "maxPeople",
    "location",
    "startTime",
    "endTime",
    "signupEndTime",
    "content",
)

TEXT_FIELDS = ("title", "organizerName", "coverImage", "location", "content")
TIME_FIELDS = ("startTime", "endTime", "signupEndTime")
AI_GENERATABLE_FIELDS = ("title", "content")
ACTIONS = ("clarify", "draft", "ready")
TOP_LEVEL_KEYS = (
    "action",
    "reply",
    "activity",
    "generatedFields",
    "missingFields",
    "confidentFields",
    "warnings",
)
TIME_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$")
SIGNUP_SKIP_WARNING_TOKENS = ("报名截止", "signupEndTime")


class ActivityPublishAgent:
    name = "activity_publish_agent"

    def process(
        self,
        input_text: str,
        evidence: List[Dict[str, Any]],
        chat_service=None,
    ) -> str:
        payload = build_llm_payload(input_text)
        answer = complete_agent_or_raise(
            self.name,
            json.dumps(payload, ensure_ascii=False),
            evidence or [],
            model_provider=chat_service,
        )
        return validate_and_serialize_answer(answer, payload["categoryOptions"])


activity_publish_agent = ActivityPublishAgent()


def build_llm_payload(input_text: str) -> Dict[str, Any]:
    """解析并校验输入契约，返回交给 LLM 的数据载荷。

    兼容两种输入：
    - 契约 JSON（含 userInput 键）：按原逻辑解析校验；
    - 普通自然语言：自动包装为最小输入契约后继续处理。
    """
    raw = _parse_input_contract(input_text)
    user_input = raw.get("userInput")
    if not isinstance(user_input, str) or not user_input.strip():
        raise HTTPException(status_code=502, detail="activity_publish_agent 输入缺少非空 userInput")

    draft = raw.get("activityDraft")
    if not isinstance(draft, dict):
        raise HTTPException(status_code=502, detail="activity_publish_agent 输入 activityDraft 必须是对象")
    missing_keys = set(ACTIVITY_FIELDS) - set(draft.keys())
    extra_keys = set(draft.keys()) - set(ACTIVITY_FIELDS)
    if missing_keys or extra_keys:
        detail = "activityDraft 必须恰好包含 10 个表单字段"
        if missing_keys:
            detail += "，缺少：" + "、".join(sorted(missing_keys))
        if extra_keys:
            detail += "，多余：" + "、".join(sorted(extra_keys))
        raise HTTPException(status_code=502, detail="activity_publish_agent 输入校验失败：" + detail)
    for field in ACTIVITY_FIELDS:
        validate_activity_value(field, draft[field], context="输入 activityDraft")

    generated = raw.get("generatedFields") or []
    if not isinstance(generated, list) or not all(
        isinstance(item, str) and item in AI_GENERATABLE_FIELDS for item in generated
    ):
        raise HTTPException(status_code=502, detail="activity_publish_agent 输入 generatedFields 只能是 title/content 数组")

    options_raw = raw.get("categoryOptions") or []
    if not isinstance(options_raw, list):
        raise HTTPException(status_code=502, detail="activity_publish_agent 输入 categoryOptions 必须是数组")
    category_options = [
        {"id": item["id"], "name": item["name"]}
        for item in options_raw
        if isinstance(item, dict)
        and isinstance(item.get("id"), int)
        and isinstance(item.get("name"), str)
        and item["name"].strip()
    ]

    current_time = raw.get("currentTime")
    if current_time is not None and not isinstance(current_time, str):
        raise HTTPException(status_code=502, detail="activity_publish_agent 输入 currentTime 必须是字符串")
    conversation_context = raw.get("conversationContext")
    if conversation_context is not None and not isinstance(conversation_context, dict):
        raise HTTPException(status_code=502, detail="activity_publish_agent 输入 conversationContext 必须是对象")

    return {
        "userInput": user_input,
        "activityDraft": dict(draft),
        "generatedFields": list(generated),
        "categoryOptions": category_options,
        "currentTime": current_time,
        "conversationContext": conversation_context or {},
    }


def _parse_input_contract(input_text: str) -> Dict[str, Any]:
    """契约 JSON 原样返回；普通自然语言自动包装为最小输入契约。"""
    value = (input_text or "").strip()
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError:
        parsed = None
    if isinstance(parsed, dict) and isinstance(parsed.get("userInput"), str):
        return parsed
    return {
        "userInput": input_text or "",
        "activityDraft": {field: None for field in ACTIVITY_FIELDS},
        "generatedFields": [],
        "categoryOptions": [],
        "currentTime": None,
        "conversationContext": {},
    }


def validate_and_serialize_answer(answer: str, category_options: List[Dict[str, Any]]) -> str:
    """解析 LLM 输出并按契约执行严格校验，返回可交付的 JSON 字符串。"""
    payload = parse_strict_json_text(answer, context="活动发布智能体输出")
    issues = validate_output_contract(payload, category_options)
    if issues:
        raise HTTPException(
            status_code=502,
            detail="activity_publish_agent 输出的活动草稿 JSON 未通过校验：" + "；".join(issues[:12]),
        )
    return json.dumps(payload, ensure_ascii=False, indent=2)


def parse_strict_json_text(text: str, context: str) -> Dict[str, Any]:
    """严格 JSON 解析：禁止 Markdown 代码块，顶层必须是对象。"""
    value = (text or "").strip()
    if not value:
        raise HTTPException(status_code=502, detail=f"{context}为空")
    if value.startswith("```") or value.endswith("```"):
        raise HTTPException(status_code=502, detail=f"{context}必须直接返回 JSON，不能包含 Markdown 代码块")
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=502, detail=f"{context}不是合法的严格 JSON：{exc}") from exc
    if not isinstance(parsed, dict):
        raise HTTPException(status_code=502, detail=f"{context}顶层必须是 JSON 对象")
    return parsed


def validate_output_contract(payload: Dict[str, Any], category_options: List[Dict[str, Any]]) -> List[str]:
    """按业务契约校验 LLM 输出，返回问题列表（空列表表示通过）。"""
    issues: List[str] = []
    top_keys = set(payload.keys())
    expected_keys = set(TOP_LEVEL_KEYS)
    if top_keys != expected_keys:
        extra = sorted(top_keys - expected_keys)
        missing = sorted(expected_keys - top_keys)
        if extra:
            issues.append(f"顶层不允许出现额外字段：{', '.join(extra)}")
        if missing:
            issues.append(f"顶层缺少字段：{', '.join(missing)}")

    action = payload.get("action")
    if action not in ACTIONS:
        issues.append(f"action 必须是 {'/'.join(ACTIONS)}，实际为 {action!r}")

    reply = payload.get("reply")
    if not isinstance(reply, str) or not reply.strip():
        issues.append("reply 必须是非空字符串")

    activity = payload.get("activity")
    if not isinstance(activity, dict):
        issues.append("activity 必须是对象")
        activity = {}
    else:
        activity_keys = set(activity.keys())
        if activity_keys != set(ACTIVITY_FIELDS):
            extra = sorted(activity_keys - set(ACTIVITY_FIELDS))
            missing = sorted(set(ACTIVITY_FIELDS) - activity_keys)
            if extra:
                issues.append(f"activity 不允许出现额外字段：{', '.join(extra)}")
            if missing:
                issues.append(f"activity 缺少字段：{', '.join(missing)}")

    for field in ACTIVITY_FIELDS:
        if field in activity:
            validate_activity_value(field, activity[field], context="activity", issues=issues)

    start_time = activity.get("startTime")
    end_time = activity.get("endTime")
    signup_end_time = activity.get("signupEndTime")
    parsed_start = parse_datetime(start_time) if start_time else None
    parsed_end = parse_datetime(end_time) if end_time else None
    parsed_signup = parse_datetime(signup_end_time) if signup_end_time else None
    if parsed_start is not None and parsed_end is not None and parsed_end < parsed_start:
        issues.append("endTime 必须不早于 startTime")
    if parsed_start is not None and parsed_signup is not None and parsed_signup > parsed_start:
        issues.append("signupEndTime 必须不晚于 startTime")

    category_id = activity.get("categoryId")
    if category_id is not None:
        option_ids = [item["id"] for item in category_options if isinstance(item, dict)]
        if not option_ids:
            issues.append("categoryId 非空但未提供 categoryOptions，无法校验")
        elif category_id not in option_ids:
            issues.append(f"categoryId={category_id} 不在 categoryOptions 候选内：{sorted(option_ids)}")

    warnings = payload.get("warnings")
    if not isinstance(warnings, list) or not all(isinstance(item, str) for item in warnings):
        issues.append("warnings 必须是字符串数组")
        warnings = []

    generated = payload.get("generatedFields")
    if not isinstance(generated, list) or not all(
        isinstance(item, str) and item in AI_GENERATABLE_FIELDS for item in generated
    ):
        issues.append("generatedFields 只能是 title/content 数组")
        generated = []

    confident = payload.get("confidentFields")
    if not isinstance(confident, list) or not all(isinstance(item, str) for item in confident):
        issues.append("confidentFields 必须是字符串数组")
        confident = []

    missing = payload.get("missingFields")
    if not isinstance(missing, list) or not all(isinstance(item, str) for item in missing):
        issues.append("missingFields 必须是字符串数组")
        missing = []

    non_null_fields = {field for field in ACTIVITY_FIELDS if activity.get(field) is not None}
    generated_set = set(generated)
    if generated_set - set(AI_GENERATABLE_FIELDS):
        issues.append("generatedFields 只能包含 title/content")
    for field in generated_set:
        if activity.get(field) is None:
            issues.append(f"generatedFields 中的 {field} 必须有值")
    if generated_set & set(confident):
        issues.append("generatedFields 与 confidentFields 不能重叠")
    expected_confident = non_null_fields - generated_set
    if set(confident) != expected_confident:
        issues.append(
            "confidentFields 必须等于 activity 非空字段减去 generatedFields，"
            f"实际={sorted(set(confident))}，期望={sorted(expected_confident)}"
        )

    missing_set = set(missing)
    for field in missing_set:
        if field not in REQUIRED_FIELDS:
            issues.append(f"missingFields 只能包含必填字段，出现 {field}")
        elif activity.get(field) is not None:
            issues.append(f"missingFields 中的 {field} 在 activity 中已有值")
    has_signup_skip_warning = any(
        any(token in str(item) for token in SIGNUP_SKIP_WARNING_TOKENS) for item in warnings
    )
    for field in REQUIRED_FIELDS:
        if activity.get(field) is None and field not in missing_set:
            if field == "signupEndTime" and has_signup_skip_warning:
                continue
            issues.append(f"必填字段 {field} 为空且未出现在 missingFields（也未明确不设置）")

    if action == "clarify" and not missing_set:
        issues.append("action=clarify 时 missingFields 不能为空")
    if action == "draft" and (missing_set or not generated_set):
        issues.append("action=draft 要求必填字段齐全且存在未确认的 AI 生成字段")
    if action == "ready" and (missing_set or generated_set):
        issues.append("action=ready 要求必填字段齐全且 generatedFields 为空")

    return issues


def validate_activity_value(field: str, value: Any, context: str, issues: List[str] | None = None) -> None:
    """校验单个 activity 字段值；issues 为空时直接抛 502。"""
    local_issues: List[str] = []
    if value is None:
        return
    if field in TEXT_FIELDS:
        if not isinstance(value, str) or not value.strip():
            local_issues.append(f"{context}.{field} 必须是非空字符串")
    elif field == "categoryId":
        if isinstance(value, bool) or not isinstance(value, int):
            local_issues.append(f"{context}.{field} 必须是整数")
    elif field == "maxPeople":
        if isinstance(value, bool) or not isinstance(value, int) or value < 1:
            local_issues.append(f"{context}.{field} 必须是大于等于 1 的整数")
    elif field in TIME_FIELDS:
        if not isinstance(value, str) or not TIME_PATTERN.match(value) or parse_datetime(value) is None:
            local_issues.append(f"{context}.{field} 必须是 yyyy-MM-dd HH:mm:ss 格式")
    if issues is None:
        if local_issues:
            raise HTTPException(status_code=502, detail="activity_publish_agent " + "；".join(local_issues))
    else:
        issues.extend(local_issues)


def parse_datetime(value: str):
    try:
        return datetime.strptime(value, "%Y-%m-%d %H:%M:%S")
    except (TypeError, ValueError):
        return None


__all__ = [
    "ACTIVITY_FIELDS",
    "REQUIRED_FIELDS",
    "ACTIONS",
    "ActivityPublishAgent",
    "activity_publish_agent",
    "build_llm_payload",
    "validate_and_serialize_answer",
    "parse_strict_json_text",
    "validate_output_contract",
    "validate_activity_value",
]
