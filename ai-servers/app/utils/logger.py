import logging
from typing import Optional


def init_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
        force=True,
    )
    logging.getLogger("httpx").setLevel(logging.WARNING)
    logging.getLogger("httpcore").setLevel(logging.WARNING)
    logging.getLogger("uvicorn.access").setLevel(logging.WARNING)


def get_logger(name: str) -> logging.Logger:
    return logging.getLogger(name)


def mask_id(value: Optional[str], keep_prefix: int = 6, keep_suffix: int = 4) -> str:
    if not value:
        return "-"
    if len(value) <= keep_prefix + keep_suffix + 3:
        return value[:2] + "***"
    return f"{value[:keep_prefix]}***{value[-keep_suffix:]}"
