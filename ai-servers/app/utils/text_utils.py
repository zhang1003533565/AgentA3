import hashlib
import re
from datetime import date, timedelta
from typing import Optional

try:
    from fastapi import HTTPException
except ImportError:  # allows running lightweight tests without fastapi installed
    class HTTPException(Exception):  # type: ignore
        def __init__(self, status_code: int, detail: str):
            super().__init__(detail)
            self.status_code = status_code
            self.detail = detail


def normalize_base_url(url: str) -> str:
    normalized = (url or "").rstrip("/")
    if not normalized:
        raise ValueError("LLM Base URL 未配置：缺少 ai.service.text.base-url")
    if not normalized.endswith("/v1"):
        normalized += "/v1"
    return normalized


def normalize_bearer_token(authorization: Optional[str]) -> str:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或Token无效")
    if authorization.startswith("Bearer "):
        return authorization[7:]
    return authorization


def build_session_token(session_id: str, authorization: str) -> str:
    token = normalize_bearer_token(authorization)
    hashed = hashlib.sha256(token.encode("utf-8")).hexdigest()
    return f"{session_id}_{hashed}"


def normalize_text(text: Optional[str]) -> str:
    if not text:
        return ""
    return re.sub(r"[\s，。！？,.!?？、“”\"'：:（）()]", "", text.lower())


def sanitize_keyword(text: Optional[str]) -> str:
    if not text:
        return ""
    normalized = text.strip()
    normalized = re.sub(r"关键词[:：]?", "", normalized)
    normalized = re.sub(r"[\n\r]", "", normalized)
    normalized = re.sub(r"[。！!？，,；;]", "", normalized)
    normalized = normalized.strip()
    return normalized[:24]


def is_schedule_intent(text: str) -> bool:
    normalized = normalize_text(text)
    patterns = [
        "课表", "课程安排", "下节课", "下一节课", "再下一节课", "后一节课",
        "下一门课", "再下一门课", "下门课", "今天有什么课", "明天有什么课",
        "后天有什么课", "今天有课吗", "明天有课吗", "本周有什么课", "这周有什么课",
        "接下来有什么课", "接下来哪门课", "第几节有课", "这个学期", "这学期",
        "本学期", "当前学期", "所有学期", "全部学期", "课程列表", "全部课程",
    ]
    if any(p in normalized for p in patterns):
        return True
    return bool(re.match(r".*第\d+周.*", normalized)) \
        or bool(re.match(r".*周[一二三四五六日天].*有.*课.*", normalized)) \
        or bool(re.match(r".*星期[一二三四五六日天].*有.*课.*", normalized))


def is_semester_schedule_query(text: str) -> bool:
    normalized = normalize_text(text)
    if not normalized:
        return False
    semester_tokens = ("这个学期", "这学期", "本学期", "当前学期", "整个学期", "一学期")
    course_tokens = ("都有什么课", "有哪些课", "有什么课程", "全部课程", "所有课程", "课程列表", "课表")
    return any(token in normalized for token in semester_tokens) and any(token in normalized for token in course_tokens)


def is_all_semester_schedule_query(text: str) -> bool:
    normalized = normalize_text(text)
    if not normalized:
        return False
    semester_tokens = ("所有学期", "全部学期", "每个学期", "各个学期", "历史学期", "历年学期")
    course_tokens = ("都有什么课", "有哪些课", "有什么课程", "全部课程", "所有课程", "课程列表", "课表", "课程")
    return any(token in normalized for token in semester_tokens) and any(token in normalized for token in course_tokens)


def is_course_teacher_query(text: str) -> bool:
    normalized = normalize_text(text)
    if not normalized:
        return False
    teacher_tokens = ("老师是谁", "教师是谁", "任课老师", "授课老师", "任课教师", "授课教师", "哪个老师", "哪位老师", "谁教", "谁上的")
    return any(token in normalized for token in teacher_tokens)


def is_course_count_query(text: str) -> bool:
    normalized = normalize_text(text)
    if not normalized:
        return False
    count_tokens = ("有几节课", "几节课", "多少节课", "几次课", "多少次课", "上几次", "上多少次", "课时", "多少课时")
    return any(token in normalized for token in count_tokens)


def is_course_time_query(text: str) -> bool:
    normalized = normalize_text(text)
    if not normalized:
        return False
    time_tokens = (
        "什么时候学", "什么时候上", "什么时间学", "什么时间上", "哪天学", "哪天上",
        "周几学", "周几上", "几点学", "几点上", "上课时间", "学习时间",
    )
    return any(token in normalized for token in time_tokens)


def parse_course_lookup_keyword(text: str) -> Optional[str]:
    normalized = normalize_text(text)
    if not normalized:
        return None
    patterns = [
        r"(?:请问|帮我查一下|查一下|查询|想知道)?(.+?)(?:这门课|课程)?(?:的)?(?:任课老师|授课老师|任课教师|授课教师|老师|教师)是?谁",
        r"(?:请问|帮我查一下|查一下|查询|想知道)?(.+?)(?:这门课|课程)?(?:是)?(?:哪个老师|哪位老师)",
        r"(?:请问|帮我查一下|查一下|查询|想知道)?(.+?)(?:这门课|课程)?(?:是谁教|谁教|谁上的)",
    ]
    for pattern in patterns:
        match = re.search(pattern, normalized)
        if not match:
            continue
        candidate = re.sub(r"^(请问|帮我查一下|查一下|查询|想知道|一下)", "", match.group(1) or "")
        candidate = re.sub(r"(这门课|课程|课)$", "", candidate)
        candidate = candidate.strip()
        if candidate and candidate not in {"老师", "教师", "课程", "这门"}:
            return candidate[:40]
    return None


def parse_schedule_course_keyword(text: str) -> Optional[str]:
    teacher_keyword = parse_course_lookup_keyword(text)
    if teacher_keyword:
        return teacher_keyword

    candidate = normalize_text(text)
    if not candidate:
        return None
    direct_patterns = [
        r"(?:请问|帮我查一下|查一下|查询|想知道)?(.+?)(?:是)?(?:什么时候|什么时间|哪天|周几|几点)(?:上|学)",
        r"(?:请问|帮我查一下|查一下|查询|想知道)?(?:我)?(?:上过|学过)(.+?)(?:吗|么)?$",
        r"(?:请问|帮我查一下|查一下|查询|想知道)?(.+?)(?:我)?(?:上过|学过)(?:吗|么)?$",
    ]
    for pattern in direct_patterns:
        match = re.search(pattern, candidate)
        if not match:
            continue
        extracted = _clean_course_keyword_candidate(match.group(1) or "")
        if extracted:
            return extracted
    cleanup_patterns = [
        r"第\d+周",
        r"第?\d+(?:[-到至]\d+)?节",
        r"周[一二三四五六日天]",
        r"星期[一二三四五六日天]",
        r"\d{1,2}月(?:份)?",
        r"[一二三四五六七八九十]{1,3}月(?:份)?",
        r"这个月",
        r"本月",
    ]
    for pattern in cleanup_patterns:
        candidate = re.sub(pattern, "", candidate)
    noise_tokens = (
        "请问", "帮我", "帮我查一下", "查一下", "查询", "想知道", "一下",
        "这个学期", "这学期", "本学期", "当前学期", "所有学期", "全部学期",
        "今天", "明天", "后天", "本周", "这周", "我有",
        "有多少节", "有几节课", "几节课", "多少节课", "有几节", "几节", "多少节",
        "几次课", "多少次课", "上几次", "上多少次", "课时", "多少课时",
        "都有什么课", "有什么课", "有哪些课", "什么课程", "有课吗",
        "课程安排", "课程列表", "全部课程", "所有课程", "课表",
        "这门课", "课程", "这门", "课",
        "老师是谁", "教师是谁", "任课老师", "授课老师", "任课教师", "授课教师",
        "哪个老师", "哪位老师", "谁教", "谁上的",
        "什么时候上", "什么时候", "什么时间", "几点上", "几点", "在哪上", "在哪里上", "在哪", "哪里",
        "我上过", "我学过", "上过", "学过", "上的呢", "学的呢", "上的", "学的", "上吗", "学吗", "吗", "呢", "是",
        "地点", "教室", "上课安排", "安排",
    )
    for token in noise_tokens:
        candidate = candidate.replace(token, "")
    candidate = _clean_course_keyword_candidate(candidate)
    if not candidate:
        return None
    if candidate in {"老师", "教师", "课程", "课表", "本学期", "当前学期", "所有学期"}:
        return None
    if re.fullmatch(r"[a-z0-9+#._-]{2,}", candidate):
        return candidate[:40]
    if len(candidate) >= 2:
        return candidate[:40]
    return None


def _clean_course_keyword_candidate(candidate: str) -> str:
    text = (candidate or "").strip()
    text = re.sub(r"^(请问|帮我查一下|查一下|查询|想知道|一下|我想知道|我想问)", "", text)
    text = re.sub(r"(这门课|课程|课|吗|么|呢|呀|啊)$", "", text)
    text = text.strip()
    if len(text) % 2 == 0:
        half = len(text) // 2
        if half >= 2 and text[:half] == text[half:]:
            text = text[:half]
    return text[:40]


def is_smalltalk_intent(text: str) -> bool:
    normalized = normalize_text(text)
    if not normalized:
        return True
    greetings = {
        "你好",
        "您好",
        "嗨",
        "hi",
        "hello",
        "在吗",
        "在不在",
        "早上好",
        "中午好",
        "下午好",
        "晚上好",
        "谢谢",
        "再见",
    }
    if normalized in greetings:
        return True
    return normalized.startswith("你好") or normalized.startswith("您好")


def parse_requested_week(text: str) -> Optional[int]:
    match = re.search(r"第\s*(\d+)\s*周", text)
    return int(match.group(1)) if match else None


def parse_requested_weekday(text: str) -> Optional[int]:
    compact = re.sub(r"\s+", "", text)
    today = date.today()
    if "今天" in compact:
        return today.isoweekday()
    if "明天" in compact:
        return (today + timedelta(days=1)).isoweekday()
    if "后天" in compact:
        return (today + timedelta(days=2)).isoweekday()
    mappings = {
        ("周一", "星期一"): 1,
        ("周二", "星期二"): 2,
        ("周三", "星期三"): 3,
        ("周四", "星期四"): 4,
        ("周五", "星期五"): 5,
        ("周六", "星期六"): 6,
        ("周日", "周天", "星期日", "星期天"): 7,
    }
    for keys, value in mappings.items():
        if any(k in compact for k in keys):
            return value
    return None


def parse_requested_session(text: str) -> Optional[tuple[int, int]]:
    compact = re.sub(r"\s+", "", text or "")
    if not compact:
        return None
    match = re.search(r"第?(\d{1,2})(?:[-到至](\d{1,2}))?节", compact)
    if match:
        start = int(match.group(1))
        end = int(match.group(2) or start)
        return (min(start, end), max(start, end))

    numerals = {
        "一": 1,
        "二": 2,
        "两": 2,
        "三": 3,
        "四": 4,
        "五": 5,
        "六": 6,
        "七": 7,
        "八": 8,
        "九": 9,
        "十": 10,
    }
    match = re.search(r"第?([一二两三四五六七八九十])节", compact)
    if match:
        value = numerals.get(match.group(1))
        return (value, value) if value else None
    return None


def parse_requested_date(text: str) -> Optional[date]:
    compact = re.sub(r"\s+", "", text or "")
    if not compact:
        return None

    today = date.today()
    if "今天" in compact:
        return today
    if "明天" in compact:
        return today + timedelta(days=1)
    if "后天" in compact:
        return today + timedelta(days=2)

    match = re.search(r"(20\d{2})[-/.年](\d{1,2})[-/.月](\d{1,2})日?", compact)
    if match:
        try:
            return date(int(match.group(1)), int(match.group(2)), int(match.group(3)))
        except ValueError:
            return None

    match = re.search(r"(\d{1,2})月(\d{1,2})日?", compact)
    if match:
        try:
            return date(today.year, int(match.group(1)), int(match.group(2)))
        except ValueError:
            return None
    return None


def parse_requested_month(text: str) -> Optional[tuple[int, int]]:
    compact = re.sub(r"\s+", "", text or "")
    if not compact:
        return None
    # 单日日期("X月X日/X月X号/五月十日")不算月份,交给 parse_requested_date
    if re.search(r"\d{1,2}月\d{1,2}(?:日|号)?", compact):
        return None
    if re.search(r"[一二三四五六七八九十]{1,3}月[一二三四五六七八九十]{1,3}日?", compact):
        return None

    month = None
    match = re.search(r"(\d{1,2})月(?:份)?", compact)
    if match:
        candidate = int(match.group(1))
        if 1 <= candidate <= 12:
            month = candidate
    if month is None:
        numerals = {
            "一": 1, "二": 2, "三": 3, "四": 4, "五": 5, "六": 6,
            "七": 7, "八": 8, "九": 9, "十": 10, "十一": 11, "十二": 12,
        }
        match = re.search(r"([一二三四五六七八九十]{1,3})月(?:份)?", compact)
        if match:
            month = numerals.get(match.group(1))
    if month is None:
        if "本月" in compact or "这个月" in compact:
            month = date.today().month
    if month is None:
        return None
    return (date.today().year, month)


def week_in_range(week_range: Optional[str], week: int) -> bool:
    if not week_range:
        return True
    matches = re.findall(r"(\d+)\s*-\s*(\d+)|(\d+)", week_range)
    if not matches:
        return True
    for start, end, single in matches:
        if single:
            if int(single) == week:
                return True
            continue
        if int(start) <= week <= int(end):
            return True
    return False


def format_weekday(weekday: Optional[int]) -> str:
    mapping = {1: "周一", 2: "周二", 3: "周三", 4: "周四", 5: "周五", 6: "周六", 7: "周日"}
    return mapping.get(weekday or 0, "")


def parse_session_start(sessions: Optional[str]) -> int:
    if not sessions:
        return 999
    match = re.search(r"(\d+)", sessions)
    return int(match.group(1)) if match else 999
