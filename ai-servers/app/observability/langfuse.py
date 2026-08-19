"""Langfuse tracing configured by the Java administration service.

Credentials arrive only on authenticated internal requests from the Java backend.
They are not read from environment variables and are never returned by this service.
"""

from contextlib import contextmanager
from contextvars import ContextVar
from dataclasses import dataclass
from typing import Any, Iterator, Mapping, Optional


@dataclass(frozen=True)
class LangfuseSettings:
    enabled: bool = False
    base_url: str = ""
    public_key: str = ""
    secret_key: str = ""

    @property
    def configured(self) -> bool:
        return self.enabled and bool(self.base_url and self.public_key and self.secret_key)


_settings: ContextVar[LangfuseSettings] = ContextVar("langfuse_settings", default=LangfuseSettings())
_clients: dict[tuple[str, str, str], Any] = {}


def settings_from_headers(enabled: Optional[str], base_url: Optional[str], public_key: Optional[str], secret_key: Optional[str]) -> LangfuseSettings:
    return LangfuseSettings(
        enabled=str(enabled or "").strip().lower() == "true",
        base_url=str(base_url or "").strip().rstrip("/"),
        public_key=str(public_key or "").strip(),
        secret_key=str(secret_key or "").strip(),
    )


def provider_cache_key() -> tuple[bool, str, str, str]:
    """Ensure providers built before tracing is enabled are not reused without callbacks."""
    settings = _settings.get()
    return settings.enabled, settings.base_url, settings.public_key, settings.secret_key


@contextmanager
def use_settings(settings: LangfuseSettings) -> Iterator[None]:
    token = _settings.set(settings)
    try:
        yield
    finally:
        _settings.reset(token)


def _client() -> Optional[Any]:
    settings = _settings.get()
    if not settings.configured:
        return None
    key = (settings.base_url, settings.public_key, settings.secret_key)
    if key not in _clients:
        from langfuse import Langfuse

        _clients[key] = Langfuse(
            base_url=settings.base_url,
            public_key=settings.public_key,
            secret_key=settings.secret_key,
        )
    return _clients[key]


def langchain_callbacks() -> list[Any]:
    settings = _settings.get()
    if not settings.configured:
        return []
    try:
        _client()
        from langfuse.langchain import CallbackHandler
    except ImportError as exc:
        raise RuntimeError("Langfuse 的 Python 依赖未安装，请安装 ai-servers 依赖后重试。") from exc
    return [CallbackHandler(public_key=settings.public_key)]


@contextmanager
def observe_request(
    name: str,
    *,
    session_id: Optional[str] = None,
    user_id: Optional[int] = None,
    metadata: Optional[Mapping[str, Any]] = None,
) -> Iterator[None]:
    client = _client()
    if client is None:
        yield
        return

    from langfuse import propagate_attributes

    safe_metadata = {key: value for key, value in (metadata or {}).items() if value is not None}
    with client.start_as_current_observation(as_type="span", name=name) as observation:
        if safe_metadata:
            observation.update(metadata=safe_metadata)
        with propagate_attributes(
            session_id=session_id or None,
            user_id=str(user_id) if user_id is not None else None,
        ):
            yield


def flush() -> None:
    for client in list(_clients.values()):
        try:
            client.flush()
        except Exception:
            continue
