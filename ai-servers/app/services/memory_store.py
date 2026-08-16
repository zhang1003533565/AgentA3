import json
import os
import re
from typing import Any, Dict, List, Optional

try:
    import redis
except Exception:  # redis is optional because MemoryStore has an in-memory fallback.
    redis = None

from app.utils.logger import get_logger, mask_id

logger = get_logger("services.memory_store")


class MemoryStore:
    def __init__(self) -> None:
        self._fallback: Dict[str, List[Dict[str, str]]] = {}
        self._context_fallback: Dict[str, Dict[str, Any]] = {}
        self._redis_client = None
        self.ttl_seconds = 120 * 60
        self.max_messages = 20
        self.context_recent_turns = 8
        self.context_summary_chars = 2400
        try:
            if redis is None:
                raise RuntimeError("redis package is not installed")
            redis_url = os.getenv("REDIS_URL", "").strip() or "redis://localhost:6379/0"
            self._redis_client = redis.Redis.from_url(redis_url, decode_responses=True)
            self._redis_client.ping()
            logger.debug("redis connected")
        except Exception as exc:
            logger.warning("redis unavailable, fallback to in-memory store: %s", exc)
            self._redis_client = None

    def is_redis_ready(self) -> bool:
        if self._redis_client is None:
            return False
        try:
            return bool(self._redis_client.ping())
        except Exception as exc:
            logger.warning("redis readiness failed: %s", exc)
            return False

    def _key(self, session_token: str) -> str:
        return f"llm:memory:{session_token}"

    def _context_key(self, session_token: str) -> str:
        return f"llm:context:{session_token}"

    def get_history(self, session_token: str) -> List[Dict[str, str]]:
        if self._redis_client is None:
            logger.debug("memory get fallback session_token=%s", mask_id(session_token))
            return list(self._fallback.get(session_token, []))
        raw = self._redis_client.get(self._key(session_token))
        self._redis_client.expire(self._key(session_token), self.ttl_seconds)
        if not raw:
            return []
        try:
            data = json.loads(raw)
            if isinstance(data, list):
                return data
        except Exception:
            pass
        return []

    def append(self, session_token: str, user_input: str, assistant_answer: str) -> None:
        history = self.get_history(session_token)
        history.append({"role": "user", "content": user_input})
        history.append({"role": "ai", "content": assistant_answer})
        while len(history) > self.max_messages:
            history.pop(0)

        if self._redis_client is None:
            logger.debug("memory append fallback session_token=%s size=%s", mask_id(session_token), len(history))
            self._fallback[session_token] = history
            return

        self._redis_client.setex(
            self._key(session_token),
            self.ttl_seconds,
            json.dumps(history, ensure_ascii=False),
        )
        logger.debug("memory append redis session_token=%s size=%s", mask_id(session_token), len(history))

    def get_context(self, session_token: str) -> Dict[str, Any]:
        if not session_token:
            return self._empty_context()
        if self._redis_client is None:
            logger.debug("context get fallback session_token=%s", mask_id(session_token))
            return self._normalize_context(self._context_fallback.get(session_token))
        raw = self._redis_client.get(self._context_key(session_token))
        self._redis_client.expire(self._context_key(session_token), self.ttl_seconds)
        if not raw:
            return self._empty_context()
        try:
            return self._normalize_context(json.loads(raw))
        except Exception:
            return self._empty_context()

    def append_context_turn(
        self,
        session_token: str,
        user_input: str,
        assistant_answer: str,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> None:
        if not session_token:
            return
        context = self.get_context(session_token)
        turn = {
            "user": self._clean_text(user_input, 1000),
            "assistant": self._clean_text(assistant_answer, 1600),
            "metadata": self._small_metadata(metadata or {}),
            "subjects": self._extract_subjects(user_input, assistant_answer, metadata or {}),
        }
        if not turn["user"] and not turn["assistant"]:
            return
        context["turns"].append(turn)
        context = self._compact_context(context)
        if self._redis_client is None:
            logger.debug("context append fallback session_token=%s turns=%s", mask_id(session_token), len(context["turns"]))
            self._context_fallback[session_token] = context
            return
        self._redis_client.setex(
            self._context_key(session_token),
            self.ttl_seconds,
            json.dumps(context, ensure_ascii=False),
        )
        logger.debug("context append redis session_token=%s turns=%s", mask_id(session_token), len(context["turns"]))

    def _empty_context(self) -> Dict[str, Any]:
        return {
            "summary": "",
            "turns": [],
            "lastSubjects": [],
            "compressedTurnCount": 0,
        }

    def _normalize_context(self, value: Any) -> Dict[str, Any]:
        if not isinstance(value, dict):
            return self._empty_context()
        context = self._empty_context()
        context["summary"] = self._clean_text(value.get("summary"), self.context_summary_chars)
        turns = value.get("turns")
        if isinstance(turns, list):
            context["turns"] = [turn for turn in turns if isinstance(turn, dict)][-self.context_recent_turns:]
        subjects = value.get("lastSubjects")
        if isinstance(subjects, list):
            context["lastSubjects"] = [str(item).strip() for item in subjects if str(item or "").strip()][:8]
        count = value.get("compressedTurnCount")
        context["compressedTurnCount"] = int(count) if isinstance(count, int) and count > 0 else 0
        return context

    def _compact_context(self, context: Dict[str, Any]) -> Dict[str, Any]:
        turns = context.get("turns") if isinstance(context.get("turns"), list) else []
        if len(turns) > self.context_recent_turns:
            older = turns[:-self.context_recent_turns]
            context["turns"] = turns[-self.context_recent_turns:]
            summary_lines = [context.get("summary") or ""]
            summary_lines.extend(self._turn_summary_line(turn) for turn in older)
            context["summary"] = self._clean_text("\n".join(line for line in summary_lines if line), self.context_summary_chars)
            context["compressedTurnCount"] = int(context.get("compressedTurnCount") or 0) + len(older)
        subjects: List[str] = []
        for turn in reversed(context.get("turns") or []):
            for subject in turn.get("subjects") or []:
                text = str(subject or "").strip()
                if text and text not in subjects:
                    subjects.append(text)
                if len(subjects) >= 8:
                    break
            if len(subjects) >= 8:
                break
        context["lastSubjects"] = subjects
        return context

    def _turn_summary_line(self, turn: Dict[str, Any]) -> str:
        user = self._clean_text(turn.get("user"), 220)
        assistant = self._clean_text(turn.get("assistant"), 260)
        subjects = "、".join(str(item) for item in (turn.get("subjects") or [])[:3])
        prefix = f"主题：{subjects}；" if subjects else ""
        return f"{prefix}用户问：{user}；助手答：{assistant}"

    def _small_metadata(self, metadata: Dict[str, Any]) -> Dict[str, Any]:
        keep = (
            "intent",
            "toolName",
            "targetAgent",
            "executedAgent",
            "answerType",
            "strategyLabel",
            "routeReason",
            "knowledgeSourceMode",
            "knowledgeTopic",
        )
        return {key: self._clean_text(metadata.get(key), 260) for key in keep if metadata.get(key) is not None}

    def _extract_subjects(self, user_input: str, assistant_answer: str, metadata: Dict[str, Any]) -> List[str]:
        text = "\n".join([str(user_input or ""), str(assistant_answer or "")])
        candidates: List[str] = []
        patterns = [
            r"课程名[:：]\s*([^\n，。,；;]+)",
            r"你的[“\"]([^”\"]+)[”\"]课程",
            r"([A-Za-z][A-Za-z0-9_+\- ]{1,40})\s*(?:程序设计|系统|课程|这门课)",
            r"([\u4e00-\u9fa5A-Za-z0-9_+\- ]{2,40}?)(?:的老师是|本学期查到|课程安排如下|上课安排如下)",
        ]
        for pattern in patterns:
            for match in re.findall(pattern, text, flags=re.IGNORECASE):
                if isinstance(match, tuple):
                    match = next((item for item in match if item), "")
                cleaned = self._clean_subject(match)
                if cleaned and cleaned not in candidates:
                    candidates.append(cleaned)
                if len(candidates) >= 8:
                    return candidates
        return candidates

    def _clean_subject(self, value: Any) -> str:
        text = str(value or "").strip()
        text = re.sub(r"^[：:，,。；;\s]+|[：:，,。；;\s]+$", "", text)
        text = re.sub(r"\s+", " ", text)
        blocked = {"请问", "根据查询结果", "查询结果", "暂未", "没有", "老师", "课程"}
        if not text or text in blocked or len(text) > 48:
            return ""
        return text

    def _clean_text(self, value: Any, max_chars: int) -> str:
        text = str(value or "").strip()
        text = re.sub(r"\s+", " ", text)
        return text[:max_chars]


memory_store = MemoryStore()
