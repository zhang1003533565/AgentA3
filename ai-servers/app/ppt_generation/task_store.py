from __future__ import annotations

import copy
import json
import logging
import os
import threading
from typing import Any, Dict, Optional

try:
    import redis
except Exception:  # pragma: no cover - redis is an optional runtime dependency
    redis = None


logger = logging.getLogger(__name__)


class PptTaskStore:
    def __init__(self) -> None:
        self._fallback: Dict[str, Dict[str, Any]] = {}
        self._lock = threading.RLock()
        self._redis = None
        self._ttl_seconds = _positive_int("PPT_TASK_TTL_SECONDS", 7 * 24 * 60 * 60)
        try:
            if redis is None:
                raise RuntimeError("redis package is not installed")
            redis_url = str(os.getenv("REDIS_URL") or "redis://localhost:6379/0").strip()
            client = redis.Redis.from_url(
                redis_url,
                decode_responses=True,
                socket_connect_timeout=1,
                socket_timeout=1,
            )
            client.ping()
            self._redis = client
        except Exception as exc:
            logger.warning("PPT task Redis unavailable; using process memory: %s", exc)

    @property
    def persistent(self) -> bool:
        return self._redis is not None

    def put(self, task: Dict[str, Any]) -> None:
        task_id = str(task.get("taskId") or "").strip()
        if not task_id:
            raise ValueError("PPT taskId is required")
        snapshot = copy.deepcopy(task)
        with self._lock:
            self._fallback[task_id] = snapshot
            if self._redis is not None:
                self._redis.setex(
                    self._key(task_id),
                    self._ttl_seconds,
                    json.dumps(snapshot, ensure_ascii=False),
                )

    def get(self, task_id: str) -> Optional[Dict[str, Any]]:
        with self._lock:
            if self._redis is not None:
                raw = self._redis.get(self._key(task_id))
                if raw:
                    try:
                        value = json.loads(raw)
                        if isinstance(value, dict):
                            self._redis.expire(self._key(task_id), self._ttl_seconds)
                            self._fallback[task_id] = value
                            return copy.deepcopy(value)
                    except (TypeError, ValueError):
                        logger.warning("Ignoring invalid persisted PPT task: %s", task_id)
            value = self._fallback.get(task_id)
            return copy.deepcopy(value) if value is not None else None

    def list_tasks(self) -> list[Dict[str, Any]]:
        """Return persisted task snapshots for startup recovery."""
        with self._lock:
            values: Dict[str, Dict[str, Any]] = {
                key: copy.deepcopy(value) for key, value in self._fallback.items()
            }
            if self._redis is not None:
                for key in self._redis.scan_iter(match="ppt:task:*"):
                    raw = self._redis.get(key)
                    if not raw:
                        continue
                    try:
                        value = json.loads(raw)
                    except (TypeError, ValueError):
                        continue
                    if isinstance(value, dict) and value.get("taskId"):
                        values[str(value["taskId"])] = copy.deepcopy(value)
            return list(values.values())

    @staticmethod
    def _key(task_id: str) -> str:
        return f"ppt:task:{task_id}"


def _positive_int(name: str, default: int) -> int:
    raw = str(os.getenv(name) or "").strip()
    if not raw:
        return default
    try:
        value = int(raw)
    except ValueError as exc:
        raise RuntimeError(f"{name} must be a positive integer") from exc
    if value <= 0:
        raise RuntimeError(f"{name} must be a positive integer")
    return value
