from __future__ import annotations

import copy
import json
import logging
import os
import threading
from typing import Any, Dict, Optional

try:
    import redis
except Exception:  # pragma: no cover
    redis = None


logger = logging.getLogger(__name__)


class PptSourceFileStore:
    def __init__(self) -> None:
        self._fallback: Dict[str, Dict[str, Any]] = {}
        self._lock = threading.RLock()
        self._redis = None
        self._ttl_seconds = int(os.getenv("PPT_SOURCE_FILE_TTL_SECONDS") or 24 * 60 * 60)
        try:
            if redis is None:
                raise RuntimeError("redis package is not installed")
            client = redis.Redis.from_url(
                str(os.getenv("REDIS_URL") or "redis://localhost:6379/0").strip(),
                decode_responses=True,
                socket_connect_timeout=1,
                socket_timeout=1,
            )
            client.ping()
            self._redis = client
        except Exception as exc:
            logger.warning("PPT source file Redis unavailable; using process memory: %s", exc)

    def put(self, file_id: str, metadata: Dict[str, Any]) -> None:
        snapshot = copy.deepcopy(metadata)
        with self._lock:
            self._fallback[file_id] = snapshot
            if self._redis is not None:
                self._redis.setex(
                    self._key(file_id),
                    self._ttl_seconds,
                    json.dumps(snapshot, ensure_ascii=False),
                )

    def get_owned(self, user_id: str, file_id: str) -> Optional[Dict[str, Any]]:
        with self._lock:
            value = None
            if self._redis is not None:
                raw = self._redis.get(self._key(file_id))
                if raw:
                    try:
                        decoded = json.loads(raw)
                        value = decoded if isinstance(decoded, dict) else None
                    except ValueError:
                        value = None
            if value is None:
                value = self._fallback.get(file_id)
            if not isinstance(value, dict) or str(value.get("userId")) != str(user_id):
                return None
            return copy.deepcopy(value)

    @staticmethod
    def _key(file_id: str) -> str:
        return f"ppt:source-file:{file_id}"

