import json
import os
from typing import Dict, List

import redis

from app.utils.logger import get_logger, mask_id

logger = get_logger("services.memory_store")


class MemoryStore:
    def __init__(self) -> None:
        self._fallback: Dict[str, List[Dict[str, str]]] = {}
        self._redis_client = None
        self.ttl_seconds = int(os.getenv("LLM_MEMORY_TTL_MINUTES", "120")) * 60
        self.max_messages = int(os.getenv("LLM_MEMORY_MAX_MESSAGES", "20"))
        try:
            redis_url = os.getenv("REDIS_URL", "redis://localhost:6379/0")
            self._redis_client = redis.Redis.from_url(redis_url, decode_responses=True)
            self._redis_client.ping()
            logger.debug("redis connected")
        except Exception as exc:
            logger.warning("redis unavailable, fallback to in-memory store: %s", exc)
            self._redis_client = None

    def _key(self, session_token: str) -> str:
        return f"llm:memory:{session_token}"

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


memory_store = MemoryStore()
