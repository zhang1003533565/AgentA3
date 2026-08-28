import contextvars
import copy
import hashlib
import json
import os
import re
import time
from collections import OrderedDict, deque
from datetime import date, timedelta
from threading import RLock
from typing import Any, Dict, List, Optional
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from app.utils.logger import get_logger
from app.utils.text_utils import (
    format_weekday,
    is_all_semester_schedule_query,
    is_semester_schedule_query,
    normalize_text,
    parse_requested_date,
    parse_requested_month,
    parse_requested_session,
    parse_requested_week,
    parse_requested_weekday,
    parse_schedule_course_keyword,
    parse_session_start,
    week_in_range,
)

logger = get_logger("services.java_backend")

_tool_cache_events_var: contextvars.ContextVar[Optional[List[Dict[str, Any]]]] = contextvars.ContextVar(
    "java_tool_cache_events",
    default=None,
)
_tool_cache_context_var: contextvars.ContextVar[Dict[str, Any]] = contextvars.ContextVar(
    "java_tool_cache_context",
    default={},
)


class JavaBackendRetriever:
    def __init__(self) -> None:
        self.enabled = True
        self.disabled_until: Optional[float] = None
        self.circuit_open_seconds = 10.0
        configured_java_url = str(os.getenv("JAVA_BACKEND_BASE_URL", "")).strip().rstrip("/")
        self.java_base_url = configured_java_url or "http://localhost:8080"
        self.timeout_seconds = 8
        self.cache_enabled = self._env_bool("AI_TOOL_CACHE_ENABLED", True)
        self.cache_ttl_seconds = self._env_int("AI_TOOL_CACHE_TTL_SECONDS", 180)
        self.cache_max_entries = self._env_int("AI_TOOL_CACHE_MAX_ENTRIES", 1000)
        self.cache_event_limit = self._env_int("AI_TOOL_CACHE_EVENT_LIMIT", 100)
        self._cache_lock = RLock()
        self._tool_cache: OrderedDict[str, Dict[str, Any]] = OrderedDict()
        self._cache_recent_events = deque(maxlen=max(1, self.cache_event_limit))
        self._cache_event_seq = 0
        self._cache_request_count = 0
        self._cache_hit_count = 0
        self._cache_miss_count = 0
        self._cache_estimated_saved_ms = 0
        self._cache_last_hit_at = ""
        self._cache_last_miss_at = ""
        self._cache_path_stats: Dict[str, Dict[str, Any]] = {}

    def _get_json(self, path: str, authorization: str, params: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        if not self._can_call():
            return {}

        query = ""
        normalized: Dict[str, Any] = {}
        if params:
            normalized = {k: v for k, v in params.items() if v is not None and v != ""}
            if normalized:
                query = "?" + urlencode(normalized)

        user_key = self._authorization_hash(authorization)
        cache_key = self._cache_key(path, user_key, normalized)
        cached_payload = self._read_cache(path, cache_key)
        if cached_payload is not None:
            return cached_payload

        url = f"{self.java_base_url}{path}{query}"
        req = Request(url, method="GET")
        req.add_header("Authorization", authorization)
        req.add_header("Accept", "application/json")

        started = time.perf_counter()
        try:
            with urlopen(req, timeout=self.timeout_seconds) as resp:
                body = resp.read().decode("utf-8")
                elapsed_ms = int((time.perf_counter() - started) * 1000)
                logger.info("java api ok path=%s elapsed_ms=%s", path, elapsed_ms)
                payload = json.loads(body)
                self._record_success()
                self._record_cache_miss(path, elapsed_ms, cache_key, normalized, user_key, payload)
                self._write_cache(path, cache_key, payload, elapsed_ms, normalized, user_key)
                return payload
        except Exception:
            elapsed_ms = int((time.perf_counter() - started) * 1000)
            self._record_cache_miss(path, elapsed_ms, cache_key, normalized, user_key, None, status="error")
            self._record_failure()
            logger.exception(
                "java api failed path=%s elapsed_ms=%s circuit_open_seconds=%s",
                path,
                elapsed_ms,
                self.circuit_open_seconds,
            )
            return {}

    def _can_call(self) -> bool:
        if not self.enabled:
            return False
        disabled_until = self.disabled_until
        return disabled_until is None or time.monotonic() >= disabled_until

    def _record_failure(self) -> None:
        with self._cache_lock:
            self.disabled_until = time.monotonic() + self.circuit_open_seconds

    def _record_success(self) -> None:
        with self._cache_lock:
            self.disabled_until = None

    def _env_bool(self, key: str, default: bool) -> bool:
        value = str(os.getenv(key, "")).strip().lower()
        if value in {"1", "true", "yes", "on"}:
            return True
        if value in {"0", "false", "no", "off"}:
            return False
        return default

    def _env_int(self, key: str, default: int) -> int:
        try:
            return max(0, int(str(os.getenv(key, default)).strip()))
        except (TypeError, ValueError):
            return default

    def _cache_available(self) -> bool:
        return self.cache_enabled and self.cache_ttl_seconds > 0 and self.cache_max_entries > 0

    def _authorization_hash(self, authorization: str) -> str:
        return hashlib.sha256(str(authorization or "").encode("utf-8")).hexdigest()

    def _cache_key(self, path: str, user_key: str, params: Dict[str, Any]) -> str:
        payload = json.dumps(
            {"user": user_key, "path": path, "params": params},
            ensure_ascii=False,
            sort_keys=True,
            default=str,
        )
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()

    def _read_cache(self, path: str, cache_key: str) -> Optional[Dict[str, Any]]:
        if not self._cache_available():
            return None
        started = time.perf_counter()
        now = time.time()
        with self._cache_lock:
            entry = self._tool_cache.get(cache_key)
            if not entry:
                return None
            if float(entry.get("expires_at") or 0) <= now:
                self._tool_cache.pop(cache_key, None)
                return None
            self._tool_cache.move_to_end(cache_key)
            payload = copy.deepcopy(entry.get("payload") or {})
            saved_ms = int(entry.get("origin_elapsed_ms") or 0)
            entry["hit_count"] = int(entry.get("hit_count") or 0) + 1
            entry["last_hit_at"] = self._now_iso()
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        event = self._event_from_entry(entry, cache_hit=True, elapsed_ms=elapsed_ms, saved_ms=saved_ms, status="hit")
        event["elapsedMs"] = elapsed_ms
        self._record_cache_hit(event)
        logger.info("java api cache hit path=%s saved_ms=%s", path, saved_ms)
        return payload

    def _write_cache(
        self,
        path: str,
        cache_key: str,
        payload: Dict[str, Any],
        origin_elapsed_ms: int,
        params: Dict[str, Any],
        user_key: str,
    ) -> None:
        if not self._cache_available() or not self._is_cacheable_payload(payload):
            return
        now = time.time()
        context = self._current_cache_context()
        with self._cache_lock:
            self._purge_expired_locked(now)
            self._tool_cache[cache_key] = {
                "cache_key": cache_key,
                "cacheKey": cache_key[:12],
                "path": path,
                "params": copy.deepcopy(params),
                "userKey": user_key[:12],
                "toolName": context.get("toolName") or "",
                "inputPreview": context.get("inputPreview") or "",
                "inputHash": context.get("inputHash") or "",
                "payload": copy.deepcopy(payload),
                "dataCount": self._payload_data_count(payload),
                "origin_elapsed_ms": max(0, int(origin_elapsed_ms or 0)),
                "created_at": now,
                "expires_at": now + self.cache_ttl_seconds,
                "createdAt": self._timestamp_iso(now),
                "expiresAt": self._timestamp_iso(now + self.cache_ttl_seconds),
                "hit_count": 0,
                "last_hit_at": "",
            }
            self._tool_cache.move_to_end(cache_key)
            while len(self._tool_cache) > self.cache_max_entries:
                self._tool_cache.popitem(last=False)

    def _is_cacheable_payload(self, payload: Dict[str, Any]) -> bool:
        return isinstance(payload, dict) and payload.get("code") == 200

    def _purge_expired_locked(self, now: Optional[float] = None) -> None:
        current = now if now is not None else time.time()
        expired_keys = [
            key for key, entry in self._tool_cache.items()
            if float(entry.get("expires_at") or 0) <= current
        ]
        for key in expired_keys:
            self._tool_cache.pop(key, None)

    def _record_cache_hit(self, event: Dict[str, Any]) -> None:
        path = str(event.get("path") or "")
        saved_ms = int(event.get("savedMillis") or 0)
        now_text = str(event.get("time") or self._now_iso())
        with self._cache_lock:
            self._cache_request_count += 1
            self._cache_hit_count += 1
            self._cache_estimated_saved_ms += max(0, saved_ms)
            self._cache_last_hit_at = now_text
            stats = self._path_stats(path)
            stats["requestCount"] += 1
            stats["hitCount"] += 1
            stats["estimatedSavedMillis"] += max(0, saved_ms)
            stats["lastHitAt"] = now_text
        self._record_cache_event(event)

    def _record_cache_miss(
        self,
        path: str,
        elapsed_ms: int,
        cache_key: str,
        params: Dict[str, Any],
        user_key: str,
        payload: Optional[Dict[str, Any]],
        status: str = "miss",
    ) -> None:
        event = self._build_cache_event(
            path=path,
            cache_key=cache_key,
            params=params,
            user_key=user_key,
            cache_hit=False,
            elapsed_ms=elapsed_ms,
            saved_ms=0,
            data_count=self._payload_data_count(payload) if payload else 0,
            status=status,
        )
        now_text = str(event.get("time") or self._now_iso())
        with self._cache_lock:
            self._cache_request_count += 1
            self._cache_miss_count += 1
            self._cache_last_miss_at = now_text
            stats = self._path_stats(path)
            stats["requestCount"] += 1
            stats["missCount"] += 1
            stats["lastMissAt"] = now_text
        self._record_cache_event(event)

    def _path_stats(self, path: str) -> Dict[str, Any]:
        return self._cache_path_stats.setdefault(path, {
            "path": path,
            "requestCount": 0,
            "hitCount": 0,
            "missCount": 0,
            "estimatedSavedMillis": 0,
            "lastHitAt": "",
            "lastMissAt": "",
        })

    def _record_cache_event(self, event: Dict[str, Any]) -> None:
        with self._cache_lock:
            self._cache_event_seq += 1
            full_event = {
                "id": self._cache_event_seq,
                **event,
            }
            self._cache_recent_events.appendleft(full_event)
        self._append_cache_event(full_event)

    def _append_cache_event(self, event: Dict[str, Any]) -> None:
        events = _tool_cache_events_var.get()
        if events is not None:
            events.append(event)

    def _current_cache_context(self) -> Dict[str, Any]:
        context = _tool_cache_context_var.get() or {}
        return context if isinstance(context, dict) else {}

    def _build_cache_event(
        self,
        *,
        path: str,
        cache_key: str,
        params: Dict[str, Any],
        user_key: str,
        cache_hit: bool,
        elapsed_ms: int,
        saved_ms: int,
        data_count: int,
        status: str,
    ) -> Dict[str, Any]:
        context = self._current_cache_context()
        return {
            "time": self._now_iso(),
            "status": status,
            "cacheHit": cache_hit,
            "cacheKey": cache_key[:12],
            "path": path,
            "params": copy.deepcopy(params),
            "query": urlencode(params) if params else "",
            "toolName": context.get("toolName") or "",
            "inputPreview": context.get("inputPreview") or "",
            "inputHash": context.get("inputHash") or "",
            "userKey": user_key[:12],
            "elapsedMs": max(0, int(elapsed_ms or 0)),
            "savedMillis": max(0, int(saved_ms or 0)),
            "dataCount": max(0, int(data_count or 0)),
        }

    def _event_from_entry(self, entry: Dict[str, Any], cache_hit: bool, elapsed_ms: int, saved_ms: int, status: str) -> Dict[str, Any]:
        return {
            "time": self._now_iso(),
            "status": status,
            "cacheHit": cache_hit,
            "cacheKey": entry.get("cacheKey") or str(entry.get("cache_key") or "")[:12],
            "path": entry.get("path") or "",
            "params": copy.deepcopy(entry.get("params") or {}),
            "query": urlencode(entry.get("params") or {}) if entry.get("params") else "",
            "toolName": entry.get("toolName") or "",
            "inputPreview": entry.get("inputPreview") or "",
            "inputHash": entry.get("inputHash") or "",
            "userKey": entry.get("userKey") or "",
            "elapsedMs": max(0, int(elapsed_ms or 0)),
            "savedMillis": max(0, int(saved_ms or 0)),
            "dataCount": max(0, int(entry.get("dataCount") or 0)),
        }

    def _payload_data_count(self, payload: Optional[Dict[str, Any]]) -> int:
        if not isinstance(payload, dict):
            return 0
        data = payload.get("data")
        if isinstance(data, list):
            return len(data)
        if isinstance(data, dict):
            for key in ("records", "list", "items", "content", "rows"):
                value = data.get(key)
                if isinstance(value, list):
                    return len(value)
            return 1 if data else 0
        return 1 if data is not None else 0

    def _now_iso(self) -> str:
        return self._timestamp_iso(time.time())

    def _timestamp_iso(self, value: float) -> str:
        return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(value))

    def _cache_entries_snapshot(self) -> List[Dict[str, Any]]:
        entries = []
        for entry in self._tool_cache.values():
            entries.append({
                "cacheKey": entry.get("cacheKey") or "",
                "path": entry.get("path") or "",
                "params": copy.deepcopy(entry.get("params") or {}),
                "query": urlencode(entry.get("params") or {}) if entry.get("params") else "",
                "toolName": entry.get("toolName") or "",
                "inputPreview": entry.get("inputPreview") or "",
                "inputHash": entry.get("inputHash") or "",
                "userKey": entry.get("userKey") or "",
                "dataCount": int(entry.get("dataCount") or 0),
                "originElapsedMs": int(entry.get("origin_elapsed_ms") or 0),
                "hitCount": int(entry.get("hit_count") or 0),
                "createdAt": entry.get("createdAt") or "",
                "expiresAt": entry.get("expiresAt") or "",
                "lastHitAt": entry.get("last_hit_at") or "",
            })
        entries.sort(key=lambda item: (item["hitCount"], item["originElapsedMs"], item["createdAt"]), reverse=True)
        return entries[:100]

    def tool_cache_stats(self) -> Dict[str, Any]:
        with self._cache_lock:
            self._purge_expired_locked()
            by_path = []
            entry_count_by_path: Dict[str, int] = {}
            for entry in self._tool_cache.values():
                entry_path = str(entry.get("path") or "")
                entry_count_by_path[entry_path] = entry_count_by_path.get(entry_path, 0) + 1
            for item in self._cache_path_stats.values():
                request_count = int(item.get("requestCount") or 0)
                hit_count = int(item.get("hitCount") or 0)
                by_path.append({
                    "path": item.get("path") or "",
                    "requestCount": request_count,
                    "hitCount": hit_count,
                    "missCount": int(item.get("missCount") or 0),
                    "hitRate": (hit_count / request_count) if request_count else 0,
                    "entryCount": entry_count_by_path.get(str(item.get("path") or ""), 0),
                    "estimatedSavedMillis": int(item.get("estimatedSavedMillis") or 0),
                    "lastHitAt": item.get("lastHitAt") or "",
                    "lastMissAt": item.get("lastMissAt") or "",
                })
            by_path.sort(key=lambda row: (row["requestCount"], row["hitCount"]), reverse=True)
            request_count = int(self._cache_request_count)
            hit_count = int(self._cache_hit_count)
            recent_events = list(self._cache_recent_events)
            entries = self._cache_entries_snapshot()
            return {
                "enabled": self._cache_available(),
                "ttlSeconds": self.cache_ttl_seconds,
                "maxEntries": self.cache_max_entries,
                "eventLimit": self.cache_event_limit,
                "entryCount": len(self._tool_cache),
                "requestCount": request_count,
                "hitCount": hit_count,
                "missCount": int(self._cache_miss_count),
                "hitRate": (hit_count / request_count) if request_count else 0,
                "estimatedSavedMillis": int(self._cache_estimated_saved_ms),
                "lastHitAt": self._cache_last_hit_at,
                "lastMissAt": self._cache_last_miss_at,
                "byPath": by_path,
                "recentEvents": recent_events,
                "cacheEntries": entries,
            }

    def clear_tool_cache(self) -> Dict[str, Any]:
        with self._cache_lock:
            cleared = len(self._tool_cache)
            self._tool_cache.clear()
            self._cache_request_count = 0
            self._cache_hit_count = 0
            self._cache_miss_count = 0
            self._cache_estimated_saved_ms = 0
            self._cache_last_hit_at = ""
            self._cache_last_miss_at = ""
            self._cache_path_stats.clear()
            self._cache_recent_events.clear()
            self._cache_event_seq = 0
        return {
            "cleared": cleared,
            "entryCount": 0,
            "stats": self.tool_cache_stats(),
        }

    def _extract_result_data(self, payload: Dict[str, Any]) -> Any:
        if not isinstance(payload, dict):
            return None
        if payload.get("code") != 200:
            return None
        return payload.get("data")

    def search_schedule(self, authorization: str, input_text: str) -> List[Dict[str, Any]]:
        if not self.enabled:
            return []

        requested_week = parse_requested_week(input_text)
        requested_date = parse_requested_date(input_text)
        requested_month = parse_requested_month(input_text)
        requested_weekday = parse_requested_weekday(input_text)
        requested_session = parse_requested_session(input_text)
        course_keyword = parse_schedule_course_keyword(input_text)
        all_semester_scope = is_all_semester_schedule_query(input_text)
        semester_scope = all_semester_scope or is_semester_schedule_query(input_text)
        month_scope = requested_month is not None
        # Explicit dates are schedule lookups even when the natural-language
        # course-keyword parser sees the trailing “有什么课”. Do not widen such
        # requests to all semesters or collapse them into course summaries.
        course_lookup_scope = bool(course_keyword) and requested_date is None

        if requested_date is not None:
            requested_weekday = requested_date.isoweekday()
            requested_week = self._week_for_date(authorization, requested_date)
            if requested_week is None and not semester_scope:
                return []

        if all_semester_scope:
            payload = self._get_json("/api/schedule", authorization, params={"allSemesters": "true"})
        elif requested_week is not None:
            payload = self._get_json(f"/api/schedule/week/{requested_week}", authorization)
        elif semester_scope or course_lookup_scope or month_scope:
            payload = self._get_json("/api/schedule", authorization)
        else:
            payload = self._get_json("/api/schedule/current-week", authorization)

        schedules = self._extract_result_data(payload)
        if not isinstance(schedules, list):
            return []

        query_scope = "all_semesters" if all_semester_scope else "current_semester"
        fallback_reason = ""
        if course_lookup_scope and not all_semester_scope and not self._has_course_keyword_match(schedules, course_keyword):
            fallback_payload = self._get_json("/api/schedule", authorization, params={"allSemesters": "true"})
            fallback_schedules = self._extract_result_data(fallback_payload)
            if isinstance(fallback_schedules, list):
                schedules = fallback_schedules
                query_scope = "all_semesters_fallback"
                fallback_reason = "current_semester_no_course_match"

        if requested_weekday is not None:
            schedules = [item for item in schedules if item.get("weekday") == requested_weekday]
        if semester_scope and requested_week is not None:
            schedules = [item for item in schedules if week_in_range(item.get("weekRange"), requested_week)]
        if requested_session is not None:
            schedules = [item for item in schedules if self._session_matches(item.get("classSessions"), requested_session)]
        if month_scope:
            month_week_range = self._month_week_range(authorization, requested_month)
            if month_week_range is None:
                return []
            month_start, month_end = month_week_range
            schedules = [
                item for item in schedules
                if any(week_in_range(item.get("weekRange"), w) for w in range(month_start, month_end + 1))
            ]

        schedules.sort(key=lambda item: ((item.get("weekday") or 99), parse_session_start(item.get("classSessions"))))
        if semester_scope or course_lookup_scope or month_scope:
            results = self._semester_course_results(
                schedules,
                include_semester=all_semester_scope or query_scope == "all_semesters_fallback",
            )
            if query_scope != "current_semester":
                for item in results:
                    item["queryScope"] = query_scope
                    if fallback_reason:
                        item["fallbackReason"] = fallback_reason
                        item["currentSemesterMatched"] = False
            return results

        results: List[Dict[str, Any]] = []
        for row in schedules[:12]:
            result = {
                "type": "course_schedule",
                "id": row.get("id"),
                "name": row.get("courseName"),
                "teacherName": row.get("teacherName"),
                "location": row.get("location"),
                "weekday": row.get("weekday"),
                "weekdayText": format_weekday(row.get("weekday")),
                "classSessions": row.get("classSessions"),
                "weekRange": row.get("weekRange"),
                "assessmentType": row.get("assessmentType"),
                "campus": row.get("campus"),
                "classCode": row.get("classCode"),
                "credit": row.get("credit"),
            }
            if requested_week is not None:
                result["requestedWeek"] = requested_week
            if requested_weekday is not None:
                result["requestedWeekday"] = requested_weekday
                result["requestedWeekdayText"] = format_weekday(requested_weekday)
            if requested_session is not None:
                result["requestedSessionStart"] = requested_session[0]
                result["requestedSessionEnd"] = requested_session[1]
            results.append(result)
        return results

    def _week_for_date(self, authorization: str, target_date: date) -> Optional[int]:
        payload = self._get_json("/api/schedule/settings", authorization)
        settings = self._extract_result_data(payload)
        if not isinstance(settings, dict):
            return None
        semester_start_text = str(settings.get("semesterStart") or "").strip()
        if not semester_start_text:
            return None
        try:
            semester_start = date.fromisoformat(semester_start_text)
        except ValueError:
            return None
        if target_date < semester_start:
            return None
        return (target_date - semester_start).days // 7 + 1

    def _month_week_range(self, authorization: str, requested_month: tuple[int, int]) -> Optional[tuple[int, int]]:
        """把"X月"换算成该月覆盖的周次范围 (start_week, end_week);整月都在开学前返回 None。"""
        payload = self._get_json("/api/schedule/settings", authorization)
        settings = self._extract_result_data(payload)
        if not isinstance(settings, dict):
            return None
        semester_start_text = str(settings.get("semesterStart") or "").strip()
        if not semester_start_text:
            return None
        try:
            semester_start = date.fromisoformat(semester_start_text)
        except ValueError:
            return None
        year, month = requested_month
        try:
            first_day = date(year, month, 1)
        except ValueError:
            return None
        if month == 12:
            last_day = date(year, 12, 31)
        else:
            last_day = date(year, month + 1, 1) - timedelta(days=1)
        start_week = (first_day - semester_start).days // 7 + 1
        end_week = (last_day - semester_start).days // 7 + 1
        if end_week < 1:
            return None
        return (max(1, start_week), end_week)

    def _session_matches(self, class_sessions: Any, requested_session: tuple[int, int]) -> bool:
        text = str(class_sessions or "")
        match = re.search(r"(\d{1,2})(?:\s*-\s*(\d{1,2}))?", text)
        if not match:
            return False
        start = int(match.group(1))
        end = int(match.group(2) or start)
        request_start, request_end = requested_session
        return max(start, request_start) <= min(end, request_end)

    def _has_course_keyword_match(self, schedules: List[Dict[str, Any]], course_keyword: Optional[str]) -> bool:
        keyword = normalize_text(course_keyword)
        if not keyword:
            return bool(schedules)
        for row in schedules:
            course_name = normalize_text(str(row.get("courseName") or row.get("name") or ""))
            if course_name and (keyword in course_name or course_name in keyword):
                return True
        return False

    def _semester_course_results(self, schedules: List[Dict[str, Any]], include_semester: bool = False) -> List[Dict[str, Any]]:
        grouped: Dict[str, Dict[str, Any]] = {}
        for row in schedules:
            course_name = str(row.get("courseName") or row.get("name") or "").strip()
            if not course_name:
                continue
            academic_year = str(row.get("academicYear") or "").strip()
            semester_term = row.get("semesterTerm")
            class_code = str(row.get("classCode") or "").strip()
            key = f"{academic_year}::{semester_term}::{course_name}::{class_code}" if include_semester else f"{course_name}::{class_code}"
            item = grouped.setdefault(key, {
                "type": "course_schedule_summary",
                "id": row.get("id") or key,
                "name": course_name,
                "academicYear": academic_year,
                "semesterTerm": semester_term,
                "semesterLabel": self._semester_label(academic_year, semester_term),
                "teacherName": row.get("teacherName"),
                "assessmentType": row.get("assessmentType"),
                "classCode": row.get("classCode"),
                "credit": row.get("credit"),
                "scheduleItems": [],
                "locations": [],
                "weekRanges": [],
            })
            location = str(row.get("location") or "").strip()
            week_range = str(row.get("weekRange") or "").strip()
            schedule_text = " ".join(
                part for part in (
                    format_weekday(row.get("weekday")),
                    str(row.get("classSessions") or "").strip(),
                    week_range,
                    location,
                )
                if part
            )
            if schedule_text and schedule_text not in item["scheduleItems"]:
                item["scheduleItems"].append(schedule_text)
            if location and location not in item["locations"]:
                item["locations"].append(location)
            if week_range and week_range not in item["weekRanges"]:
                item["weekRanges"].append(week_range)

        results = list(grouped.values())
        for item in results:
            item["scheduleCount"] = len(item.get("scheduleItems") or [])
            item["location"] = "、".join(item.get("locations") or [])
            item["weekRange"] = "、".join(item.get("weekRanges") or [])
        results.sort(key=lambda item: (
            str(item.get("academicYear") or ""),
            item.get("semesterTerm") or 0,
            str(item.get("name") or ""),
        ))
        return results[:40]

    def _semester_label(self, academic_year: str, semester_term: Any) -> str:
        if not academic_year and not semester_term:
            return ""
        if academic_year and semester_term:
            return f"{academic_year} 第 {semester_term} 学期"
        return academic_year or f"第 {semester_term} 学期"

    def search_service_tool(self, authorization: str, tool_name: str, input_text: str) -> List[Dict[str, Any]]:
        name = str(tool_name or "").strip()
        if name == "java_schedule_api":
            return self.search_schedule(authorization, input_text)
        if not self.enabled:
            return []
        handlers = {
            "java_activity_api": self._search_activities,
            "java_meeting_api": self._search_meetings,
            "java_canteen_api": self._search_canteen,
            "java_facility_api": self._search_facilities,
            "java_secondhand_api": self._search_secondhand,
        }
        handler = handlers.get(name)
        if handler is None:
            return []
        return handler(authorization, input_text)

    def search_service_tool_with_meta(self, authorization: str, tool_name: str, input_text: str) -> tuple[List[Dict[str, Any]], Dict[str, Any]]:
        events: List[Dict[str, Any]] = []
        input_preview = re.sub(r"\s+", " ", str(input_text or "")).strip()[:120]
        context = {
            "toolName": str(tool_name or "").strip(),
            "inputPreview": input_preview,
            "inputHash": hashlib.sha256(str(input_text or "").encode("utf-8")).hexdigest()[:12] if input_text else "",
        }
        event_token = _tool_cache_events_var.set(events)
        context_token = _tool_cache_context_var.set(context)
        try:
            results = self.search_service_tool(authorization, tool_name, input_text)
        finally:
            _tool_cache_context_var.reset(context_token)
            _tool_cache_events_var.reset(event_token)
        request_count = len(events)
        hit_count = sum(1 for event in events if event.get("cacheHit"))
        miss_count = request_count - hit_count
        tool_cache = {
            "enabled": self._cache_available(),
            "requestCount": request_count,
            "hitCount": hit_count,
            "missCount": miss_count,
            "hitRate": (hit_count / request_count) if request_count else 0,
            "cacheHit": request_count > 0 and hit_count == request_count,
            "partialHit": 0 < hit_count < request_count,
            "estimatedSavedMillis": sum(int(event.get("savedMillis") or 0) for event in events),
            "events": events[:20],
        }
        return results, {"toolCache": tool_cache}

    def search_keyword(self, authorization: str, keyword: str) -> List[Dict[str, Any]]:
        if not self.enabled or not keyword:
            return []

        keyword_lower = keyword.lower()
        results: List[Dict[str, Any]] = []
        matched_restaurant_ids: set[int] = set()
        matched_stall_ids: set[int] = set()
        coupon_ids: set[int] = set()

        facilities_payload = self._get_json(
            "/api/v1/facility/list",
            authorization,
            params={"type": 1, "name": keyword, "pageNum": 1, "pageSize": 20},
        )
        facilities_page = self._extract_result_data(facilities_payload)
        facilities = facilities_page.get("records", []) if isinstance(facilities_page, dict) else []
        for row in facilities[:5]:
            results.append({
                "type": "restaurant",
                "id": row.get("id"),
                "name": row.get("facilityName"),
                "location": row.get("location"),
                "description": row.get("description"),
            })
            if row.get("id") is not None:
                matched_restaurant_ids.add(int(row["id"]))

        stalls_payload = self._get_json("/api/v1/canteen-stall/list", authorization)
        stalls = self._extract_result_data(stalls_payload)
        if not isinstance(stalls, list):
            stalls = []
        matched_stalls = [
            row for row in stalls
            if self._contains_keyword(keyword_lower, row.get("stallName"), row.get("category"), row.get("location"), row.get("description"))
        ]
        matched_stalls.sort(key=lambda item: item.get("score") if item.get("score") is not None else -1, reverse=True)
        for row in matched_stalls[:8]:
            results.append({
                "type": "stall",
                "id": row.get("id"),
                "name": row.get("stallName"),
                "category": row.get("category"),
                "restaurantId": row.get("restaurantId"),
                "score": row.get("score"),
                "avgPrice": row.get("avgPrice"),
            })
            if row.get("id") is not None:
                matched_stall_ids.add(int(row["id"]))
            if row.get("restaurantId") is not None:
                matched_restaurant_ids.add(int(row["restaurantId"]))

        dish_candidates: List[Dict[str, Any]] = []
        for params in (
            {"name": keyword},
            {"category": keyword},
            {"taste": keyword},
        ):
            dishes_payload = self._get_json("/api/v1/dish/list", authorization, params=params)
            dishes = self._extract_result_data(dishes_payload)
            if isinstance(dishes, list):
                dish_candidates.extend(dishes)

        dedup_dishes: Dict[Any, Dict[str, Any]] = {}
        for dish in dish_candidates:
            dish_id = dish.get("id")
            dedup_dishes[dish_id if dish_id is not None else id(dish)] = dish

        filtered_dishes = [
            row for row in dedup_dishes.values()
            if self._contains_keyword(keyword_lower, row.get("name"), row.get("category"), row.get("taste"), row.get("description"))
        ]
        filtered_dishes.sort(key=lambda item: item.get("rating") if item.get("rating") is not None else -1, reverse=True)
        for row in filtered_dishes[:8]:
            results.append({
                "type": "dish",
                "id": row.get("id"),
                "name": row.get("name"),
                "stallId": row.get("stallId"),
                "category": row.get("category"),
                "taste": row.get("taste"),
                "rating": row.get("rating"),
                "price": row.get("price"),
            })
            if row.get("stallId") is not None:
                matched_stall_ids.add(int(row["stallId"]))

        coupons_payload = self._get_json("/api/v1/promotion-coupon/list", authorization)
        coupons = self._extract_result_data(coupons_payload)
        if not isinstance(coupons, list):
            coupons = []

        matched_coupons = [
            row for row in coupons
            if self._contains_keyword(
                keyword_lower,
                row.get("couponName"),
                row.get("description"),
                row.get("pickupLocation"),
                row.get("tagType"),
                row.get("merchantName"),
                row.get("stallName"),
                row.get("facilityName"),
            )
        ]
        for row in matched_coupons[:8]:
            if row.get("id") is not None:
                coupon_ids.add(int(row["id"]))
            results.append(self._coupon_result(row))

        for row in coupons:
            row_id = row.get("id")
            if row_id is None:
                continue
            if int(row_id) in coupon_ids:
                continue
            if row.get("stallId") in matched_stall_ids or row.get("facilityId") in matched_restaurant_ids:
                coupon_ids.add(int(row_id))
                results.append(self._coupon_result(row))

        return results[:10]

    def _search_activities(self, authorization: str, input_text: str) -> List[Dict[str, Any]]:
        keyword = self._keyword_from_input(input_text, {"活动", "讲座", "比赛", "报名", "校园活动"})
        if keyword:
            payload = self._get_json("/api/activities/search", authorization, params={"page": 1, "size": 10, "keyword": keyword})
        else:
            payload = self._get_json("/api/activities", authorization, params={"page": 1, "size": 10, "status": "PUBLISHED"})
        records = self._page_records(self._extract_result_data(payload))
        return [self._activity_result(row) for row in records[:10]]

    def _search_meetings(self, authorization: str, input_text: str) -> List[Dict[str, Any]]:
        keyword = self._keyword_from_input(input_text, {"会议", "会议室", "预约", "开会", "日程"})
        payload = self._get_json("/api/meetings", authorization, params={"pageNum": 1, "pageSize": 10, "keyword": keyword})
        records = self._page_records(self._extract_result_data(payload))
        return [self._meeting_result(row) for row in records[:10]]

    def _search_canteen(self, authorization: str, input_text: str) -> List[Dict[str, Any]]:
        keyword = self._keyword_from_input(input_text, {"食堂", "餐厅", "档口", "菜品", "吃饭", "推荐", "窗口"})
        results = self.search_keyword(authorization, keyword) if keyword else []
        if results:
            return results[:10]

        facility_payload = self._get_json(
            "/api/v1/facility/list",
            authorization,
            params={"type": 1, "status": 1, "pageNum": 1, "pageSize": 5},
        )
        facilities = self._page_records(self._extract_result_data(facility_payload))
        for row in facilities[:5]:
            results.append({
                "type": "restaurant",
                "id": row.get("id"),
                "name": row.get("facilityName"),
                "location": row.get("location"),
                "description": row.get("description"),
                "status": row.get("status"),
            })

        stalls_payload = self._get_json("/api/v1/canteen-stall/list", authorization)
        stalls = self._extract_result_data(stalls_payload)
        if isinstance(stalls, list):
            for row in stalls[:5]:
                results.append({
                    "type": "stall",
                    "id": row.get("id"),
                    "name": row.get("stallName"),
                    "category": row.get("category"),
                    "restaurantId": row.get("restaurantId"),
                    "location": row.get("location"),
                    "score": row.get("score"),
                    "avgPrice": row.get("avgPrice"),
                })
        return results[:10]

    def _search_facilities(self, authorization: str, input_text: str) -> List[Dict[str, Any]]:
        keyword = self._keyword_from_input(input_text, {"设施", "位置", "在哪", "哪里", "地图", "导航", "定位", "怎么走", "路线"})
        results: List[Dict[str, Any]] = []
        if keyword:
            search_payload = self._get_json("/api/v1/map/search", authorization, params={"keyword": keyword, "limit": 10})
            markers = self._extract_result_data(search_payload)
            if isinstance(markers, list):
                results.extend(self._marker_result(row) for row in markers[:10])
            locate_payload = self._get_json("/api/v1/map/locate", authorization, params={"keyword": keyword})
            located = self._extract_result_data(locate_payload)
            if isinstance(located, dict) and located:
                located_result = self._locate_result(located)
                if located_result:
                    results.insert(0, located_result)
        else:
            facility_payload = self._get_json("/api/v1/facility/list", authorization, params={"pageNum": 1, "pageSize": 10})
            facilities = self._page_records(self._extract_result_data(facility_payload))
            results.extend(self._facility_result(row) for row in facilities[:10])
        return self._dedupe_results(results)[:10]

    def _search_secondhand(self, authorization: str, input_text: str) -> List[Dict[str, Any]]:
        keyword = self._keyword_from_input(input_text, {"旧物", "二手", "闲置", "物品", "卖", "买", "转让"})
        payload = self._get_json(
            "/api/secondhand/item/list",
            authorization,
            params={"current": 1, "size": 10, "keyword": keyword, "sort": "latest"},
        )
        records = self._page_records(self._extract_result_data(payload))
        return [self._secondhand_result(row) for row in records[:10]]

    def _page_records(self, data: Any) -> List[Dict[str, Any]]:
        if isinstance(data, list):
            return [item for item in data if isinstance(item, dict)]
        if not isinstance(data, dict):
            return []
        for key in ("records", "list", "items", "content"):
            value = data.get(key)
            if isinstance(value, list):
                return [item for item in value if isinstance(item, dict)]
        return []

    def _keyword_from_input(self, input_text: str, domain_tokens: set[str]) -> str:
        text = re.sub(r"[，。！？、,.!?;；:：\n\r\t]", " ", str(input_text or "")).strip()
        if not text:
            return ""
        remove_tokens = {
            "帮我", "请", "一下", "查一下", "查询", "查找", "查", "找", "看看", "看下",
            "有哪些", "有什么", "有没有", "多少", "列表", "推荐", "最近", "今天", "明天",
            "本周", "这周", "我的", "我", "想", "要", "可以", "吗", "呢", "的", "安排",
            "信息", "详情", "状态", "当前", "在",
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

    def _activity_result(self, row: Dict[str, Any]) -> Dict[str, Any]:
        return {
            "type": "activity",
            "id": row.get("id"),
            "name": row.get("title") or row.get("activityTitle"),
            "category": row.get("categoryName") or row.get("category"),
            "location": row.get("location"),
            "organizerName": row.get("organizerName"),
            "status": row.get("status"),
            "startTime": row.get("startTime"),
            "endTime": row.get("endTime"),
            "description": row.get("description") or row.get("content"),
        }

    def _meeting_result(self, row: Dict[str, Any]) -> Dict[str, Any]:
        return {
            "type": "meeting",
            "id": row.get("id") or row.get("sessionId"),
            "sessionId": row.get("sessionId"),
            "name": row.get("title"),
            "meetingType": row.get("meetingType"),
            "status": row.get("status"),
            "roomCode": row.get("roomCode"),
            "scheduledStartTime": row.get("scheduledStartTime"),
            "participantCount": row.get("participantCount"),
            "updatedAt": row.get("updatedAt") or row.get("updateTime"),
        }

    def _facility_result(self, row: Dict[str, Any]) -> Dict[str, Any]:
        return {
            "type": "facility",
            "id": row.get("id"),
            "name": row.get("facilityName") or row.get("name"),
            "facilityType": row.get("facilityType"),
            "facilityTypeName": row.get("facilityTypeName"),
            "location": row.get("location"),
            "description": row.get("description"),
            "longitude": row.get("longitude"),
            "latitude": row.get("latitude"),
            "status": row.get("status"),
        }

    def _marker_result(self, row: Dict[str, Any]) -> Dict[str, Any]:
        return {
            "type": "facility",
            "id": row.get("facilityId") or row.get("id"),
            "markerId": row.get("markerId") or row.get("id"),
            "name": row.get("facilityName") or row.get("name") or row.get("title"),
            "facilityType": row.get("facilityType"),
            "facilityTypeName": row.get("facilityTypeName"),
            "location": row.get("location") or row.get("address"),
            "description": row.get("description"),
            "longitude": row.get("longitude"),
            "latitude": row.get("latitude"),
            "distance": row.get("distance"),
        }

    def _locate_result(self, row: Dict[str, Any]) -> Dict[str, Any]:
        name = row.get("facilityName") or row.get("name")
        if not name:
            return {}
        return {
            "type": "facility_location",
            "id": row.get("facilityId") or row.get("id"),
            "name": name,
            "location": row.get("location") or row.get("address"),
            "longitude": row.get("longitude"),
            "latitude": row.get("latitude"),
            "floor": row.get("floor"),
            "building": row.get("building"),
        }

    def _secondhand_result(self, row: Dict[str, Any]) -> Dict[str, Any]:
        return {
            "type": "secondhand_item",
            "id": row.get("id"),
            "name": row.get("title") or row.get("itemName"),
            "category": row.get("categoryName"),
            "price": row.get("price"),
            "condition": row.get("condition") or row.get("conditionText"),
            "status": row.get("status"),
            "sellerName": row.get("sellerName") or row.get("publisherName"),
            "location": row.get("location"),
            "description": row.get("description"),
            "viewCount": row.get("viewCount"),
        }

    def _dedupe_results(self, rows: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        seen: set[str] = set()
        deduped: List[Dict[str, Any]] = []
        for row in rows:
            key = f"{row.get('type')}:{row.get('id') or row.get('name')}"
            if key in seen:
                continue
            seen.add(key)
            deduped.append(row)
        return deduped

    def _contains_keyword(self, keyword: str, *fields: Any) -> bool:
        for field in fields:
            if field is None:
                continue
            text = str(field).lower()
            if keyword in text:
                return True
        return False

    def _coupon_result(self, row: Dict[str, Any]) -> Dict[str, Any]:
        return {
            "type": "coupon",
            "id": row.get("id"),
            "name": row.get("couponName"),
            "category": row.get("category"),
            "tagType": row.get("tagType"),
            "pickupLocation": row.get("pickupLocation"),
            "description": row.get("description"),
            "merchantName": row.get("merchantName"),
            "stallName": row.get("stallName"),
            "facilityName": row.get("facilityName"),
            "startDate": row.get("startDate"),
            "endDate": row.get("endDate"),
        }


java_backend_retriever = JavaBackendRetriever()
