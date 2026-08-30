"""Structured tool_params builders for campus Java backend service tools."""

from __future__ import annotations

import re
from datetime import date
from typing import Any, Callable, Dict, Optional

from app.utils.text_utils import (
    is_all_semester_schedule_query,
    is_course_count_query,
    is_course_teacher_query,
    is_course_time_query,
    is_schedule_intent,
    is_semester_schedule_query,
    normalize_text,
    parse_course_lookup_keyword,
    parse_requested_date,
    parse_requested_month,
    parse_requested_session,
    parse_requested_week,
    parse_requested_weekday,
    parse_schedule_course_keyword,
)

CAMPUS_SERVICE_TOOL_NAMES = {
    "java_schedule_api",
    "java_activity_api",
    "java_meeting_api",
    "java_canteen_api",
    "java_facility_api",
    "java_secondhand_api",
}

_PARAM_BUILDERS: Dict[str, Callable[[str], Dict[str, Any]]] = {}


def _register(tool_name: str):
    def decorator(func: Callable[[str], Dict[str, Any]]):
        _PARAM_BUILDERS[tool_name] = func
        return func

    return decorator


def _keyword_from_input(input_text: str, domain_tokens: set[str]) -> str:
    text = re.sub(r"[，。！？、,.!?;；:：\n\r\t]", " ", str(input_text or "")).strip()
    if not text:
        return ""
    remove_tokens = {
        "帮我", "请", "一下", "查一下", "查询", "查找", "查", "找", "看看", "看下",
        "有哪些", "有什么", "有没有", "多少", "列表", "推荐", "最近", "今天", "明天",
        "本周", "这周", "我的", "我", "想", "要", "可以", "吗", "呢", "的", "安排",
        "信息", "详情", "状态", "当前", "在", "及其", "以及", "和", "校园",
    } | set(domain_tokens)
    candidate = text
    for token in sorted(remove_tokens, key=len, reverse=True):
        candidate = candidate.replace(token, " ")
    candidate = re.sub(r"\s+", " ", candidate).strip()
    if len(candidate) >= 2:
        return candidate[:60]
    if any(token in text for token in domain_tokens) and len(text) <= 8:
        return ""
    return text[:60] if len(text) >= 2 and not any(token in text for token in domain_tokens) else ""


def extract_activity_keyword(input_text: str) -> str:
    text = re.sub(r"[，。！？、,.!?;；:：\n\r\t]", " ", str(input_text or "")).strip()
    if not text:
        return ""
    match = re.search(r"([^\s]{2,40}?)活动", text)
    if match:
        name = match.group(1).strip()
        name = re.sub(r"^(这个|那个|什么|哪些|校园|最近|当前)+", "", name)
        name = re.sub(r"(这个|那个|怎么样|如何|详情|介绍|值得|评价|好不好|咋样|吗|呢|不)+$", "", name)
        if len(name) >= 2:
            return name[:60]
    keyword = _keyword_from_input(input_text, {"活动", "讲座", "比赛", "报名", "校园活动"})
    if keyword and len(keyword) >= 2:
        return keyword
    if any(token in text for token in ("活动", "讲座", "比赛", "报名")):
        return ""
    return keyword


def _merge_params(base: Dict[str, Any], override: Dict[str, Any]) -> Dict[str, Any]:
    merged = dict(base)
    for key, value in override.items():
        if value is None:
            continue
        if isinstance(value, str) and not value.strip():
            continue
        if isinstance(value, (list, tuple)) and not value:
            continue
        merged[key] = value
    return merged


def _should_clear_spurious_schedule_course_keyword(input_text: str, course_keyword: str) -> bool:
    keyword = normalize_text(course_keyword)
    if not keyword:
        return False
    if parse_course_lookup_keyword(input_text) or is_course_teacher_query(input_text):
        return False
    if is_course_count_query(input_text) or is_course_time_query(input_text):
        return False
    if not is_schedule_intent(input_text):
        return False
    normalized = normalize_text(input_text)
    if re.search(rf"{re.escape(keyword)}.{{0,8}}(今天|今日|明天|后天|周)", normalized):
        return False
    noise_tokens = ("今日", "今天", "明天", "后天", "课表", "查询", "我想", "帮我")
    return any(token in keyword for token in noise_tokens)


@_register("java_schedule_api")
def build_schedule_params(input_text: str) -> Dict[str, Any]:
    requested_week = parse_requested_week(input_text)
    requested_date = parse_requested_date(input_text)
    requested_month = parse_requested_month(input_text)
    requested_weekday = parse_requested_weekday(input_text)
    requested_session = parse_requested_session(input_text)
    course_keyword = parse_schedule_course_keyword(input_text) or ""
    all_semester_scope = is_all_semester_schedule_query(input_text)
    semester_scope = all_semester_scope or is_semester_schedule_query(input_text)
    month_scope = requested_month is not None
    course_lookup_scope = bool(course_keyword) and requested_date is None
    if course_lookup_scope and _should_clear_spurious_schedule_course_keyword(input_text, course_keyword):
        course_keyword = ""
        course_lookup_scope = False

    if all_semester_scope:
        scope = "all_semesters"
    elif requested_week is not None or requested_date is not None:
        scope = "week"
    elif semester_scope or course_lookup_scope or month_scope:
        scope = "semester"
    else:
        scope = "current_week"

    params: Dict[str, Any] = {
        "scope": scope,
        "week": requested_week,
        "weekday": requested_weekday,
        "date": requested_date.isoformat() if requested_date else None,
        "month": list(requested_month) if requested_month else None,
        "courseKeyword": course_keyword,
    }
    if requested_session is not None:
        params["sessionStart"] = requested_session[0]
        params["sessionEnd"] = requested_session[1]
    return params


@_register("java_activity_api")
def build_activity_params(input_text: str) -> Dict[str, Any]:
    text = str(input_text or "")
    keyword = extract_activity_keyword(input_text)
    time_phase = None
    if any(token in text for token in ("可报名", "正在报名", "还能报名", "报名中")):
        time_phase = "upcoming"
    return {
        "mode": "search" if keyword else "list",
        "keyword": keyword,
        "status": "PUBLISHED",
        "timePhase": time_phase,
        "page": 1,
        "size": 10,
    }


@_register("java_meeting_api")
def build_meeting_params(input_text: str) -> Dict[str, Any]:
    keyword = _keyword_from_input(input_text, {"会议", "会议室", "预约", "开会", "日程"})
    params: Dict[str, Any] = {"pageNum": 1, "pageSize": 10}
    if keyword:
        params["keyword"] = keyword
    return params


@_register("java_canteen_api")
def build_canteen_params(input_text: str) -> Dict[str, Any]:
    keyword = _keyword_from_input(input_text, {"食堂", "餐厅", "档口", "菜品", "吃饭", "推荐", "窗口"})
    return {
        "mode": "search" if keyword else "browse",
        "keyword": keyword,
    }


@_register("java_facility_api")
def build_facility_params(input_text: str) -> Dict[str, Any]:
    keyword = _keyword_from_input(input_text, {"设施", "位置", "在哪", "哪里", "地图", "导航", "定位", "怎么走", "路线"})
    text = str(input_text or "")
    navigation_intent = any(token in text for token in ("在哪", "哪里", "导航", "定位", "怎么走", "路线"))
    params: Dict[str, Any] = {"navigationIntent": navigation_intent}
    if keyword:
        params["keyword"] = keyword
    return params


@_register("java_secondhand_api")
def build_secondhand_params(input_text: str) -> Dict[str, Any]:
    keyword = _keyword_from_input(input_text, {"旧物", "二手", "闲置", "物品", "卖", "买", "转让"})
    params: Dict[str, Any] = {"current": 1, "size": 10, "sort": "latest"}
    if keyword:
        params["keyword"] = keyword
    return params


def resolve_campus_tool_params(
    tool_name: str,
    input_text: str,
    llm_params: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    name = str(tool_name or "").strip()
    builder = _PARAM_BUILDERS.get(name)
    if builder is None:
        return dict(llm_params) if isinstance(llm_params, dict) else {}
    built = builder(input_text or "")
    if isinstance(llm_params, dict) and llm_params:
        return _merge_params(built, llm_params)
    return built


def params_from_schedule_dict(params: Dict[str, Any]) -> Dict[str, Any]:
    """Normalize schedule tool_params for java_backend execution."""
    normalized = dict(params or {})
    scope = str(normalized.get("scope") or "current_week").strip()
    if scope not in {"current_week", "week", "semester", "all_semesters"}:
        scope = "current_week"
    normalized["scope"] = scope

    week = normalized.get("week")
    if week is not None:
        try:
            normalized["week"] = int(week)
        except (TypeError, ValueError):
            normalized["week"] = None

    weekday = normalized.get("weekday")
    if weekday is not None:
        try:
            normalized["weekday"] = int(weekday)
        except (TypeError, ValueError):
            normalized["weekday"] = None

    date_text = normalized.get("date")
    if date_text:
        try:
            normalized["date"] = date.fromisoformat(str(date_text)).isoformat()
        except ValueError:
            normalized["date"] = None

    month_val = normalized.get("month")
    if isinstance(month_val, (list, tuple)) and len(month_val) == 2:
        normalized["month"] = [int(month_val[0]), int(month_val[1])]
    else:
        normalized["month"] = None

    for key in ("sessionStart", "sessionEnd"):
        value = normalized.get(key)
        if value is not None:
            try:
                normalized[key] = int(value)
            except (TypeError, ValueError):
                normalized[key] = None

    normalized["courseKeyword"] = str(normalized.get("courseKeyword") or "").strip()
    return normalized
