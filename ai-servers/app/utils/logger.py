import logging
import os
from typing import Optional


def init_logging() -> None:
    level_name = os.getenv("LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
        force=True,
    )
    logging.getLogger("httpx").setLevel(getattr(logging, os.getenv("LOG_HTTPX_LEVEL", "WARNING").upper(), logging.WARNING))
    logging.getLogger("httpcore").setLevel(getattr(logging, os.getenv("LOG_HTTPCORE_LEVEL", "WARNING").upper(), logging.WARNING))
    logging.getLogger("uvicorn.access").setLevel(
        getattr(logging, os.getenv("LOG_UVICORN_ACCESS_LEVEL", "WARNING").upper(), logging.WARNING)
    )


def get_logger(name: str) -> logging.Logger:
    return logging.getLogger(name)


def mask_id(value: Optional[str], keep_prefix: int = 6, keep_suffix: int = 4) -> str:
    if not value:
        return "-"
    if len(value) <= keep_prefix + keep_suffix + 3:
        return value[:2] + "***"
    return f"{value[:keep_prefix]}***{value[-keep_suffix:]}"
