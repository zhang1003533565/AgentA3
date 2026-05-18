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
        raise ValueError("DEEPSEEK_BASE_URL 未配置")
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
    return re.sub(r"[\s，。！？,.!？、“”\"'：:（）()]", "", text.lower())


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
        "接下来有什么课", "接下来哪门课", "第几节有课",
    ]
    if any(p in normalized for p in patterns):
        return True
    return bool(re.match(r".*第\d+周.*", normalized)) \
        or bool(re.match(r".*周[一二三四五六日天].*有.*课.*", normalized)) \
        or bool(re.match(r".*星期[一二三四五六日天].*有.*课.*", normalized))


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
