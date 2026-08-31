from contextvars import ContextVar, Token
from typing import Optional

request_user_id: ContextVar[Optional[str]] = ContextVar("request_user_id", default=None)


def set_request_user_id(user_id: Optional[str]) -> Token:
    normalized = str(user_id or "").strip()
    return request_user_id.set(normalized or None)


def get_request_user_id() -> Optional[str]:
    return request_user_id.get()


def reset_request_user_id(token: Token) -> None:
    request_user_id.reset(token)
