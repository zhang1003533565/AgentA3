import hmac
import os
from typing import Optional

from fastapi import Header, HTTPException


def require_internal_token(
    x_ai_internal_token: Optional[str] = Header(
        default=None,
        alias="X-AI-Internal-Token",
    ),
) -> None:
    """Authenticate trusted control-plane calls without exposing the secret."""
    expected = os.environ.get("AI_INTERNAL_TOKEN", "")
    if (
        not expected
        or not x_ai_internal_token
        or not hmac.compare_digest(expected, x_ai_internal_token)
    ):
        raise HTTPException(status_code=401, detail="内部调用认证失败")


__all__ = ["require_internal_token"]
